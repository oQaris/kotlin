/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.backend.js.utils

import org.jetbrains.kotlin.descriptors.isClass
import org.jetbrains.kotlin.descriptors.isInterface
import org.jetbrains.kotlin.ir.backend.js.JsIrBackendContext
import org.jetbrains.kotlin.ir.backend.js.export.isExported
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrDeclarationWithVisibility
import org.jetbrains.kotlin.ir.expressions.IrReturn
import org.jetbrains.kotlin.ir.symbols.IrReturnableBlockSymbol
import org.jetbrains.kotlin.ir.util.parentClassOrNull

fun IrDeclaration.isExportedMember(context: JsIrBackendContext) =
    (this is IrDeclarationWithVisibility && visibility.isPublicAPI) &&
            parentClassOrNull?.isExported(context) == true

fun IrDeclaration?.isExportedClass(context: JsIrBackendContext) =
    this is IrClass && kind.isClass && isExported(context)

fun IrDeclaration?.isExportedInterface(context: JsIrBackendContext) =
    this is IrClass && kind.isInterface && isExported(context)

fun IrReturn.isTheLastReturnStatementIn(target: IrReturnableBlockSymbol): Boolean {
    return target.owner.statements.lastOrNull() === this
}
