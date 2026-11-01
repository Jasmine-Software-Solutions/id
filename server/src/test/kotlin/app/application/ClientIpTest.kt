package app.application

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * How the client's address is read out of the configured forwarded-for header.
 *
 * The parse is small but load-bearing twice over: an address read wrongly here
 * is the address the rapid-creation detector counts and the address blocks are
 * placed against.
 */
class ClientIpTest {

    private val peer = "172.28.0.19"

    @Test
    fun `no header means the socket peer is the client`() {
        // The request came around the proxy, or there is no proxy at all.
        assertEquals(peer, clientIp(null, peer))
    }

    @Test
    fun `a blank header falls back to the peer rather than naming nobody`() {
        assertEquals(peer, clientIp("", peer))
        assertEquals(peer, clientIp("   ", peer))
    }

    @Test
    fun `a list yields the left-most address, the original client`() {
        assertEquals("203.0.113.7", clientIp("203.0.113.7, 172.28.0.19, 172.28.0.1", peer))
    }

    @Test
    fun `surrounding whitespace is trimmed`() {
        // A header name configured with stray whitespace would otherwise never
        // match, and the client would silently be the peer again.
        assertEquals("203.0.113.7", clientIp("  203.0.113.7  ", peer))
    }
}
