// !DIAGNOSTICS: -UNUSED_PARAMETER -UNREACHABLE_CODE -UNUSED_VARIABLE -DEPRECATION
// !WITH_NEW_INFERENCE

inline fun<reified T> foo(block: () -> T): String = block().toString()

inline fun <reified T: Any> javaClass(): Class<T> = T::class.java

fun box() {
    val a = <!OI;REIFIED_TYPE_FORBIDDEN_SUBSTITUTION, OI;UNSUPPORTED!>arrayOf<!>(null!!)
    val b = <!UNSUPPORTED!>Array<!><<!REIFIED_TYPE_FORBIDDEN_SUBSTITUTION!>Nothing?<!>>(5) { null!! }
    val c = <!OI;REIFIED_TYPE_FORBIDDEN_SUBSTITUTION!>foo<!>() { null!! }
    val d = foo<Any> { null!! }
    val e = <!OI;REIFIED_TYPE_FORBIDDEN_SUBSTITUTION!>foo<!> { "1" <!CAST_NEVER_SUCCEEDS!>as<!> Nothing }
    val e1 = <!REIFIED_TYPE_FORBIDDEN_SUBSTITUTION!>foo<!> { "1" <!CAST_NEVER_SUCCEEDS!>as<!> Nothing? }

    val f = javaClass<<!REIFIED_TYPE_FORBIDDEN_SUBSTITUTION!>Nothing<!>>()
}
