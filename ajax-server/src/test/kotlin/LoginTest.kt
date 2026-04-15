import app.AjaxApplication
import app.Env
import app.infrastructure.account.AccountTOTPEngine
import app.infrastructure.models.account.Account
import app.infrastructure.models.account.SessionsTable
import app.infrastructure.models.audit.LoginAuditTable
import com.microsoft.playwright.Browser
import com.microsoft.playwright.BrowserType
import com.microsoft.playwright.Page
import com.microsoft.playwright.Playwright
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.time.Instant

class LoginTest {
    companion object {
        private lateinit var application: AjaxApplication
        private lateinit var browser: Browser

        private lateinit var accountWithTotp: Account

        @BeforeAll
        @JvmStatic
        fun setup() {
            application = AjaxApplication()
            application.start()

            // Create an account with a password
            transaction {
                val account = Account.new {
                    this.createdAt = Instant.now()
                    this.email = "passwordUser@LoginTest"
                    this.firstName = "Test"
                    this.lastName = "User"
                    this.systemAdmin = false
                }

                AccountPasswordUpdater().update(account, "password:passwordUser@LoginTest")
            }

            // Create an account with a TOTP secret
            transaction {
                val account = Account.new {
                    this.createdAt = Instant.now()
                    this.email = "totpUser@LoginTest"
                    this.firstName = "Test"
                    this.lastName = "User"
                    this.systemAdmin = false
                }

                AccountTOTPEngine.create(account)

                AccountPasswordUpdater().update(account, "password:totpUser@LoginTest")
                accountWithTotp = account
            }

            // Create a Chromium browser with the necessary configuration
            // for logging in
            browser = Playwright.create().chromium().launch(BrowserType.LaunchOptions()
                .setHeadless(Env.Test.HEADLESS)
            )
        }

        @AfterAll
        @JvmStatic
        fun teardown() {
            // Print all login logs
            transaction {
                val logs = LoginAuditTable.selectAll()
                logs.forEach {
                    println("Login Audit: ${it[LoginAuditTable.action]}")
                }
            }

            application.stop()
            browser.close()
        }
    }

    @Test
    fun `login to existing password account returns valid session token`() {
        // Open http://localhost:80/login
        // Enter the email and password for the Password User account
        // Click the login button
        // Verify that the session token was issued

        val context = browser.newContext()
        val page = context.newPage()

        page.navigate("${Env.Test.URL}/login")

        page.waitForSelector("#email")
        page.fill("#email", "passwordUser@LoginTest")

        page.waitForSelector("#password")
        page.fill("#password", "password:passwordUser@LoginTest")

        page.waitForSelector("button[type=\"submit\"]")
        page.click("button[type=\"submit\"]")

        page.waitForTimeout(Env.Test.LOGIN_TIMEOUT)

        // Wait for the session token to be issued
        val cookie = page.context().cookies().find { it.name == "session" }
            ?: throw AssertionError("Session token was not issued")

        // Verify that the session token is valid
        transaction {
            SessionsTable.select { SessionsTable.token eq cookie.value }.limit(1).firstOrNull()
                ?: throw AssertionError("Session token was not found in the database")
        }
    }

    @Test
    fun `login to existing totp account requires TOTP`() {
        // Open http://localhost:80/login
        // Enter the email and password for the TOTP User account
        // Click the login button
        // Verify that the TOTP input is displayed

        val context = browser.newContext()
        val page = context.newPage()

        page.navigate("${Env.Test.URL}/login")

        page.waitForSelector("#email")
        page.fill("#email", "totpUser@LoginTest")

        page.waitForSelector("#password")
        page.fill("#password", "password:totpUser@LoginTest")

        page.waitForSelector("button[type=\"submit\"]")
        page.click("button[type=\"submit\"]")

        page.waitForSelector("#otp", Page.WaitForSelectorOptions().setTimeout(Env.Test.LOGIN_TIMEOUT))
            ?: throw AssertionError("OTP input was not displayed")

        // Verify that the session token was not issued
        if (page.context().cookies().any { it.name == "session" })
            throw AssertionError("Session token was issued before providing OTP")
    }

    @Test
    fun `login to existing totp account with totp returns valid session token`() {
        // Open http://localhost:80/login
        // Enter the email and password for the TOTP User account
        // Enter the TOTP code
        // Click the login button
        // Verify that the session token was issued

        val context = browser.newContext()
        val page = context.newPage()

        page.navigate("${Env.Test.URL}/login")

        page.waitForSelector("#email")
        page.fill("#email", "totpUser@LoginTest")

        page.waitForSelector("#password")
        page.fill("#password", "password:totpUser@LoginTest")

        page.waitForSelector("button[type=\"submit\"]")
        page.click("button[type=\"submit\"]")

        val totp = AccountTOTPEngine.generate(accountWithTotp)

        page.waitForSelector("#otp", Page.WaitForSelectorOptions().setTimeout(Env.Test.LOGIN_TIMEOUT))
        page.fill("#otp", totp)

        page.waitForSelector("button[type=\"submit\"]")
        page.click("button[type=\"submit\"]")

        page.waitForTimeout(Env.Test.LOGIN_TIMEOUT)

        // Wait for the session token to be issued
        val cookie = page.context().cookies().find { it.name == "session" }
            ?: throw AssertionError("Session token was not issued")

        // Verify that the session token is valid
        transaction {
            SessionsTable.select { SessionsTable.token eq cookie.value }.limit(1).firstOrNull()
                ?: throw AssertionError("Session token was not found in the database")
        }
    }
}