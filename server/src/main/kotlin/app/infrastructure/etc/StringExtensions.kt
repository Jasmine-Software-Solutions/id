package app.infrastructure.etc

import java.util.*

fun String.toUUID(): UUID = UUID.fromString(this)
fun String.toUUIDOrNull(): UUID? = runCatching { UUID.fromString(this) }.getOrNull()