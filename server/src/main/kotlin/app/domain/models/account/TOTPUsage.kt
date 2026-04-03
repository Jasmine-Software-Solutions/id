package app.domain.models.account

import app.domain.models.ICreated

interface ITOTPUsage : ICreated {
    var period: Long
}