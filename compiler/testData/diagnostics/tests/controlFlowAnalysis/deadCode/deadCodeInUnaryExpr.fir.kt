fun testPrefix() {
    operator fun Any.not() {}
    <!UNREACHABLE_CODE!>!<!>todo()
}

fun testPostfixWithCall(n: Nothing) {
    operator fun Nothing.inc(): Nothing = this
    <!VAL_REASSIGNMENT!>n<!><!UNREACHABLE_CODE!>++<!>
}

fun testPostfixSpecial() {
    todo()<!UNREACHABLE_CODE!>!!<!>
}

fun todo(): Nothing = throw Exception()
