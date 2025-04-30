package app.routes.api

import app.sql.account.Account
import app.sql.account.AccountsTable
import app.sql.tenant.TenantAccountLinksTable
import io.javalin.community.routing.annotations.Endpoints
import io.javalin.community.routing.annotations.Get
import io.javalin.http.Context
import io.javalin.http.NotFoundResponse
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greater
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.SqlExpressionBuilder.like
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.util.*

@Endpoints("/api/v1/accounts")
object RestAccountsRoutes {
    data class AccountDTO(
        val id: UUID,
        val email: String,
        val firstName: String,
        val lastName: String,
        val createdAt: Instant
    )

    @Get
    fun list(ctx: Context) {
        ctx.requireAdministratorAndScope("id:accounts:read")

        val id = ctx.queryParam("id")?.let { UUID.fromString(it) }
        val email = ctx.queryParam("email")
        val firstName = ctx.queryParam("firstName")
        val lastName = ctx.queryParam("lastName")
        val createdAtBefore = ctx.queryParam("createdAt.before")?.toLongOrNull()?.let { Instant.ofEpochMilli(it) }
        val createdAtAfter = ctx.queryParam("createdAt.after")?.toLongOrNull()?.let { Instant.ofEpochMilli(it) }
        val createdAtExact = ctx.queryParam("createdAt.exact")?.toLongOrNull()?.let { Instant.ofEpochMilli(it) }

        val matchAny = ctx.queryParam("any")?.toBooleanStrictOrNull() ?: false
        val tenantQuery = if (ctx.tenantId != null) TenantAccountLinksTable.tenant eq ctx.tenantId else Op.TRUE

        transaction {
            val query = AccountsTable.innerJoin(TenantAccountLinksTable).select { tenantQuery }

            val conditions = mutableListOf<Op<Boolean>>()

            if (id != null)
                conditions.add(TenantAccountLinksTable.account eq id)

            if (email != null)
                conditions.add(AccountsTable.email like "%$email%")

            if (firstName != null)
                conditions.add(AccountsTable.firstName like "%$firstName%")

            if (lastName != null)
                conditions.add(AccountsTable.lastName like "%$lastName%")

            if (createdAtBefore != null)
                conditions.add(AccountsTable.createdAt less createdAtBefore.toEpochMilli())

            if (createdAtAfter != null)
                conditions.add(AccountsTable.createdAt greater createdAtAfter.toEpochMilli())

            if (createdAtExact != null)
                conditions.add(AccountsTable.createdAt eq createdAtExact.toEpochMilli())


            if (matchAny) query.andWhere { conditions.reduce { acc, op -> acc or op } }
            else query.andWhere { conditions.reduce { acc, op -> acc and op } }

            val accounts = query.map { Account.wrapRow(it) }

            if (accounts.isEmpty()) {
                throw NotFoundResponse()
            }

            ctx.json(accounts.map { account ->
                AccountDTO(
                    id = account.id.value,
                    email = account.email,
                    firstName = account.firstName,
                    lastName = account.lastName,
                    createdAt = account.createdAt
                )
            })

            ctx.writeApiAudit("""
                List accounts with filters:
                id=$id, 
                email=$email, 
                firstName=$firstName, 
                lastName=$lastName, 
                createdAtBefore=$createdAtBefore, 
                createdAtAfter=$createdAtAfter, 
                createdAtExact=$createdAtExact;
                Result: ${accounts.size} accounts found and returned.
            """.trimIndent())
        }
    }
}