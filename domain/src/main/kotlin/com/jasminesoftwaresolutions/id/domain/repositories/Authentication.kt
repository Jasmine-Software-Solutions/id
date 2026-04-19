package com.jasminesoftwaresolutions.id.domain.repositories

import com.jasminesoftwaresolutions.id.domain.models.authentication.IAuthenticationFlow

interface IAuthenticationFlowRepository<T : IAuthenticationFlow> :
    IIdentifiedRepository<T>