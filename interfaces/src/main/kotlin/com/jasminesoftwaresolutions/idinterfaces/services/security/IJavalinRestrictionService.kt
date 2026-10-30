package com.jasminesoftwaresolutions.idinterfaces.services.security

import io.javalin.http.Context

interface IJavalinRestrictionService {
    /** Returns true if the request should be blocked. False = allow. */
    fun restrictRequest(ctx: Context): Boolean
}
