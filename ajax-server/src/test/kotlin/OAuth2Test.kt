import app.AjaxApplication
import app.Env
import app.infrastructure.models.account.Account
import app.infrastructure.models.account.Session
import app.infrastructure.models.client.Client
import app.infrastructure.models.client.ClientRedirectUri
import app.infrastructure.models.tenant.Tenant
import app.infrastructure.models.tenant.TenantAccountLinksTable
import app.infrastructure.password.AccountPasswordUpdater
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Instant
import java.util.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OAuth2Test {
    private lateinit var application: AjaxApplication

    private lateinit var client: HttpClient
    private lateinit var testAccount: Account
    private lateinit var testPassword: String
    private lateinit var testSession: Session
    private lateinit var testTenant: Tenant
    private lateinit var testClient: Client
    private lateinit var testRedirectUri: ClientRedirectUri

    @BeforeAll
    fun setup() {
        application = AjaxApplication()
        application.start()

        client = HttpClient.newHttpClient()
        transaction {
            // Create tenant
            testTenant = Tenant.new {
                createdAt = Instant.now()
                name = "TestTenant"
                suspended = false
                openbox = false
            }
            // Create account
            testAccount = Account.new {
                createdAt = Instant.now()
                email = "oauth2user@test"
                firstName = "OAuth2"
                lastName = "User"
                systemAdmin = true
                totpSecret = null
            }
            // Link account to tenant
            TenantAccountLinksTable.insertIgnore {
                it[tenant] = testTenant.id
                it[account] = testAccount.id
                it[administrator] = true
            }
            // Set password
            testPassword = "password:oauth2user@test"
            AccountPasswordUpdater().update(testAccount, testPassword)
            // Create OAuth2 client
            testClient = Client.new {
                createdAt = Instant.now()
                name = "TestClient"
                confidential = false
                secret = null
                automaticGrant = true
                scope = "openid profile"
                suspended = false
            }
            // Add redirect URI
            testRedirectUri = ClientRedirectUri.new {
                client = testClient
                uri = "http://localhost/callback"
            }
        }
        // Create session for the user
        transaction {
            testSession = Session.new {
                createdAt = Instant.now()
                accessedAt = Instant.now()
                expiresAt = Instant.now().plusSeconds(3600)
                invalidatedAt = null
                account = testAccount
                userAgent = "JUnit"
                ipAddress = "127.0.0.1"
                token = UUID.randomUUID().toString().replace("-", "").substring(0, 32)
            }
        }
    }

    @AfterAll
    fun teardown() {
        application.stop()
    }

    @Test
    fun `authorize endpoint returns error for missing parameters`() {
        val url = "${Env.Test.URL}/oauth2/authorize"
        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .GET()
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        assertEquals(400, response.statusCode())
    }

    @Test
    fun `authorize endpoint returns error for invalid client_id`() {
        val url = "${Env.Test.URL}/oauth2/authorize?response_type=code&client_id=${UUID.randomUUID()}&redirect_uri=http://localhost/callback"
        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .GET()
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        assertEquals(400, response.statusCode())
    }

    @Test
    fun `authorize endpoint returns error for invalid redirect_uri`() {
        val url = "${Env.Test.URL}/oauth2/authorize?response_type=code&client_id=${testClient.id.value}&redirect_uri=http://invalid/callback"
        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .GET()
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        assertEquals(400, response.statusCode())
    }

    @Test
    fun `authorize endpoint returns redirect with code for valid request`() {
        val url = "${Env.Test.URL}/oauth2/authorize?response_type=code&client_id=${testClient.id.value}&redirect_uri=${testRedirectUri.uri}&scope=openid&tenant=${testTenant.id.value}"
        val cookie = "session=${testSession.token}"
        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Cookie", cookie)
            .GET()
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        // Should redirect (302 or 303) to the redirect_uri with code
        assertTrue(response.statusCode() in 300..399)
        val location = response.headers().firstValue("Location")
        assertTrue(location.isPresent)
        assertTrue(location.get().contains("code="))
        assertTrue(location.get().contains("tenant=${testTenant.id.value}"))
    }
} 