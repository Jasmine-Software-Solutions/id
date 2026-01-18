import app.app
import app.main
import app.models.account.AccountsTable
import app.models.account.ForgotPasswordCodesTable
import app.models.account.SessionsTable
import app.models.audit.LoginAuditTable
import app.models.audit.SessionAuditTable
import app.models.client.ClientRedirectUrisTable
import app.models.client.ClientsTable
import app.models.oauth2.MachineAccessTokensTable
import app.models.oauth2.SessionAccessTokensTable
import app.models.tenant.TenantAccountLinksTable
import app.models.tenant.TenantsTable
import org.jetbrains.exposed.sql.exists
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class ApplicationTest {
    companion object {
        @BeforeAll
        @JvmStatic
        fun setup() {
            main()
        }

        @AfterAll
        @JvmStatic
        fun teardown() {
            app.stop()
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