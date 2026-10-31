/**
 * Event discriminators. These strings are the Kotlin data-class simple names
 * (`Event.eventType`) and are the contract with every publisher.
 */

export const EventTypes = {
  // Account
  AccountCreated: "AccountCreatedEvent",
  AccountDeleted: "AccountDeletedEvent",
  AccountPasswordSet: "AccountPasswordSetEvent",
  AccountTOTPEnabled: "AccountTOTPEnabledEvent",
  AccountTOTPDisabled: "AccountTOTPDisabledEvent",
  AccountEmailChangeStarted: "AccountEmailChangeStartedEvent",
  AccountEmailChangeVerified: "AccountEmailChangeVerifiedEvent",

  // Authentication
  AuthenticationFlowStarted: "AuthenticationFlowStartedEvent",
  AuthenticationSucceeded: "AuthenticationSucceededEvent",
  InvalidPasswordAtAuthentication: "InvalidPasswordAtAuthenticationEvent",
  ValidPasswordAtAuthentication: "ValidPasswordAtAuthenticationEvent",
  InvalidTOTPAtAuthentication: "InvalidTOTPAtAuthenticationEvent",
  MagicLinkRequested: "MagicLinkRequestedEvent",

  // Sessions
  SessionCreated: "SessionCreatedEvent",
  SessionRevoked: "SessionRevokedEvent",
  AllSessionsRevoked: "AllSessionsRevokedEvent",

  // Clients / roles / policies
  ClientCreated: "ClientCreatedEvent",
  RoleCreated: "RoleCreatedEvent",
  RoleUpdated: "RoleUpdatedEvent",
  RoleDeleted: "RoleDeletedEvent",
  RoleAssigned: "RoleAssignedEvent",
  RoleUnassigned: "RoleUnassignedEvent",
  PolicyCreated: "PolicyCreatedEvent",
  PolicyUpdated: "PolicyUpdatedEvent",
  PolicyDeleted: "PolicyDeletedEvent",
  PolicyAssigned: "PolicyAssignedEvent",
  PolicyUnassigned: "PolicyUnassignedEvent",

  // Invitations
  InvitationCreated: "InvitationCreatedEvent",

  // Security (worker-emitted)
  PasswordBruteForceAttempted: "PasswordBruteForceAttemptedSecurityEvent",
  ImpossibleTravelDetected: "ImpossibleTravelDetectedSecurityEvent",
  ConcurrentSessionLimitExceeded: "ConcurrentSessionLimitExceededSecurityEvent",
  RapidAccountCreationDetected: "RapidAccountCreationDetectedSecurityEvent",
  EnumerationDetected: "EnumerationDetectedSecurityEvent",
  TOTPDisabledFromUnusualLocation: "TOTPDisabledFromUnusualLocationSecurityEvent",
  CredentialStuffingSucceeded: "CredentialStuffingSucceededSecurityEvent",
  RestrictionPlaced: "RestrictionPlacedEvent",
} as const;

export const SECURITY_EVENT_TYPES: ReadonlySet<string> = new Set([
  EventTypes.ValidPasswordAtAuthentication,
  EventTypes.InvalidPasswordAtAuthentication,
  EventTypes.InvalidTOTPAtAuthentication,
  EventTypes.AuthenticationSucceeded,
  EventTypes.AuthenticationFlowStarted,
  EventTypes.SessionCreated,
  EventTypes.SessionRevoked,
  EventTypes.AllSessionsRevoked,
  EventTypes.AccountCreated,
  EventTypes.AccountDeleted,
  EventTypes.AccountPasswordSet,
  EventTypes.AccountTOTPEnabled,
  EventTypes.AccountTOTPDisabled,
  EventTypes.AccountEmailChangeVerified,
]);

export const EMAIL_EVENT_TYPES: ReadonlySet<string> = new Set([
  EventTypes.MagicLinkRequested,
  EventTypes.AccountEmailChangeStarted,
  EventTypes.InvitationCreated,
  EventTypes.CredentialStuffingSucceeded,
]);

export const WITCHWOOD_EVENT_TYPES: ReadonlySet<string> = new Set([
  EventTypes.AccountCreated,
  EventTypes.ClientCreated,
  EventTypes.RoleCreated,
  EventTypes.RoleUpdated,
  EventTypes.RoleDeleted,
  EventTypes.RoleAssigned,
  EventTypes.RoleUnassigned,
  EventTypes.PolicyCreated,
  EventTypes.PolicyUpdated,
  EventTypes.PolicyDeleted,
  EventTypes.PolicyAssigned,
  EventTypes.PolicyUnassigned,
  EventTypes.AuthenticationSucceeded,
]);
