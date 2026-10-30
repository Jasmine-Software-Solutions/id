package app.workers.security.detectors

import com.jasminesoftwaresolutions.id.domain.events.AuthenticationSucceededEvent
import com.jasminesoftwaresolutions.id.domain.events.ImpossibleTravelDetectedSecurityEvent
import com.jasminesoftwaresolutions.id.domain.events.SecurityEventLocation
import com.jasminesoftwaresolutions.id.domain.models.authorization.IdActions
import com.jasminesoftwaresolutions.id.domain.models.authorization.IdResources
import app.workers.infrastructure.IpGeolocationService
import com.jasminesoftwaresolutions.id.domain.events.IEventDispatcher
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Detects impossible travel by comparing geographic locations of consecutive
 * successful logins and flagging physically impossible travel speeds.
 */
class ImpossibleTravelDetector(
    private val geoLocation: IpGeolocationService,
    private val eventDispatcher: IEventDispatcher,
    private val clientId: UUID,
) {
    private val log = LoggerFactory.getLogger(ImpossibleTravelDetector::class.java)

    companion object {
        const val MAX_SPEED_KMH = 1000.0
    }

    private data class LastLogin(
        val sessionId: UUID,
        val ip: String,
        val location: SecurityEventLocation?,
        val timestamp: Instant,
    )

    private val lastSuccessfulLogins = ConcurrentHashMap<UUID, LastLogin>()

    fun handleAuthSucceeded(event: AuthenticationSucceededEvent) {
        val accountId = event.accountId
        val ip = event.ipAddress ?: return
        val now = Instant.now()

        val previous = lastSuccessfulLogins[accountId]
        if (previous != null) {
            geoLocation.lookupAsync(ip) { currentLocation ->
                val prevLoc = previous.location
                val curLoc = currentLocation

                if (prevLoc != null && curLoc != null) {
                    val prevLat = prevLoc.latitude ?: return@lookupAsync
                    val prevLng = prevLoc.longitude ?: return@lookupAsync
                    val curLat = curLoc.latitude ?: return@lookupAsync
                    val curLng = curLoc.longitude ?: return@lookupAsync

                    val distanceKm = haversineDistance(prevLat, prevLng, curLat, curLng)
                    val hoursElapsed = Duration.between(previous.timestamp, now).seconds / 3600.0
                    if (hoursElapsed > 0) {
                        val speedKmh = distanceKm / hoursElapsed
                        if (speedKmh > MAX_SPEED_KMH) {
                            log.warn(
                                "Impossible travel: account=$accountId " +
                                        "from (${prevLoc.city}, ${prevLoc.country}) at ${previous.timestamp} " +
                                        "to (${curLoc.city}, ${curLoc.country}) at $now " +
                                        "distance=${distanceKm}km speed=${speedKmh}km/h"
                            )

                            val securityEventId = UUID.randomUUID()
                            eventDispatcher.dispatch(
                                ImpossibleTravelDetectedSecurityEvent(
                                    principalId = clientId,
                                    action = "${IdActions.SERVICE}:ImpossibleTravelDetected",
                                    resource = IdResources.platform().toString(),
                                    timestamp = Instant.now(),
                                    eventId = securityEventId,
                                    accountId = accountId,
                                    previousIp = previous.ip,
                                    previousLocation = prevLoc,
                                    previousLoginAt = previous.timestamp,
                                    currentIp = ip,
                                    currentLocation = curLoc,
                                    currentLoginAt = now,
                                )
                            )
                        }
                    }
                }

                geoLocation.lookupAsync(previous.ip) { loc ->
                    lastSuccessfulLogins[accountId] = LastLogin(
                        sessionId = event.sessionId,
                        ip = previous.ip,
                        location = loc,
                        timestamp = previous.timestamp,
                    )
                }
            }
        }

        geoLocation.lookupAsync(ip) { location ->
            lastSuccessfulLogins[accountId] = LastLogin(
                sessionId = event.sessionId,
                ip = ip,
                location = location,
                timestamp = now,
            )
        }
    }

    fun removeLoginRecord(accountId: UUID) {
        lastSuccessfulLogins.remove(accountId)
    }

    private fun haversineDistance(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLng / 2) * Math.sin(dLng / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return r * c
    }
}
