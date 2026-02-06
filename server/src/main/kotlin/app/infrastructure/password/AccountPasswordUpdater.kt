package app.infrastructure.password

import app.Env
import app.application.password.IAccountPasswordUpdater
import app.infrastructure.models.account.Account
import app.infrastructure.models.account.Password
import de.mkammerer.argon2.Argon2Factory
import java.time.Instant

class AccountPasswordUpdater : IAccountPasswordUpdater {
    private val argon2 = Argon2Factory.create(
        Env.PASSWORD_SALT_LENGTH,
        1024
    )

    override fun update(account: Account, password: String) {
        val passwordArray = password.toCharArray()
        val hashedPassword = try {
            argon2.hash(
                Env.PASSWORD_HASH_ITERATIONS,
                Env.ARGON2_MEMORY,
                Env.ARGON2_PARALLELISM,
                passwordArray
            )
        } finally {
            argon2.wipeArray(passwordArray)
        }

        Password.new {
            this.createdAt = Instant.now()
            this.account = account
            this.passwordHash = hashedPassword
        }
    }
}