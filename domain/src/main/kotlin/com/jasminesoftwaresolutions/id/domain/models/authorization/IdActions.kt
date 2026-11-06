package com.jasminesoftwaresolutions.id.domain.models.authorization

/**
 * Centralized action constants for Witchwood policy evaluation.
 * Every authorization check in ID controllers should reference these.
 */
object IdActions {
    const val SERVICE = "id.jasmine.software"

    // Account actions
    const val GET_ACCOUNT = SERVICE + ":GetAccount"
    const val UPDATE_ACCOUNT_PROFILE = SERVICE + ":UpdateAccountProfile"
    const val SET_ACCOUNT_PASSWORD = SERVICE + ":SetAccountPassword"
    const val GET_ACCOUNT_TOTP = SERVICE + ":GetAccountTOTP"
    const val ENABLE_ACCOUNT_TOTP = SERVICE + ":EnableAccountTOTP"
    const val DISABLE_ACCOUNT_TOTP = SERVICE + ":DisableAccountTOTP"
    const val CONFIRM_ACCOUNT_TOTP = SERVICE + ":ConfirmAccountTOTP"
    const val LIST_ACCOUNT_SESSIONS = SERVICE + ":ListAccountSessions"
    const val REVOKE_ACCOUNT_SESSION = SERVICE + ":RevokeAccountSession"
    const val REVOKE_ALL_ACCOUNT_SESSIONS = SERVICE + ":RevokeAllAccountSessions"
    const val LIST_ACCOUNT_ROLES = SERVICE + ":ListAccountRoles"
    const val LIST_ACCOUNT_MEMBERSHIPS = SERVICE + ":ListAccountMemberships"
    const val LIST_ACCOUNT_POLICIES = SERVICE + ":ListAccountPolicies"

    // Role actions
    const val LIST_ROLES = SERVICE + ":ListRoles"
    const val CREATE_ROLE = SERVICE + ":CreateRole"
    const val UPDATE_ROLE = SERVICE + ":UpdateRole"
    const val DELETE_ROLE = SERVICE + ":DeleteRole"
    const val ASSIGN_ROLE = SERVICE + ":AssignRole"
    const val UNASSIGN_ROLE = SERVICE + ":UnassignRole"
    const val LIST_ROLE_ASSIGNMENTS = SERVICE + ":ListRoleAssignments"
    const val LIST_CLIENT_ROLES = SERVICE + ":ListClientRoles"

    // Policy actions
    const val LIST_POLICIES = SERVICE + ":ListPolicies"
    const val CREATE_POLICY = SERVICE + ":CreatePolicy"
    const val UPDATE_POLICY = SERVICE + ":UpdatePolicy"
    const val DELETE_POLICY = SERVICE + ":DeletePolicy"
    const val UNASSIGN_POLICY = SERVICE + ":UnassignPolicy"
    const val GET_POLICY = SERVICE + ":GetPolicy"
    const val LIST_POLICY_ASSIGNMENTS = SERVICE + ":ListPolicyAssignments"
    const val ASSIGN_POLICY = SERVICE + ":AssignPolicy"
    const val LIST_ROLE_POLICIES = SERVICE + ":ListRolePolicies"
    const val LIST_CLIENT_POLICIES = SERVICE + ":ListClientPolicies"

    // Client actions
    const val LIST_CLIENTS = SERVICE + ":ListClients"
    const val GET_CLIENT = SERVICE + ":GetClient"
    const val CREATE_CLIENT = SERVICE + ":CreateClient"
    const val UPDATE_CLIENT = SERVICE + ":UpdateClient"
    const val DELETE_CLIENT = SERVICE + ":DeleteClient"
    const val RESET_CLIENT_SECRET = SERVICE + ":ResetClientSecret"
    const val MANAGE_CLIENT_SCOPES = SERVICE + ":ManageClientScopes"
    const val GET_SSO_APPLICATION = SERVICE + ":GetSSOApplication"

    // Membership actions
    const val LIST_MEMBERSHIPS = SERVICE + ":ListMemberships"
    const val GET_MEMBERSHIP = SERVICE + ":GetMembership"
    const val CREATE_MEMBERSHIP = SERVICE + ":CreateMembership"
    const val UPDATE_MEMBER_PROFILE = SERVICE + ":UpdateMemberProfile"
    const val UPDATE_MEMBER_SECURITY = SERVICE + ":UpdateMemberSecurity"
    const val DELETE_MEMBERSHIP = SERVICE + ":DeleteMembership"

    // Invitation actions
    const val INVITE_ACCOUNT = SERVICE + ":InviteAccount"
    const val ACCEPT_INVITATION = SERVICE + ":AcceptInvitation"

    // Account management actions (admin)
    const val CREATE_ACCOUNT = SERVICE + ":CreateAccount"
    const val LIST_ACCOUNTS = SERVICE + ":ListAccounts"
    const val DELETE_ACCOUNT = SERVICE + ":DeleteAccount"

    // Role detail action
    const val GET_ROLE = SERVICE + ":GetRole"

    // Tenant CRUD actions
    const val LIST_TENANTS = SERVICE + ":ListTenants"
    const val CREATE_TENANT = SERVICE + ":CreateTenant"
    const val UPDATE_TENANT = SERVICE + ":UpdateTenant"
    const val DELETE_TENANT = SERVICE + ":DeleteTenant"
    const val GET_TENANT = SERVICE + ":GetTenant"

    // Restriction actions
    const val PLACE_RESTRICTION = SERVICE + ":PlaceRestriction"
    const val LIFT_RESTRICTION = SERVICE + ":LiftRestriction"
    const val LIST_RESTRICTIONS = SERVICE + ":ListRestrictions"

    // Handoff actions
    /**
     * Held by a client that may create and complete a cross-device handoff of
     * one of its user's ID sessions. Checked when the embed loads, when the
     * user confirms, and again when the code is exchanged.
     */
    const val ACCEPT_HANDOFF = SERVICE + ":AcceptHandoff"

    /** All registered actions (for Witchwood bootstrap). */
    val ALL: List<String> = listOf(
        GET_ACCOUNT,
        UPDATE_ACCOUNT_PROFILE,
        SET_ACCOUNT_PASSWORD,
        GET_ACCOUNT_TOTP,
        ENABLE_ACCOUNT_TOTP,
        DISABLE_ACCOUNT_TOTP,
        CONFIRM_ACCOUNT_TOTP,
        LIST_ACCOUNT_SESSIONS,
        REVOKE_ACCOUNT_SESSION,
        REVOKE_ALL_ACCOUNT_SESSIONS,
        LIST_ROLES,
        CREATE_ROLE,
        UPDATE_ROLE,
        DELETE_ROLE,
        GET_ROLE,
        ASSIGN_ROLE,
        UNASSIGN_ROLE,
        LIST_ROLE_ASSIGNMENTS,
        LIST_CLIENT_ROLES,
        LIST_POLICIES,
        CREATE_POLICY,
        UPDATE_POLICY,
        DELETE_POLICY,
        UNASSIGN_POLICY,
        GET_POLICY,
        LIST_POLICY_ASSIGNMENTS,
        ASSIGN_POLICY,
        LIST_ROLE_POLICIES,
        LIST_CLIENT_POLICIES,
        LIST_CLIENTS,
        GET_CLIENT,
        CREATE_CLIENT,
        UPDATE_CLIENT,
        DELETE_CLIENT,
        RESET_CLIENT_SECRET,
        MANAGE_CLIENT_SCOPES,
        GET_SSO_APPLICATION,
        LIST_MEMBERSHIPS,
        GET_MEMBERSHIP,
        CREATE_MEMBERSHIP,
        UPDATE_MEMBER_PROFILE,
        UPDATE_MEMBER_SECURITY,
        DELETE_MEMBERSHIP,
        INVITE_ACCOUNT,
        ACCEPT_INVITATION,
        CREATE_ACCOUNT,
        LIST_ACCOUNTS,
        DELETE_ACCOUNT,
        LIST_TENANTS,
        CREATE_TENANT,
        UPDATE_TENANT,
        DELETE_TENANT,
        GET_TENANT,
        PLACE_RESTRICTION,
        LIFT_RESTRICTION,
        LIST_RESTRICTIONS,
        ACCEPT_HANDOFF,
    )
}
