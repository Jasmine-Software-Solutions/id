package com.jasminesoftwaresolutions.id.domain.models.account

import com.jasminesoftwaresolutions.id.domain.models.ICreated
import com.jasminesoftwaresolutions.id.domain.models.IExpires
import com.jasminesoftwaresolutions.id.domain.models.IIdentified
import java.time.Instant

interface IMagicLink : IIdentified,
    ICreated, IExpires {
    var account: IAccount

    var decidedAt: Instant?

    var approved: Boolean
    var consumed: Boolean

    var decisionToken: String
    var acceptanceToken: String

    fun verifyDecisionToken(token: String): Boolean
    fun verifyAcceptanceToken(token: String): Boolean
}

interface IHashedMagicLink : IMagicLink