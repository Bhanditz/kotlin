class B {
    companion object <!REDECLARATION!>A<!> {
    }

    val <!REDECLARATION!>A<!> = this
}

class C {
    companion <!CONFLICTING_JVM_DECLARATIONS!>object A<!> {
        <!CONFLICTING_JVM_DECLARATIONS!>val A<!> = this
    }

}
