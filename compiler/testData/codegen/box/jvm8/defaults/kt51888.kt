// TARGET_BACKEND: JVM
// JVM_TARGET: 1.8
// MODULE: lib
// !JVM_DEFAULT_MODE: all
// FILE: PsiElement.java

public interface PsiElement {
    default String test() {
        return "OK";
    }
}

// FILE: kotlin.kt

abstract class KtLightElementBase : PsiElement

interface KtLightClass : PsiElement

// MODULE: main(lib)
// !JVM_DEFAULT_MODE: disable
// FILE: main.kt
abstract class KtLightClassForDecompiledDeclarationBase() : KtLightElementBase(), PsiElement, KtLightClass

fun box(): String {
    return object : KtLightClassForDecompiledDeclarationBase() {}.test()
}
