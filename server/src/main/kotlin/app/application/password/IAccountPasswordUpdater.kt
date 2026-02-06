package app.application.password

import app.infrastructure.models.account.Account

interface IAccountPasswordUpdater {
    fun update(account: Account, password: String)
}