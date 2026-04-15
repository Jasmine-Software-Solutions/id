package app.domain.services.accounts

import app.domain.models.account.IAccount
import app.domain.models.account.IMagicLink

interface IMagicLinkService {
    fun create(account: IAccount): IMagicLink
}