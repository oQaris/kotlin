/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */
package org.jetbrains.kotlin.backend.konan

import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKStringFromUtf8
import llvm.*
import org.jetbrains.kotlin.backend.common.phaser.ActionState
import org.jetbrains.kotlin.backend.common.phaser.BeforeOrAfter
import org.jetbrains.kotlin.backend.common.serialization.KlibIrVersion
import org.jetbrains.kotlin.backend.common.serialization.metadata.KlibMetadataVersion
import org.jetbrains.kotlin.backend.konan.llvm.*
import org.jetbrains.kotlin.backend.konan.llvm.objc.linkObjC
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.konan.CURRENT
import org.jetbrains.kotlin.konan.CompilerVersion
import org.jetbrains.kotlin.konan.file.isBitcode
import org.jetbrains.kotlin.konan.library.impl.buildLibrary
import org.jetbrains.kotlin.konan.target.CompilerOutputKind
import org.jetbrains.kotlin.library.*

/**
 * Supposed to be true for a single LLVM module within final binary.
 */
val CompilerOutputKind.isFinalBinary: Boolean get() = when (this) {
    CompilerOutputKind.PROGRAM, CompilerOutputKind.DYNAMIC,
    CompilerOutputKind.STATIC, CompilerOutputKind.FRAMEWORK -> true
    CompilerOutputKind.DYNAMIC_CACHE, CompilerOutputKind.STATIC_CACHE, CompilerOutputKind.PRELIMINARY_CACHE,
    CompilerOutputKind.LIBRARY, CompilerOutputKind.BITCODE -> false
}

val CompilerOutputKind.involvesBitcodeGeneration: Boolean
    get() = this != CompilerOutputKind.LIBRARY

internal val Context.producedLlvmModuleContainsStdlib: Boolean
    get() = this.llvmModuleSpecification.containsModule(this.stdlibModule)

internal val Context.shouldDefineFunctionClasses: Boolean
    get() = producedLlvmModuleContainsStdlib &&
            (config.libraryToCache?.strategy as? CacheDeserializationStrategy.SingleFile)
                    ?.filePath?.endsWith("runtime/src/main/kotlin/kotlin/reflect/KFunction.kt") != false

internal val Context.shouldDefineRuntimeConstGlobals: Boolean
    get() = producedLlvmModuleContainsStdlib &&
            (config.libraryToCache?.strategy as? CacheDeserializationStrategy.SingleFile)
                    ?.filePath?.endsWith("runtime/src/main/kotlin/kotlin/native/Runtime.kt") != false

internal val Context.shouldDefineCachedBoxes: Boolean
    get() = producedLlvmModuleContainsStdlib &&
            (config.libraryToCache?.strategy as? CacheDeserializationStrategy.SingleFile)
                    ?.filePath?.endsWith("runtime/src/main/kotlin/kotlin/native/internal/Boxing.kt") != false

internal val Context.shouldLinkRuntimeNativeLibraries: Boolean
    get() = producedLlvmModuleContainsStdlib &&
            (config.libraryToCache?.strategy as? CacheDeserializationStrategy.SingleFile)
                    ?.filePath?.endsWith("runtime/src/main/kotlin/kotlin/native/Runtime.kt") != false

internal val Context.shouldLinkLibrariesBitcode: Boolean
    get() = !producedLlvmModuleContainsStdlib ||
            (config.libraryToCache?.strategy as? CacheDeserializationStrategy.SingleFile)
                    ?.filePath?.endsWith("runtime/src/main/kotlin/kotlin/native/Runtime.kt") != false

val CompilerOutputKind.involvesLinkStage: Boolean
    get() = when (this) {
        CompilerOutputKind.PROGRAM, CompilerOutputKind.DYNAMIC,
        CompilerOutputKind.DYNAMIC_CACHE, CompilerOutputKind.STATIC_CACHE,
        CompilerOutputKind.STATIC, CompilerOutputKind.FRAMEWORK -> true
        CompilerOutputKind.LIBRARY, CompilerOutputKind.BITCODE, CompilerOutputKind.PRELIMINARY_CACHE -> false
    }

val CompilerOutputKind.isCache: Boolean
    get() = this == CompilerOutputKind.STATIC_CACHE || this == CompilerOutputKind.DYNAMIC_CACHE
            || this == CompilerOutputKind.PRELIMINARY_CACHE

internal fun llvmIrDumpCallback(state: ActionState, module: IrModuleFragment, context: Context) {
    module.let{}
    if (state.beforeOrAfter == BeforeOrAfter.AFTER && state.phase.name in context.configuration.getList(KonanConfigKeys.SAVE_LLVM_IR)) {
        val moduleName: String = memScoped {
            val sizeVar = alloc<size_tVar>()
            LLVMGetModuleIdentifier(context.llvmModule, sizeVar.ptr)!!.toKStringFromUtf8()
        }
        val output = context.config.tempFiles.create("$moduleName.${state.phase.name}", ".ll")
        if (LLVMPrintModuleToFile(context.llvmModule, output.absolutePath, null) != 0) {
            error("Can't dump LLVM IR to ${output.absolutePath}")
        }
    }
}

internal fun produceCStubs(context: Context) {
    val llvmModule = context.llvmModule!!
    context.cStubsManager.compile(
            context.config.clang,
            context.messageCollector,
            context.inVerbosePhase
    ).forEach {
        parseAndLinkBitcodeFile(context, llvmModule, it.absolutePath)
    }
}

private fun linkAllDependencies(context: Context, generatedBitcodeFiles: List<String>) {
    val config = context.config

    // TODO: Possibly slow, maybe to a separate phase?
    val runtimeModules = RuntimeLinkageStrategy.pick(context).run()

    val launcherNativeLibraries = config.launcherNativeLibraries
            .takeIf { config.produce == CompilerOutputKind.PROGRAM }.orEmpty()

    linkObjC(context)

    val nativeLibraries = config.nativeLibraries + launcherNativeLibraries

    val bitcodeLibraries = context.llvm.bitcodeToLink.map { it.bitcodePaths }.flatten().filter { it.isBitcode }
            .takeIf { context.shouldLinkLibrariesBitcode }.orEmpty()

    val additionalBitcodeFilesToLink = context.llvm.additionalProducedBitcodeFiles

    val exceptionsSupportNativeLibrary = config.exceptionsSupportNativeLibrary
    val bitcodeFiles = (nativeLibraries + generatedBitcodeFiles + additionalBitcodeFilesToLink + bitcodeLibraries).toMutableSet()
    if (config.produce == CompilerOutputKind.DYNAMIC_CACHE)
        bitcodeFiles += exceptionsSupportNativeLibrary

    val llvmModule = context.llvmModule!!
    runtimeModules.forEach {
        val failed = llvmLinkModules2(context, llvmModule, it)
        if (failed != 0) {
            error("Failed to link ${it.getName()}")
        }
    }
    bitcodeFiles.forEach {
        parseAndLinkBitcodeFile(context, llvmModule, it)
    }
}

private fun insertAliasToEntryPoint(context: Context) {
    val nomain = context.config.configuration.get(KonanConfigKeys.NOMAIN) ?: false
    if (context.config.produce != CompilerOutputKind.PROGRAM || nomain)
        return
    val module = context.llvmModule
    val entryPointName = context.config.entryPointName
    val entryPoint = LLVMGetNamedFunction(module, entryPointName)
            ?: error("Module doesn't contain `$entryPointName`")
    LLVMAddAlias(module, LLVMTypeOf(entryPoint)!!, entryPoint, "main")
}

internal fun linkBitcodeDependencies(context: Context) {
    val config = context.config.configuration
    val tempFiles = context.config.tempFiles
    val produce = config.get(KonanConfigKeys.PRODUCE)

    val generatedBitcodeFiles =
            if (produce == CompilerOutputKind.DYNAMIC || produce == CompilerOutputKind.STATIC) {
                produceCAdapterBitcode(
                        context.config.clang,
                        tempFiles.cAdapterCppName,
                        tempFiles.cAdapterBitcodeName)
                listOf(tempFiles.cAdapterBitcodeName)
            } else emptyList()
    if (produce == CompilerOutputKind.FRAMEWORK && context.config.produceStaticFramework) {
        embedAppleLinkerOptionsToBitcode(context.llvm, context.config)
    }
    linkAllDependencies(context, generatedBitcodeFiles)

}

internal fun produceOutput(context: Context) {

    val config = context.config.configuration
    val tempFiles = context.config.tempFiles
    val produce = config.get(KonanConfigKeys.PRODUCE)

    when (produce) {
        CompilerOutputKind.STATIC,
        CompilerOutputKind.DYNAMIC,
        CompilerOutputKind.FRAMEWORK,
        CompilerOutputKind.DYNAMIC_CACHE,
        CompilerOutputKind.STATIC_CACHE,
        CompilerOutputKind.PROGRAM -> {
            val output = tempFiles.nativeBinaryFileName
            context.bitcodeFileName = output
            // Insert `_main` after pipeline so we won't worry about optimizations
            // corrupting entry point.
            insertAliasToEntryPoint(context)
            LLVMWriteBitcodeToFile(context.llvmModule!!, output)
        }
        CompilerOutputKind.LIBRARY -> {
            val nopack = config.getBoolean(KonanConfigKeys.NOPACK)
            val output = context.config.outputFiles.klibOutputFileName(!nopack)
            val libraryName = context.config.moduleId
            val shortLibraryName = context.config.shortModuleName
            val neededLibraries = context.librariesWithDependencies
            val abiVersion = KotlinAbiVersion.CURRENT
            val compilerVersion = CompilerVersion.CURRENT.toString()
            val libraryVersion = config.get(KonanConfigKeys.LIBRARY_VERSION)
            val metadataVersion = KlibMetadataVersion.INSTANCE.toString()
            val irVersion = KlibIrVersion.INSTANCE.toString()
            val versions = KotlinLibraryVersioning(
                abiVersion = abiVersion,
                libraryVersion = libraryVersion,
                compilerVersion = compilerVersion,
                metadataVersion = metadataVersion,
                irVersion = irVersion
            )
            val target = context.config.target
            val manifestProperties = context.config.manifestProperties

            if (!nopack) {
                val suffix = context.config.outputFiles.produce.suffix(target)
                if (!output.endsWith(suffix)) {
                    error("please specify correct output: packed: ${!nopack}, $output$suffix")
                }
            }

            val library = buildLibrary(
                    context.config.nativeLibraries,
                    context.config.includeBinaries,
                    neededLibraries,
                    context.serializedMetadata!!,
                    context.serializedIr,
                    versions,
                    target,
                    output,
                    libraryName,
                    nopack,
                    shortLibraryName,
                    manifestProperties,
                    context.dataFlowGraph)

            context.bitcodeFileName = library.mainBitcodeFileName
        }
        CompilerOutputKind.BITCODE -> {
            val output = context.config.outputFile
            context.bitcodeFileName = output
            LLVMWriteBitcodeToFile(context.llvmModule!!, output)
        }
        CompilerOutputKind.PRELIMINARY_CACHE -> {}
        null -> {}
    }
}

internal fun parseAndLinkBitcodeFile(context: Context, llvmModule: LLVMModuleRef, path: String) {
    val parsedModule = parseBitcodeFile(path)
    if (!context.shouldUseDebugInfoFromNativeLibs()) {
        LLVMStripModuleDebugInfo(parsedModule)
    }
    val failed = llvmLinkModules2(context, llvmModule, parsedModule)
    if (failed != 0) {
        throw Error("failed to link $path")
    }
}

private fun embedAppleLinkerOptionsToBitcode(llvm: Llvm, config: KonanConfig) {
    fun findEmbeddableOptions(options: List<String>): List<List<String>> {
        val result = mutableListOf<List<String>>()
        val iterator = options.iterator()
        loop@while (iterator.hasNext()) {
            val option = iterator.next()
            result += when {
                option.startsWith("-l") -> listOf(option)
                option == "-framework" && iterator.hasNext() -> listOf(option, iterator.next())
                else -> break@loop // Ignore the rest.
            }
        }
        return result
    }

    val optionsToEmbed = findEmbeddableOptions(config.platform.configurables.linkerKonanFlags) +
            llvm.allNativeDependencies.flatMap { findEmbeddableOptions(it.linkerOpts) }

    embedLlvmLinkOptions(llvm.llvmModule, optionsToEmbed)
}
