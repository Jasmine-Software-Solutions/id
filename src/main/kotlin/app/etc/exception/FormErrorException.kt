package app.etc.exception

import io.javalin.http.BadRequestResponse

class FormErrorException(val summary: String, val detail: String, val formErrorElement: String = "#form-error"): BadRequestResponse(
    """
    {
        "summary": "$summary",
        "detail": "$detail"
    }
    """.trimIndent()
)