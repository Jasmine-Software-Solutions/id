/** An authenticated account returned by the server. */
export interface Account {
  id: string
  email: string
  first_name: string
  last_name: string
  email_verified: boolean
}

/** An active browser session. */
export interface Session {
  id: string
  user_agent: string | null
  ip_address: string | null
  created_at: number // epoch seconds
  expires_at: number // epoch seconds
  is_current: boolean
}

/** A role definition (as returned from the accounts/roles endpoint — role assignment response). */
export interface Role {
  id: string
  name: string
  /** The tenant this role is scoped to, or null for platform scope. */
  tenant_id: string | null
}

/** A tenant membership. */
export interface Membership {
  id: string
  account_id: string
  account_email: string | null
  tenant_id: string
  tenant_name: string | null
}

/** TOTP configuration state. */
export interface TotpStatus {
  enabled: boolean
  confirmed: boolean | null
  qr_code_uri: string | null
}

/** Payload for creating a new account. */
export interface CreateAccountPayload {
  email: string
  first_name: string
  last_name: string
}

/** Payload for updating profile fields. All fields optional — only provided fields are changed. */
export interface UpdateProfilePayload {
  first_name?: string
  last_name?: string
}

/** Payload for starting an email change (requires verification). */
export interface StartEmailChangePayload {
  new_email: string
}

/** Current state of an email change request. */
export interface EmailChangeStatus {
  pending: boolean
  new_email: string | null
}

/** Auth flow start response (server returns snake_case via Gson). */
export interface FlowStartResponse {
  flow_id: string
  step: string
  request: string
}

/** Auth flow next–step response. */
export interface FlowNextResponse {
  done: boolean
  step: string | null
  request: string | null
  error: boolean
}

/** Auth flow instead response. */
export interface FlowInsteadResponse {
  step: string
  request: string
}

/** OAuth2 token endpoint response. */
export interface OAuth2TokenResponse {
  token_type: 'Bearer'
  access_token: string
  expires_in: number
  refresh_token?: string
  refresh_token_expires_in?: number
}

/** A single scope definition. */
export interface ScopeDef {
  id: string
  description: string
}

/** Response from PUT /api/v1/clients/{client_id}/scopes */
export interface ClientScopesResponse {
  client_id: string
  scopes: ScopeDef[]
}

// ---------------------------------------------------------------------------
// Role management (admin)
// ---------------------------------------------------------------------------

/** A role definition (the role entity itself, from /api/v1/roles). */
export interface RoleDefinition {
  id: string
  name: string
  description?: string
  /** The tenant this role is scoped to, or null for platform scope. */
  tenant_id: string | null
}

// Note: RoleDefinition and Role share the same shape — Role is the
// per-account view, RoleDefinition is the admin view. They have the same
// fields because both come from IRole.toResponse() in the controller.

/** A role assignment (flat response from /api/v1/roles/{id}/assignments). */
export interface RoleAssignment {
  id: string
  role_id: string
  role_name: string | null
  account_id: string | null
  account_email: string | null
  client_id: string | null
  client_name: string | null
  expires_at: number | null
}

/** Payload for creating a role. Omit `tenant_id` for platform scope. */
export interface CreateRolePayload {
  name: string
  description?: string
  tenant_id?: string
}

export interface UpdateRolePayload {
  name?: string
  description?: string
}

/** Payload for assigning a role to an account or client. */
export interface AssignRolePayload {
  account_id?: string
  client_id?: string
  expires_at?: number
}

// ---------------------------------------------------------------------------
// Policy management (admin)
// ---------------------------------------------------------------------------

/** A policy definition (the policy entity, from /api/v1/policies). */
export interface PolicyDefinition {
  id: string
  name: string
  description: string | null
  /** The tenant this policy is scoped to, or null for platform scope. */
  tenant_id: string | null
  document: unknown[]            // JsonArray of statements
}

/** A policy assignment (flat response from /api/v1/policies/{id}/assignments). */
export interface PolicyAssignment {
  id: string
  policy_id: string
  policy_name: string | null
  account_id: string | null
  account_email: string | null
  client_id: string | null
  client_name: string | null
  role_id: string | null
  role_name: string | null
  expires_at: number | null
}

/** Payload for creating a policy. Omit `tenant_id` for platform scope. */
export interface CreatePolicyPayload {
  name: string
  description?: string
  tenant_id?: string
  document: unknown[]
}

/** Payload for updating a policy (all fields optional). */
export interface UpdatePolicyPayload {
  name?: string
  description?: string
  document?: unknown[]
}

/** Payload for assigning a policy to an account, client, or role. */
export interface AssignPolicyPayload {
  account_id?: string
  client_id?: string
  role_id?: string
  expires_at?: number
}

/** Account-level policy assignment response (from /api/v1/accounts/{id}/policies). */
export interface AccountPolicyAssignment {
  id: string
  policy_id: string
  policy_name: string
  expires_at: number | null
}

// ---------------------------------------------------------------------------
// Membership management (admin)
// ---------------------------------------------------------------------------

/** Payload for creating a membership. */
export interface CreateMembershipPayload {
  account_id: string
}

/** A simple list item for tenants in selects. */
export interface TenantInfo {
  id: string
  name: string
  suspended?: boolean
}

/** Generic ID API error. */
export class IdApiError extends Error {
  constructor(
    public status: number,
    message: string,
  ) {
    super(message)
    this.name = 'IdApiError'
  }
}

/** Thrown when the server returns an X-ID-Restricted header. */
export class RestrictedError extends Error {
  constructor() {
    super('Your account has been temporarily restricted.')
    this.name = 'RestrictedError'
  }
}

// ---------------------------------------------------------------------------
// Restrictions (admin)
// ---------------------------------------------------------------------------

export interface RestrictionResponse {
  id: string
  type: 'account_lock' | 'ip_block'
  target_type: 'account' | 'ip'
  target_id: string
  account_email: string | null
  event_id: string | null
  duration_minutes: number | null
  created_at: number
  expires_at: number | null
}

export interface PlaceRestrictionPayload {
  type: 'account_lock' | 'ip_block'
  target_type: 'account' | 'ip'
  target_id: string
  event_id?: string
  duration_minutes?: number
}

// ---------------------------------------------------------------------------
// SSO
// ---------------------------------------------------------------------------

/** A client eligible for single sign-on, returned from /api/v1/accounts/me/sso-clients */
export interface SsoClientInfo {
  client_id: string
  fqdn: string
  name: string | null
  logo_uri: string | null
  initiate_login_uri: string
}

/** SSO preferences payload for GET/PUT /api/v1/accounts/me/sso-preferences */
export interface SsoPreferences {
  favorites: string[]
  order: string[]
}
