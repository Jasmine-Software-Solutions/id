package com.jasminesoftwaresolutions.id.domain.registries

import com.jasminesoftwaresolutions.id.domain.models.authorization.IScope
import com.jasminesoftwaresolutions.id.domain.models.authorization.StaticScope

interface IScopeRegistry<TScope : IScope> {
    fun entries(): Set<IScope>

    fun findById(id: String): TScope?
    fun register(scope: TScope)
}

open class ScopeRegistry : IScopeRegistry<IScope> {
    protected val scopes = mutableMapOf<String, IScope>().apply {
        for (scope in StaticScope.all())
            put(scope.id, scope)
    }

    override fun entries(): Set<IScope>
        = scopes.values.toSet()

    override fun findById(id: String) = scopes[id]
    override fun register(scope: IScope) { scopes[scope.id] = scope }
}