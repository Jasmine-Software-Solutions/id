package com.jasminesoftwaresolutions.idinterfaces.services.auth

import com.jasminesoftwaresolutions.id.domain.services.authorization.IAuthorizationService
import com.jasminesoftwaresolutions.witchwood.evaluation.snapshot.resources.CompiledResourcePath
import com.jasminesoftwaresolutions.witchwood.evaluation.snapshot.resources.ResourcePathMatcher
import java.util.*
import java.util.concurrent.TimeUnit

class ScopedListHelper(
    private val evaluationService: IAuthorizationService,
) {
    fun <T> filterVisible(
        principalId: UUID,
        action: String,
        entities: List<T>,
        resourceFn: (T) -> CompiledResourcePath,
    ): List<T> {
        val patterns = try {
            evaluationService.queryResources(principalId, action).get(3, TimeUnit.SECONDS)
        } catch (_: Exception) {
            emptySet()
        }

        if (patterns.isEmpty()) return emptyList()

        return entities.filter { entity ->
            val resource = resourceFn(entity)
            patterns.any { pattern -> ResourcePathMatcher.matches(pattern, resource) }
        }
    }
}
