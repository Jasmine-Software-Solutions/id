package app.etc

import io.javalin.http.Context

val Context.isHtmxRequest
    get() = this.header("HX-Request") == "true"

fun Context.hxRedirect(location: String) {
    if (this.isHtmxRequest) {
        this.header("HX-Redirect", location)
        return
    }

    this.redirect(location)
}

fun Context.hxRetarget(value: String) {
    this.header("HX-Retarget", value)
}

fun Context.hxReswap(value: String) {
    this.header("HX-Reswap", value)
}

@Suppress("unused")
fun Context.hxPushUrl(value: String) {
    this.header("HX-Push-Url", value)
}