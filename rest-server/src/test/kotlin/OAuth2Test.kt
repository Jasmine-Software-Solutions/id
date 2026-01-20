import app.Env
import app.app
import app.etc.SecureToken
import app.main
import app.models.account.Account
import app.models.account.Password
import app.models.account.Session
import app.models.client.Client
import app.models.client.ClientRedirectUri
import app.models.oauth2.MachineAccessTokens
import app.models.oauth2.SessionAccessTokens
import app.models.tenant.Tenant
import app.models.tenant.TenantAccountLinksTable
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
import java.time.temporal.ChronoUnit
import java.util.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OAuth2Test {
    private lateinit var client: HttpClient
    private lateinit var testAccount: Account
    private lateinit var testPassword: String
    private lateinit var testSession: Session
    private lateinit var testTenant: Tenant
    private lateinit var testClient: Client
    private lateinit var testRedirectUri: ClientRedirectUri

    @BeforeAll
    fun setup() {
        main()
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
            Password.new(testAccount, testPassword)
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
        app.stop()
    }

    @Test
    fun `token endpoint returns error for missing grant_type`() {
        val url = "${Env.Test.URL}/api/v1/oauth2/token"
        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .GET()
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        assertEquals(400, response.statusCode())
    }

    @Test
    fun `token endpoint returns error for invalid grant_type`() {
        val url = "${Env.Test.URL}/api/v1/oauth2/token?grant_type=invalid"
        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .GET()
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        assertEquals(400, response.statusCode())
    }

    @Test
    fun `token endpoint returns error for missing code in authorization_code grant`() {
        val url = "${Env.Test.URL}/api/v1/oauth2/token?grant_type=authorization_code"
        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .GET()
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        assertEquals(400, response.statusCode())
    }

    @Test
    fun `token endpoint returns error for invalid code in authorization_code grant`() {
        val url = "${Env.Test.URL}/api/v1/oauth2/token?grant_type=authorization_code&code=invalid&client_id=${testClient.id.value}&redirect_uri=${testRedirectUri.uri}"
        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .GET()
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        assertEquals(400, response.statusCode())
    }

    @Test
    fun `token endpoint returns tokens for valid authorization_code grant`() {
        val tokens = transaction {
            SessionAccessTokens.new {
                this.session = testSession
                this.client = testClient
                this.redirectUri = testRedirectUri

                this.accessToken = SecureToken()
                this.refreshToken = SecureToken()

                this.tenant = testTenant
                this.scope = null

                this.authorizationCode = SecureToken()
                this.authorizationCodeExpiration = Instant.now().plus(10, ChronoUnit.MINUTES)
            }
        }

        val mat = transaction {
            MachineAccessTokens.new {
                this.client = testClient
                this.issuedAt = Instant.now()
                this.expiresAt = Instant.now().plus(1, ChronoUnit.DAYS)
                this.accessToken = SecureToken()
                this.scope = null
            }
        }

        val tokenUrl = "${Env.Test.URL}/api/v1/oauth2/token?grant_type=authorization_code&code=${tokens.authorizationCode}&client_id=${testClient.id.value}&redirect_uri=${testRedirectUri.uri}"
        val tokenRequest = HttpRequest.newBuilder()
            .uri(URI.create(tokenUrl))
            .header("Authorization", "Bearer ${mat.accessToken}")
            .GET()
            .build()
        val tokenResponse = client.send(tokenRequest, HttpResponse.BodyHandlers.ofString())
        assertEquals(200, tokenResponse.statusCode())
        assertTrue(tokenResponse.body().contains("access_token"))
        assertTrue(tokenResponse.body().contains("refresh_token"))
    }

    @Test
    fun `token endpoint returns error for invalid refresh_token`() {
        val url = "${Env.Test.URL}/api/v1/oauth2/token?grant_type=refresh_token&refresh_token=invalid"
        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .GET()
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        assertEquals(400, response.statusCode())
    }

    @Test
    fun `token endpoint returns new tokens for valid refresh_token`() {
        val tokens = transaction {
            SessionAccessTokens.new {
                this.session = testSession
                this.client = testClient
                this.redirectUri = testRedirectUri

                this.accessToken = SecureToken()
                this.refreshToken = SecureToken()

                this.tenant = testTenant
                this.scope = null

                this.authorizationCode = null
                this.authorizationCodeExpiration = null
                this.lastRefreshed = Instant.now()
            }
        }

        val mat = transaction {
            MachineAccessTokens.new {
                this.client = testClient
                this.issuedAt = Instant.now()
                this.expiresAt = Instant.now().plus(1, ChronoUnit.DAYS)
                this.accessToken = SecureToken()
                this.scope = null
            }
        }

        val refreshUrl = "${Env.Test.URL}/api/v1/oauth2/token?grant_type=refresh_token&refresh_token=${tokens.refreshToken}"
        val refreshRequest = HttpRequest.newBuilder()
            .uri(URI.create(refreshUrl))
            .header("Authorization", "Bearer ${mat.accessToken}")
            .GET()
            .build()
        val refreshResponse = client.send(refreshRequest, HttpResponse.BodyHandlers.ofString())
        assertEquals(200, refreshResponse.statusCode())
        assertTrue(refreshResponse.body().contains("access_token"))
        assertTrue(refreshResponse.body().contains("refresh_token"))
    }

    @Test
    fun `token endpoint returns error for missing Authorization header in client_credentials grant`() {
        val url = "${Env.Test.URL}/api/v1/oauth2/token?grant_type=client_credentials"
        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .GET()
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        assertEquals(400, response.statusCode())
    }

    @Test
    fun `token endpoint returns error for invalid client credentials`() {
        val url = "${Env.Test.URL}/api/v1/oauth2/token?grant_type=client_credentials"
        val invalidCreds = Base64.getEncoder().encodeToString("invalid:invalid".toByteArray())
        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Authorization", "Basic $invalidCreds")
            .GET()
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        assertEquals(400, response.statusCode())
    }

    @Test
    fun `token endpoint returns access token for valid client_credentials`() {
        // Make the test client confidential and set a secret
        transaction {
            testClient.confidential = true
            testClient.secret = "testsecret"
        }

        val url = "${Env.Test.URL}/api/v1/oauth2/token?grant_type=client_credentials"
        val creds = Base64.getEncoder().encodeToString("${testClient.id.value}:testsecret".toByteArray())
        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Authorization", "Basic $creds")
            .GET()
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        assertEquals(200, response.statusCode())
        assertTrue(response.body().contains("access_token"))
    }
} 