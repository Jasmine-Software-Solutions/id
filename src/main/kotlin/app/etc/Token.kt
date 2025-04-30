package app.etc

object Token {
    operator fun invoke(): String {
        return (1..32)
            .map { ('a'..'z') + ('A'..'Z') + ('0'..'9') }
            .flatten()
            .shuffled()
            .take(32)
            .joinToString("")
    }
}