package app.workers.email

import gg.jte.ContentType
import gg.jte.TemplateEngine
import gg.jte.output.StringOutput

/**
 * Renders JTE email templates using precompiled template classes.
 */
class TemplateRenderer {
    private val templateEngine: TemplateEngine by lazy {
        TemplateEngine.createPrecompiled(ContentType.Html)
    }

    fun render(template: String, params: Map<String, Any?>): String {
        val output = StringOutput()
        templateEngine.render(template, params, output)
        return output.toString()
    }
}
