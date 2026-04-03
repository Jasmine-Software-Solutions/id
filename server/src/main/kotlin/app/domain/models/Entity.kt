package app.domain.models

import java.time.Instant
import java.util.*

interface IIdentified {
    val id: UUID
}

interface ICreated {
    val createdAt: Instant
}

interface IExpires {
    var expiresAt: Instant
}