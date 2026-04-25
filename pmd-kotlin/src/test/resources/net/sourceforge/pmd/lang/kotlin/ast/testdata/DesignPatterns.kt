/*
 * BSD-style license; for more info see http://pmd.sourceforge.net/license.html
 */

package net.sourceforge.pmd.lang.kotlin.ast.testdata

// SimplifyBooleanExpressions
val a = (someFlag == true)
val b = (someFlag != false)

// SimplifyBooleanReturns
fun checkEquality(x: Int, y: Int): Boolean {
    if (x == y) {
        return true
    } else {
        return false
    }
}

// CollapsibleIfStatements - with braces
fun withBraces(x: Boolean, y: Boolean) {
    if (x) {
        if (y) {
            println("both")
        }
    }
}

// CollapsibleIfStatements - without braces
fun withoutBraces(x: Boolean, y: Boolean) {
    if (x)
        if (y) println("ok")
}
