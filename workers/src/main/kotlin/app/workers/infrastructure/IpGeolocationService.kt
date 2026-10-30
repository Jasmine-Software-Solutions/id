package app.workers.infrastructure

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.jasminesoftwaresolutions.id.domain.events.IWorker
import com.jasminesoftwaresolutions.id.domain.events.SecurityEventLocation
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class IpGeolocationService(
    private val http: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build(),
    private val gson: Gson = Gson(),
) : IWorker {

    private val cache = ConcurrentHashMap<String, CachedLocation>()
    private val pending = ConcurrentLinkedQueue<PendingLookup>()
    private val running = AtomicBoolean(false)
    private val executor = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "ip-geolocation-batch").also { it.isDaemon = true }
    }

    /**
     * Looks up the geolocation for an IP address.
     * Returns a cached result if available and not expired (24-hour TTL).
     * If no cache hit, submits a lookup and blocks the caller until the response arrives.
     */
    fun lookup(ip: String): SecurityEventLocation? {
        val future = CompletableFuture<SecurityEventLocation?>()
        lookupAsync(ip) { location -> future.complete(location) }
        return future.get()
    }

    /**
     * Asynchronously looks up the geolocation for an IP address.
     * [callback] is invoked on the background batch thread when the lookup completes.
     */
    fun lookupAsync(ip: String, callback: (SecurityEventLocation?) -> Unit) {
        val cached = cache[ip]
        if (cached != null && !isExpired(cached)) {
            callback(cached.location)
            return
        }

        pending.add(PendingLookup(ip, callback))
    }

    override fun start() {
        running.set(true)
        log.info("IpGeolocationService started (batch interval: 5s)")

        executor.scheduleWithFixedDelay(
            { processBatch() },
            5,
            5,
            TimeUnit.SECONDS,
        )
    }

    override fun stop() {
        running.set(false)
        executor.shutdown()
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow()
            }
        } catch (e: InterruptedException) {
            executor.shutdownNow()
            Thread.currentThread().interrupt()
        }
        log.info("IpGeolocationService stopped")
    }

    private fun processBatch() {
        if (!running.get()) return

        val batch = mutableListOf<PendingLookup>()
        while (batch.size < MAX_BATCH_SIZE) {
            val item = pending.poll() ?: break
            batch.add(item)
        }

        if (batch.isEmpty()) return

        val uniqueIps = batch.map { it.ip }.distinct()
        log.debug("Batch lookup: {} IPs", uniqueIps.size)

        val results: Map<String, SecurityEventLocation?>
        try {
            results = fetchBatch(uniqueIps)
        } catch (e: Exception) {
            log.error("Batch fetch failed: {}", e.message, e)
            pending.addAll(batch)
            return
        }

        for (lookup in batch) {
            val location = results[lookup.ip]
            val cached = CachedLocation(
                location = location,
                cachedAt = Instant.now(),
            )
            cache[lookup.ip] = cached

            try {
                lookup.callback(location)
            } catch (e: Exception) {
                log.error("Callback failed for IP {}: {}", lookup.ip, e.message, e)
            }
        }
    }

    private fun fetchBatch(ips: List<String>): Map<String, SecurityEventLocation?> {
        val body = gson.toJson(ips.map { IpApiBatchRequest(it) })

        val request = HttpRequest.newBuilder()
            .uri(URI.create(BATCH_ENDPOINT))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .timeout(Duration.ofSeconds(30))
            .build()

        val response = http.send(request, HttpResponse.BodyHandlers.ofString())

        if (response.statusCode() != 200) {
            throw IllegalStateException("ip-api returned HTTP ${response.statusCode()}")
        }

        val responseList: List<IpApiResponse> = gson.fromJson(
            response.body(),
            object : TypeToken<List<IpApiResponse>>() {}.type,
        )

        return responseList.associate { resp ->
            val location = if (resp.status == "success") {
                SecurityEventLocation(
                    country = resp.country,
                    city = resp.city,
                    latitude = resp.lat,
                    longitude = resp.lon,
                )
            } else {
                log.warn("ip-api lookup failed for {}: {}", resp.query, resp.message)
                cache[resp.query] = CachedLocation(
                    location = null,
                    cachedAt = Instant.now(),
                )
                null
            }
            resp.query to location
        }
    }

    private fun isExpired(cached: CachedLocation): Boolean =
        cached.cachedAt.plus(CACHE_TTL_HOURS, ChronoUnit.HOURS).isBefore(Instant.now())

    data class CachedLocation(
        val location: SecurityEventLocation?,
        val cachedAt: Instant,
    )

    data class PendingLookup(
        val ip: String,
        val callback: (SecurityEventLocation?) -> Unit,
    )

    private data class IpApiBatchRequest(val query: String)

    private data class IpApiResponse(
        val status: String,
        val country: String?,
        val city: String?,
        val lat: Double?,
        val lon: Double?,
        val query: String,
        val message: String?,
    )

    companion object {
        private val log = LoggerFactory.getLogger(IpGeolocationService::class.java)

        private const val BATCH_ENDPOINT = "http://ip-api.com/batch"
        private const val MAX_BATCH_SIZE = 100
        private const val CACHE_TTL_HOURS = 24L
    }
}
