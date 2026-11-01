package com.jasminesoftwaresolutions.id.domain.models.account

import com.jasminesoftwaresolutions.id.domain.models.ICreated
import com.jasminesoftwaresolutions.id.domain.models.IExpires
import com.jasminesoftwaresolutions.id.domain.models.IIdentified
import java.time.Instant

/**
 * Represents a pending email address change that must be verified before taking effect.
 */
interface IEmailChange : IIdentified, ICreated, IExpires {
    var account: IAccount

    /** The new email address the user wants to switch to. */
    var newEmail: String

    var decidedAt: Instant?

    /** Whether the verification link has been clicked. */
    var confirmed: Boolean

    var verificationToken: String

    fun verify(token: String): Boolean
}

interface IHashedEmailChange : IEmailChange
