package com.jasminesoftwaresolutions.id.domain.models.account

import com.jasminesoftwaresolutions.id.domain.models.ICreated

interface ITOTPUsage : ICreated {
    var period: Long
}