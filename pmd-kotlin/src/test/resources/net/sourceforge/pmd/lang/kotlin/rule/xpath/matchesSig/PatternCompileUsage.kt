import java.util.regex.Pattern

class EmailValidator {
    fun validate(input: String): Boolean {
        val pattern = Pattern.compile("[a-z]+")
        return pattern.matcher(input).matches()
    }

    // should NOT match — no Pattern.compile call
    fun noCompile(input: String): Boolean = input.isNotEmpty()
}
