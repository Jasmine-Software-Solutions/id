package app.application

/**
 * The client's address as the configured [Env.CLIENT_IP_HEADER] reports it.
 *
 * A forwarded-for header may hold a list — `client, proxy1, proxy2` — and the
 * left-most entry is the original client. A blank value falls back to [peer]:
 * a request that came around the proxy carries no usable header, and naming
 * the peer beats naming nobody.
 */
internal fun clientIp(headerValue: String?, peer: String): String =
    headerValue?.substringBefore(',')?.trim()?.takeIf { it.isNotEmpty() } ?: peer
