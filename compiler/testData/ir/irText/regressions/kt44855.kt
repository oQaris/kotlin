// SKIP_KT_DUMP

// MODULE: lib
// FILE: test/Parent.java

package test;

public class Parent {
    protected String qqq = "";

    public String getQqq() {
        return qqq;
    }
}

// MODULE: main(lib)
// FILE: kt44855.kt

import test.Parent

open class Child(val x: Parent?) : Parent() {
    inner class QQQ {
        fun z() {
            x as Child
            val q = x.qqq
            x.qqq = q + "OK"
        }
    }
}
