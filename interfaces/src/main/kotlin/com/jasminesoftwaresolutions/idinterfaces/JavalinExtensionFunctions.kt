package com.jasminesoftwaresolutions.idinterfaces

import io.javalin.http.Context

fun Context.renderWithContext(template: String, vararg parameters: Pair<String, Any?>) {
    render(template, mapOf("context" to this, *parameters))
    status(200)
}