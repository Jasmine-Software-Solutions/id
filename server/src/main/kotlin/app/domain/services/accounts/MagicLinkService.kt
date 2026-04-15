package app.domain.services.accounts

import app.domain.models.account.IAccount
import app.domain.models.account.IMagicLink

interface IMagicLinkService<T : IMagicLink> {
    fun create(account: IAccount): T
}