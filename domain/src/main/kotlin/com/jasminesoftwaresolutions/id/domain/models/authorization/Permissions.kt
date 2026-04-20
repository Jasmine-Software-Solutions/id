package com.jasminesoftwaresolutions.id.domain.models.authorization

import com.jasminesoftwaresolutions.id.domain.models.ICreated
import com.jasminesoftwaresolutions.id.domain.models.IIdentified

interface IPermission : IIdentified, ICreated {
    var role: IRole
    var privileges: Set<IPrivilege>
}