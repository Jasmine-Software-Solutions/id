import app.AjaxApplication
import app.infrastructure.models.account.*
import app.infrastructure.models.audit.LoginAuditTable
import app.infrastructure.models.audit.SessionAuditTable
import app.infrastructure.models.client.ClientRedirectUrisTable
import app.infrastructure.models.client.ClientsTable
import app.infrastructure.models.oauth2.MachineAccessTokensTable
import app.infrastructure.models.oauth2.SessionAccessTokensTable
import app.infrastructure.models.tenant.TenantAccountLinksTable
import app.infrastructure.models.tenant.TenantsTable
import org.jetbrains.exposed.sql.exists
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class ApplicationTest {
    companion object {
        private lateinit var application: AjaxApplication

        @BeforeAll
        @JvmStatic
        fun setup() {
            application = AjaxApplication()
            application.start()
        }

        @AfterAll
        @JvmStatic
        fun teardown() {
            application.stop()
        }
    }

    @Test
    fun `starting application creates necessary tables`() {
        // This test will pass if the application starts without exceptions
        // and the in-memory database is created successfully.

        transaction {
            // Check if the tables are created
            assertTrue(ClientsTable.exists())
            assertTrue(ClientRedirectUrisTable.exists())
            assertTrue(AccountsTable.exists())
            assertTrue(SessionsTable.exists())
            assertTrue(TOTPConfigurationTable.exists())
            assertTrue(TOTPUsageTable.exists())
            assertTrue(ForgotPasswordCodesTable.exists())
            assertTrue(LoginAuditTable.exists())
            assertTrue(SessionAuditTable.exists())
            assertTrue(TenantsTable.exists())
            assertTrue(TenantAccountLinksTable.exists())
            assertTrue(SessionAccessTokensTable.exists())
            assertTrue(MachineAccessTokensTable.exists())
        }
    }
}