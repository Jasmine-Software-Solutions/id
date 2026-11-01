package app.infrastructure.repositories.clients

import app.infrastructure.services.SHA256HashFunction
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.vendors.currentDialect
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files

/**
 * The `clients` table used to store the client secret digest in a column named
 * `argon2_secret_hash`. Constructing [ExposedClientRepository] runs `install()`,
 * which has to rename that column without losing the digests: the secrets are
 * already deployed, and a client whose digest is gone cannot authenticate.
 */
class LegacySecretHashColumnMigrationTest {

    /** The `clients` schema exactly as it existed before the rename. */
    private object LegacyClientsTable : UUIDTable("clients") {
        val createdAt = long("created_at")
        val suspended = bool("suspended")
        val fqdn = varchar("fqdn", 253).uniqueIndex()
        val confidential = bool("confidential")
        val name = varchar("display_name", 255).nullable()
        val logoUri = varchar("logo_uri", 2048).nullable()
        val initiateLoginUri = varchar("initiate_login_uri", 2048).nullable()
        val secretHash = text("argon2_secret_hash")
    }

    /** A fresh file-backed SQLite database, registered as the default. */
    private fun connect(): Database {
        val path = Files.createTempFile("id-clients-migration", ".db")
        path.toFile().deleteOnExit()
        return Database.connect("jdbc:sqlite:${path.toAbsolutePath()}", driver = "org.sqlite.JDBC")
    }

    private fun clientColumnNames(): List<String> = transaction {
        currentDialect.tableColumns(ExposedClientRepository.Table)[ExposedClientRepository.Table]
            .orEmpty()
            .map { it.name.lowercase() }
    }

    @Test
    fun `renames the legacy column in place and keeps the digests readable`() {
        connect()

        val secret = "topsecret-service-secret"
        val digest = SHA256HashFunction().hash(secret.toByteArray())

        transaction {
            SchemaUtils.create(LegacyClientsTable)
            LegacyClientsTable.insert {
                it[fqdn] = "service.example.com"
                it[suspended] = false
                it[confidential] = true
                it[createdAt] = 0L
                it[secretHash] = digest
            }
        }

        // Constructing the repository runs install(), which migrates the schema.
        val repository = ExposedClientRepository(SHA256HashFunction())

        val columns = clientColumnNames()
        assertTrue("secret_hash" in columns, "expected the renamed column, got $columns")
        assertFalse("argon2_secret_hash" in columns, "the legacy column should be gone, got $columns")

        val client = repository.findByFqdn("service.example.com")!!
        assertTrue(client.verify(secret), "the existing digest must still verify after the rename")
        assertFalse(client.verify("wrong"))
    }

    @Test
    fun `creates a fresh schema directly under the new column name`() {
        connect()

        ExposedClientRepository(SHA256HashFunction())

        val columns = clientColumnNames()
        assertTrue("secret_hash" in columns, "expected secret_hash on a fresh database, got $columns")
        assertFalse("argon2_secret_hash" in columns, "a fresh database must not grow the legacy column, got $columns")
    }
}
