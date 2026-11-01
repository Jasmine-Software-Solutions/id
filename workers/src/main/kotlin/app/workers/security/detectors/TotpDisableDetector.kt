package app.workers.security.detectors

import com.jasminesoftwaresolutions.id.domain.events.AccountTOTPEnabledEvent
import com.jasminesoftwaresolutions.id.domain.events.AccountTOTPDisabledEvent
import com.jasminesoftwaresolutions.id.domain.events.SecurityEventLocation
import com.jasminesoftwaresolutions.id.domain.events.TOTPDisabledFromUnusualLocationSecurityEvent
import com.jasminesoftwaresolutions.id.domain.models.authorization.IdActions
import com.jasminesoftwaresolutions.id.domain.models.authorization.IdResources
import app.workers.infrastructure.IpGeolocationService
import com.jasminesoftwaresolutions.id.domain.events.IEventDispatcher
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Detects TOTP being disabled from an unusual or far-away location
 * compared to where it was enabled.
 */
class TotpDisableDetector(
    private val geoLocation: IpGeolocationService,
    private val eventDispatcher: IEventDispatcher,
    private val clientId: UUID,
) {
    private val log = LoggerFactory.getLogger(TotpDisableDetector::class.java)

    private data class TotpEnableRecord(
        val ip: String,
        val location: SecurityEventLocation?,
    )

    private val totpEnableInfo = ConcurrentHashMap<UUID, TotpEnableRecord>()

    fun handleTOTPEnabled(event: AccountTOTPEnabledEvent) {
        val eventIp = event.ipAddress
        val ip = eventIp ?: "unknown"
        totpEnableInfo[event.accountId] = TotpEnableRecord(ip = ip, location = null)

        if (eventIp != null) {
            geoLocation.lookupAsync(eventIp) { location ->
                totpEnableInfo[event.accountId] = TotpEnableRecord(ip = eventIp, location = location)
            }
        }
    }

    fun handleTOTPDisabled(event: AccountTOTPDisabledEvent) {
        val disabledIp = event.ipAddress ?: return
        val enableRecord = totpEnableInfo[event.accountId] ?: return
        val enabledIp = enableRecord.ip
        val enabledLocation = enableRecord.location

        if (disabledIp == enabledIp) return

        geoLocation.lookupAsync(disabledIp) { disabledLocation ->
            if (disabledLocation == null) return@lookupAsync

            val enabledLoc = enabledLocation

            val differentCountry = enabledLoc != null &&
                    enabledLoc.country != null &&
                    disabledLocation.country != null &&
                    enabledLoc.country != disabledLocation.country

            val farAway = enabledLoc != null &&
                    enabledLoc.latitude != null && enabledLoc.longitude != null &&
                    disabledLocation.latitude != null && disabledLocation.longitude != null &&
                    haversineDistance(
                        enabledLoc.latitude!!, enabledLoc.longitude!!,
                        disabledLocation.latitude!!, disabledLocation.longitude!!,
                    ) > 500.0

            val enabledLocationUnknown = enabledLoc == null

            if (differentCountry || farAway || enabledLocationUnknown) {
                log.warn(
                    "TOTP disabled from unusual location: account=${event.accountId} " +
                            "enabledIp=$enabledIp disabledIp=$disabledIp " +
                            "differentCountry=$differentCountry farAway=$farAway unknownLocation=$enabledLocationUnknown"
                )

                val securityEventId = UUID.randomUUID()
                eventDispatcher.dispatch(
                    TOTPDisabledFromUnusualLocationSecurityEvent(
                        principalId = clientId,
                        action = "${IdActions.SERVICE}:TOTPDisabledFromUnusualLocation",
                        resource = IdResources.platform().toString(),
                        timestamp = java.time.Instant.now(),
                        eventId = securityEventId,
                        accountId = event.accountId,
                        enabledIp = enabledIp,
                        enabledLocation = enabledLoc,
                        disabledIp = disabledIp,
                        disabledLocation = disabledLocation,
                    )
                )
            }
        }

        totpEnableInfo.remove(event.accountId)
    }

    fun removeRecord(accountId: UUID) {
        totpEnableInfo.remove(accountId)
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
