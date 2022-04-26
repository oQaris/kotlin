// !DIAGNOSTICS: -UNUSED_VARIABLE -UNUSED_PARAMETER
// JSR305_GLOBAL_REPORT: warn

// FILE: A.java
import javax.annotation.*;

@ParametersAreNonnullByDefault
public class A {
    @Nullable public String field = null;

    public String foo(String q, @Nonnull String x, @CheckForNull CharSequence y) {
        return "";
    }

    @Nonnull
    public String bar() {
        return "";
    }
}

// FILE: main.kt
fun main(a: A) {
    // foo is platform
    a.foo("", "", null)?.length
    a.foo("", "", null).length
    a.foo(null, <!NULL_FOR_NONNULL_TYPE!>null<!>, "").length

    a.bar().length
    a.bar()!!.length

    a.field?.length
    a.field<!UNSAFE_CALL!>.<!>length
}
