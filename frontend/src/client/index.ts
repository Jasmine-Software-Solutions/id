import type {
  Account, Session, Role, Membership, TotpStatus,
  CreateAccountPayload, UpdateProfilePayload,
  StartEmailChangePayload, EmailChangeStatus,
  FlowStartResponse, FlowNextResponse, FlowInsteadResponse,
  OAuth2TokenResponse, ClientScopesResponse, ScopeDef, IdApiError,
  RoleDefinition, RoleAssignment, CreateRolePayload, AssignRolePayload,
  PolicyDefinition, PolicyAssignment, CreatePolicyPayload, UpdatePolicyPayload, AssignPolicyPayload,
  CreateMembershipPayload,
  TenantInfo, UpdateRolePayload,
  RestrictionResponse, PlaceRestrictionPayload,
  SsoClientInfo, SsoPreferences,
  CreateHandoffPayload, HandoffResponse, HandoffStatusResponse,
  HandoffQrResponse, HandoffScanResponse, HandoffScanStatusResponse,
  HandoffClaimResponse,
} from './types'
import { RestrictedError } from './types'

const BASE = '/api/v1'

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

async function request<T>(
  url: string,
  init?: RequestInit,
): Promise<T> {
  const r = await fetch(url, init)
  if (r.status === 403 && r.headers.get('X-ID-Restricted') === 'true') {
    throw new RestrictedError()
  }
  if (!r.ok) {
    let msg: string
    try { msg = await r.text() } catch { msg = r.statusText }
    throw { status: r.status, message: msg, name: 'IdApiError' } satisfies IdApiError
  }
  // 204 or empty body
  const ct = r.headers.get('content-type')
  if (r.status === 204 || !ct || !ct.includes('application/json')) return undefined as unknown as T
  return r.json() as Promise<T>
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
function jsonBody(body: Record<string, any>): RequestInit {
  return {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  }
}

// ---------------------------------------------------------------------------
// Auth
// ---------------------------------------------------------------------------

export function createAuthClient() {
  return {
    /** Start a new authentication flow. */
    async start(): Promise<FlowStartResponse> {
      return request<FlowStartResponse>(`${BASE}/auth/flows`, { method: 'POST' })
    },

    /** Advance a flow with a step response. */
    async next(flowId: string, signedRequest: string, response: Record<string, string>): Promise<FlowNextResponse> {
      return request<FlowNextResponse>(
        `${BASE}/auth/flows/${flowId}/next`,
        jsonBody({ request: signedRequest, response }),
      )
    },

    /** Switch to a different step in the flow. */
    async instead(flowId: string, stepFqdn: string): Promise<FlowInsteadResponse> {
      return request<FlowInsteadResponse>(
        `${BASE}/auth/flows/${flowId}/instead`,
        jsonBody({ step: stepFqdn }),
      )
    },

    /** Approve a magic-link sign-in. Returns `true` when approved. */
    async resolveMagicLink(id: string, token: string): Promise<boolean> {
      const r = await fetch(
          `${BASE}/auth/magic-links/${id}?token=${encodeURIComponent(token)}`,
          { method: 'POST' }
      )

      return r.status === 200
    },
  }
}

// ---------------------------------------------------------------------------
// Accounts
// ---------------------------------------------------------------------------

export interface AccountClientAuth {
  /** The ID of the currently authenticated account. */
  accountId: string
}

export function createAccountClient(auth: AccountClientAuth) {
  /**
   * Resolve "me" or a UUID to a concrete account id.
   * This lets the public API accept `"me"` as a shorthand while still using
   * `/{id}/…` endpoints under the hood.
   */
  function resolveId(idOrMe: string): string {
    if (idOrMe === 'me') return auth.accountId
    return idOrMe
  }

  return {
    // ---- Profile ----------------------------------------------------------

    /** Fetch the current account's own profile. */
    async me(): Promise<Account> {
      return request<Account>(`${BASE}/accounts/${auth.accountId}`)
    },

    /** Fetch any account by id. Pass `"me"` for the current account. */
    async get(idOrMe: string): Promise<Account> {
      return request<Account>(`${BASE}/accounts/${resolveId(idOrMe)}`)
    },

    /** Create a new account (requires auth). Returns 201 on creation, 200 if already exists. */
    async create(payload: CreateAccountPayload): Promise<Account> {
      return request<Account>(`${BASE}/accounts`, jsonBody(payload))
    },

    /** Delete an account. Pass "me" for the current account. */
    async delete(idOrMe: string): Promise<void> {
      await request<void>(`${BASE}/accounts/${resolveId(idOrMe)}`, { method: 'DELETE' })
    },

    /** Update the profile for an account. Pass `"me"` for the current account. */
    async updateProfile(idOrMe: string, payload: UpdateProfilePayload): Promise<Account> {
      return request<Account>(
        `${BASE}/accounts/${resolveId(idOrMe)}/profile`,
        jsonBody(payload),
      )
    },

    /** Start an email change flow. Sends a verification email to the new address. */
    async startEmailChange(idOrMe: string, payload: StartEmailChangePayload): Promise<void> {
      await request<void>(
        `${BASE}/accounts/${resolveId(idOrMe)}/email-change`,
        jsonBody(payload),
      )
    },

    /** Check if there's a pending email change for an account. */
    async getEmailChangeStatus(idOrMe: string): Promise<EmailChangeStatus> {
      return request<EmailChangeStatus>(
        `${BASE}/accounts/${resolveId(idOrMe)}/email-change/status`,
      )
    },

    // ---- Password ---------------------------------------------------------

    /** Set or update the password for an account. Pass `"me"` for the current account. */
    async setPassword(idOrMe: string, password: string): Promise<void> {
      await request<void>(
        `${BASE}/accounts/${resolveId(idOrMe)}/password`,
        jsonBody({ password }),
      )
    },

    // ---- TOTP -------------------------------------------------------------

    /** Get TOTP configuration status for an account. Pass `"me"` for the current account. */
    async getTotp(idOrMe: string): Promise<TotpStatus> {
      return request<TotpStatus>(`${BASE}/accounts/${resolveId(idOrMe)}/totp`)
    },

    /** Enable TOTP for an account. Pass `"me"` for the current account. */
    async enableTotp(idOrMe: string): Promise<TotpStatus> {
      return request<TotpStatus>(
        `${BASE}/accounts/${resolveId(idOrMe)}/totp/enable`,
        { method: 'POST' },
      )
    },

    /** Disable TOTP for an account. Pass `"me"` for the current account. */
    async disableTotp(idOrMe: string): Promise<TotpStatus> {
      return request<TotpStatus>(
        `${BASE}/accounts/${resolveId(idOrMe)}/totp/disable`,
        { method: 'POST' },
      )
    },

    /** Confirm a TOTP setup using a 6-digit code. Pass `"me"` for the current account. */
    async confirmTotp(idOrMe: string, code: number): Promise<void> {
      const body = new URLSearchParams()
      body.append('totp', String(code))
      await request<void>(
        `${BASE}/accounts/${resolveId(idOrMe)}/totp/confirm`,
        { method: 'POST', body },
      )
    },

    // ---- Sessions ---------------------------------------------------------

    /** List all sessions for an account. Pass `"me"` for the current account. */
    async listSessions(idOrMe: string): Promise<Session[]> {
      return request<Session[]>(`${BASE}/accounts/${resolveId(idOrMe)}/sessions`)
    },

    /** Revoke a specific session. Pass `"me"` for the current account. */
    async revokeSession(idOrMe: string, sessionId: string): Promise<void> {
      await request<void>(
        `${BASE}/accounts/${resolveId(idOrMe)}/sessions/${sessionId}/revoke`,
        { method: 'POST' },
      )
    },

    /** Revoke all other sessions, keeping the current one. Pass `"me"` for the current account. */
    async revokeAllSessions(idOrMe: string): Promise<void> {
      await request<void>(
        `${BASE}/accounts/${resolveId(idOrMe)}/sessions/revoke-all`,
        { method: 'POST' },
      )
    },

    // ---- Roles ------------------------------------------------------------

    /** List roles assigned to an account. Pass `"me"` for the current account. */
    async listRoles(idOrMe: string): Promise<Role[]> {
      return request<Role[]>(`${BASE}/accounts/${resolveId(idOrMe)}/roles`)
    },

    // ---- Memberships ------------------------------------------------------

    /** List tenant memberships for an account. Pass `"me"` for the current account. */
    async listMemberships(idOrMe: string): Promise<Membership[]> {
      return request<Membership[]>(`${BASE}/accounts/${resolveId(idOrMe)}/memberships`)
    },

    // ---- SSO ---------------------------------------------------------------

    /** List SSO-eligible clients for the current account. */
    async listSsoClients(): Promise<SsoClientInfo[]> {
      return request<SsoClientInfo[]>(`${BASE}/accounts/me/sso-clients`)
    },

    /** Get saved SSO preferences (favorites + order) for the current account. */
    async getSsoPreferences(): Promise<SsoPreferences> {
      return request<SsoPreferences>(`${BASE}/accounts/me/sso-preferences`)
    },

    /** Save SSO preferences for the current account. */
    async saveSsoPreferences(payload: SsoPreferences): Promise<void> {
      await request<void>(`${BASE}/accounts/me/sso-preferences`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      })
    },
  }
}

// ---------------------------------------------------------------------------
// OAuth2
// ---------------------------------------------------------------------------

export function createOAuth2Client() {
  return {
    /**
     * Exchange an authorization code for tokens.
     * Auth (session/client) is sent automatically via cookies by the browser.
     */
    async tokenWithCode(
      clientId: string,
      redirectUri: string,
      code: string,
    ): Promise<OAuth2TokenResponse> {
      const params = new URLSearchParams({
        grant_type: 'authorization_code',
        client_id: clientId,
        redirect_uri: redirectUri,
        code,
      })
      return request<OAuth2TokenResponse>(`${BASE}/oauth2/token?${params}`, { method: 'POST' })
    },

    /**
     * Obtain a service token via client credentials (confidential clients only).
     * Auth is sent automatically via cookies by the browser.
     */
    async tokenWithCredentials(scope?: string): Promise<OAuth2TokenResponse> {
      const params = new URLSearchParams({ grant_type: 'client_credentials' })
      if (scope) params.set('scope', scope)
      return request<OAuth2TokenResponse>(`${BASE}/oauth2/token?${params}`, { method: 'POST' })
    },

    /**
     * Refresh an access token using a refresh token (confidential clients only).
     * Auth is sent automatically via cookies by the browser.
     */
    async tokenWithRefresh(refreshToken: string): Promise<OAuth2TokenResponse> {
      const params = new URLSearchParams({
        grant_type: 'refresh_token',
        refresh_token: refreshToken,
      })
      return request<OAuth2TokenResponse>(`${BASE}/oauth2/token?${params}`, { method: 'POST' })
    },
  }
}

// ---------------------------------------------------------------------------
// Client Scopes
// ---------------------------------------------------------------------------

export function createClientScopesClient() {
  return {
    /**
     * Replace all registered scopes for a client.
     * The client must be authenticated as itself (its principal id must match `clientId`).
     */
    async replace(
      clientId: string,
      scopes: ScopeDef[],
    ): Promise<ClientScopesResponse> {
      return request<ClientScopesResponse>(
        `${BASE}/clients/${clientId}/scopes`,
        {
          method: 'PUT',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ scopes }),
        },
      )
    },
  }
}

// ---------------------------------------------------------------------------
// Roles
// ---------------------------------------------------------------------------

export function createRolesClient() {
  return {
    /** List all roles, optionally limited to a tenant's own roles. */
    async list(params?: {
      tenantId?: string
    }): Promise<RoleDefinition[]> {
      const url = params?.tenantId
        ? `${BASE}/tenants/${params.tenantId}/roles`
        : `${BASE}/roles`
      return request<RoleDefinition[]>(url)
    },

    /** Get a single role by id (fetch from the list). */
    async get(roleId: string): Promise<RoleDefinition | undefined> {
      const roles = await request<RoleDefinition[]>(`${BASE}/roles`)
      return roles.find(r => r.id === roleId)
    },

    /** Create a new role. */
    async create(payload: CreateRolePayload): Promise<RoleDefinition> {
      return request<RoleDefinition>(`${BASE}/roles`, jsonBody(payload))
    },

    /** Update a role name. */
    async update(roleId: string, payload: UpdateRolePayload): Promise<RoleDefinition> {
      return request<RoleDefinition>(
        `${BASE}/roles/${roleId}`,
        { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload) },
      )
    },

    /** Delete a role. */
    async delete(roleId: string): Promise<void> {
      await request<void>(`${BASE}/roles/${roleId}`, { method: 'DELETE' })
    },

    /** List assignments for a role. */
    async listAssignments(roleId: string): Promise<RoleAssignment[]> {
      return request<RoleAssignment[]>(`${BASE}/roles/${roleId}/assignments`)
    },

    /** Assign a role to an account or client. */
    async assign(roleId: string, payload: AssignRolePayload): Promise<RoleAssignment> {
      return request<RoleAssignment>(
        `${BASE}/roles/${roleId}/assignments`,
        jsonBody(payload),
      )
    },

    /** Remove a role assignment. */
    async unassign(roleId: string, assignmentId: string): Promise<void> {
      await request<void>(
        `${BASE}/roles/${roleId}/assignments/${assignmentId}`,
        { method: 'DELETE' },
      )
    },

    /** List role assignments for a client. */
    async listForClient(clientId: string): Promise<RoleAssignment[]> {
      return request<RoleAssignment[]>(`${BASE}/clients/${clientId}/roles`)
    },
  }
}

// ---------------------------------------------------------------------------
// Policies
// ---------------------------------------------------------------------------

export function createPoliciesClient() {
  return {
    /** List all policies, optionally limited to a tenant's own policies. */
    async list(params?: {
      tenantId?: string
    }): Promise<PolicyDefinition[]> {
      const url = params?.tenantId
        ? `${BASE}/tenants/${params.tenantId}/policies`
        : `${BASE}/policies`
      return request<PolicyDefinition[]>(url)
    },

    /** Get a single policy. */
    async get(policyId: string): Promise<PolicyDefinition> {
      return request<PolicyDefinition>(`${BASE}/policies/${policyId}`)
    },

    /** Create a new policy. */
    async create(payload: CreatePolicyPayload): Promise<PolicyDefinition> {
      return request<PolicyDefinition>(`${BASE}/policies`, jsonBody(payload))
    },

    /** Update a policy (partial update). */
    async update(policyId: string, payload: UpdatePolicyPayload): Promise<PolicyDefinition> {
      return request<PolicyDefinition>(
        `${BASE}/policies/${policyId}`,
        { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload) },
      )
    },

    /** Delete a policy. */
    async delete(policyId: string): Promise<void> {
      await request<void>(`${BASE}/policies/${policyId}`, { method: 'DELETE' })
    },

    /** List assignments for a policy. */
    async listAssignments(policyId: string): Promise<PolicyAssignment[]> {
      return request<PolicyAssignment[]>(`${BASE}/policies/${policyId}/assignments`)
    },

    /** Assign a policy to an account, client, or role. */
    async assign(policyId: string, payload: AssignPolicyPayload): Promise<PolicyAssignment> {
      return request<PolicyAssignment>(
        `${BASE}/policies/${policyId}/assignments`,
        jsonBody(payload),
      )
    },

    /** Remove a policy assignment. */
    async unassign(policyId: string, assignmentId: string): Promise<void> {
      await request<void>(
        `${BASE}/policies/${policyId}/assignments/${assignmentId}`,
        { method: 'DELETE' },
      )
    },
  }
}

// ---------------------------------------------------------------------------
// Memberships (admin)
// ---------------------------------------------------------------------------

export function createMembershipsClient() {
  return {
    /** List memberships for a tenant. */
    async list(tenantId: string): Promise<Membership[]> {
      return request<Membership[]>(`${BASE}/tenants/${tenantId}/memberships`)
    },

    /** Add a membership to a tenant. */
    async create(tenantId: string, payload: CreateMembershipPayload): Promise<Membership> {
      return request<Membership>(`${BASE}/tenants/${tenantId}/memberships`, jsonBody(payload))
    },

    /** Remove a membership. */
    async delete(membershipId: string): Promise<void> {
      await request<void>(`${BASE}/memberships/${membershipId}`, { method: 'DELETE' })
    },

    async get(membershipId: string): Promise<Membership> {
      return request<Membership>(`${BASE}/memberships/${membershipId}`)
    },

    async updateProfile(membershipId: string, payload: { first_name?: string; last_name?: string; email?: string }): Promise<Membership> {
      return request<Membership>(`${BASE}/memberships/${membershipId}/profile`, jsonBody(payload))
    },
  }
}

// ---------------------------------------------------------------------------
// Admin Accounts
// ---------------------------------------------------------------------------

export interface AccountSummary {
  id: string
  email: string
  first_name: string
  last_name: string
  totp_enabled: boolean
  membership_id: string | null
}

export function createAdminAccountsClient() {
  return {
    /** List accounts within a tenant, or all accounts when no tenant is specified. */
    async list(params: {
      tenantId?: string
    }): Promise<AccountSummary[]> {
      const qs = new URLSearchParams()
      if (params.tenantId) qs.set('tenant_id', params.tenantId)
      return request<AccountSummary[]>(`${BASE}/admin/accounts?${qs}`)
    },

    /** Disable TOTP for an account via a membership. */
    async disableTOTP(accountId: string, membershipId: string): Promise<void> {
      await request<void>(
        `${BASE}/admin/accounts/${accountId}/disable-totp?membership_id=${membershipId}`,
        { method: 'POST' },
      )
    },
  }
}

// ---------------------------------------------------------------------------
// Tenants
// ---------------------------------------------------------------------------

export interface ClientTenantInfo {
  id: string
  name: string
  suspended: boolean
}

export function createTenantsClient() {
  return {
    async list(): Promise<ClientTenantInfo[]> {
      return request<ClientTenantInfo[]>(`${BASE}/tenants`)
    },
    async create(payload: { name: string; suspended: boolean }): Promise<ClientTenantInfo> {
      return request<ClientTenantInfo>(`${BASE}/tenants`, jsonBody(payload))
    },
    async update(id: string, payload: { name: string; suspended: boolean }): Promise<ClientTenantInfo> {
      return request<ClientTenantInfo>(`${BASE}/tenants/${id}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      })
    },
    async delete(id: string): Promise<void> {
      await request<void>(`${BASE}/tenants/${id}`, { method: 'DELETE' })
    },
  }
}

// ---------------------------------------------------------------------------
// Clients admin
// ---------------------------------------------------------------------------

export interface ClientInfo {
  id: string
  fqdn: string
  confidential: boolean
  suspended: boolean
  redirect_uris: string[]
  created_at: number
  name: string | null
  displayName: string
  logoUri: string | null
  initiateLoginUri: string | null
}

export interface ResetSecretResponse {
  secret: string
}

export function createClientsAdminClient() {
  return {
    async list(): Promise<ClientInfo[]> {
      return request<ClientInfo[]>(`${BASE}/admin/clients`)
    },
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    async create(payload: Record<string, any>): Promise<ClientInfo> {
      return request<ClientInfo>(`${BASE}/admin/clients`, jsonBody(payload))
    },
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    async update(id: string, payload: Record<string, any>): Promise<ClientInfo> {
      return request<ClientInfo>(`${BASE}/admin/clients/${id}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      })
    },
    async delete(id: string): Promise<void> {
      await request<void>(`${BASE}/admin/clients/${id}`, { method: 'DELETE' })
    },
    async resetSecret(id: string): Promise<ResetSecretResponse> {
      return request<ResetSecretResponse>(`${BASE}/admin/clients/${id}/reset-secret`, { method: 'POST' })
    },
  }
}

// ---------------------------------------------------------------------------
// Restrictions (admin)
// ---------------------------------------------------------------------------

function createRestrictionsClient() {
  return {
    async list(targetType?: string, targetId?: string): Promise<RestrictionResponse[]> {
      const params = new URLSearchParams()
      if (targetType) params.set('target_type', targetType)
      if (targetId) params.set('target_id', targetId)
      const qs = params.toString()
      return request<RestrictionResponse[]>(`${BASE}/restrictions` + (qs ? `?${qs}` : ''))
    },
    async place(payload: PlaceRestrictionPayload): Promise<RestrictionResponse> {
      return request<RestrictionResponse>(`${BASE}/restrictions`, jsonBody(payload))
    },
    async lift(id: string): Promise<void> {
      return request<void>(`${BASE}/restrictions/${id}`, { method: 'DELETE' })
    },
  }
}

// ---------------------------------------------------------------------------
// Cross-device handoff
// ---------------------------------------------------------------------------

export function createHandoffClient() {
  return {
    /**
     * Create a handoff for the current ID session or attach to the live one.
     * The first instance's client/redirect/state/scope/nonce are authoritative.
     */
    async createOrAttach(payload: CreateHandoffPayload): Promise<HandoffResponse> {
      return request<HandoffResponse>(`${BASE}/handoffs`, jsonBody(payload))
    },

    /** Shared status poll, read by every embed instance. */
    async status(handoffId: string): Promise<HandoffStatusResponse> {
      return request<HandoffStatusResponse>(
        `${BASE}/handoffs/${handoffId}`,
        { cache: 'no-store' },
      )
    },

    /** Issue a fresh QR code for this instance while a scan is awaited. */
    async issueQr(handoffId: string): Promise<HandoffQrResponse> {
      return request<HandoffQrResponse>(
        `${BASE}/handoffs/${handoffId}/qr`,
        { method: 'POST', cache: 'no-store' },
      )
    },

    /** Decline the pending scan. */
    async reject(handoffId: string): Promise<HandoffQrResponse> {
      return request<HandoffQrResponse>(
        `${BASE}/handoffs/${handoffId}/reject`,
        { method: 'POST' },
      )
    },

    /** Approve the pending scan. Idempotent once confirmed. */
    async confirm(handoffId: string): Promise<{ status: string }> {
      return request<{ status: string }>(
        `${BASE}/handoffs/${handoffId}/confirm`,
        { method: 'POST' },
      )
    },

    /** Drop the +1 device and return every instance to a fresh QR. */
    async changeDevices(handoffId: string): Promise<HandoffQrResponse> {
      return request<HandoffQrResponse>(
        `${BASE}/handoffs/${handoffId}/change-devices`,
        { method: 'POST' },
      )
    },

    /** Claim a scanned QR code from the new device. No ID session required. */
    async scan(handoffId: string, token: string): Promise<HandoffScanResponse> {
      return request<HandoffScanResponse>(
        `${BASE}/handoffs/scans`,
        jsonBody({ handoff_id: handoffId, token }),
      )
    },

    /** What the scanning device should show. The token travels in a header, not the URL. */
    async scanStatus(handoffId: string, scanToken: string): Promise<HandoffScanStatusResponse> {
      return request<HandoffScanStatusResponse>(
        `${BASE}/handoffs/${handoffId}/scan`,
        {
          headers: { 'X-Handoff-Scan-Token': scanToken },
          cache: 'no-store',
        },
      )
    },

    /** Exchange a confirmed scan for the new session and its authorization code. */
    async claim(handoffId: string, scanToken: string): Promise<HandoffClaimResponse> {
      return request<HandoffClaimResponse>(
        `${BASE}/handoffs/${handoffId}/claim`,
        jsonBody({ scan_token: scanToken }),
      )
    },
  }
}

// ---------------------------------------------------------------------------
// Convenience — single ID client
// ---------------------------------------------------------------------------

/** Create a complete ID client bound to a specific authenticated account. */
export function createIdClient(auth: AccountClientAuth) {
  return {
    auth: createAuthClient(),
    accounts: createAccountClient(auth),
    oauth2: createOAuth2Client(),
    clientScopes: createClientScopesClient(),
    roles: createRolesClient(),
    policies: createPoliciesClient(),
    memberships: createMembershipsClient(),
    adminAccounts: createAdminAccountsClient(),
    tenants: createTenantsClient(),
    clientsAdmin: createClientsAdminClient(),
    restrictions: createRestrictionsClient(),
    handoffs: createHandoffClient(),
  }
}
