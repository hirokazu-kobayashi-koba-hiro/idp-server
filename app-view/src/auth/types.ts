/**
 * Types for the config-driven authentication flow (Issue #1373).
 *
 * The flow is driven entirely by data the backend already returns:
 * - `view-data` → `authentication_policy.step_definitions` (the ordered step config)
 * - `authentication-status` → `interaction_results` / `status` (reload-safe progress)
 *
 * No backend changes are required; this is the frontend-only contract.
 */

export type RegistrationMode = "allowed" | "required" | "disabled";

/** One step in `authentication_policy.step_definitions` (mirrors AuthenticationStepDefinition). */
export type StepDefinition = {
  method: string; // "password" | "email" | "fido2" | "fido-uaf" | "sms"
  order: number;
  requires_user?: boolean; // false = 1st factor (identify), true = 2nd factor (verify)
  allow_registration?: boolean;
  registration_mode?: RegistrationMode;
  user_identity_source?: string; // "email" | "phone_number" | "username"
  verification_source?: string;
};

export type AuthenticationPolicy = {
  step_definitions?: StepDefinition[];
  available_methods?: string[];
  [key: string]: unknown;
};

/** One entry of view-data `available_federations` (mirrors AvailableFederation.toMap). */
export type Federation = {
  id?: string;
  type: string; // e.g. "oidc"
  sso_provider: string; // e.g. "google"
  auto_selected?: boolean;
};

/**
 * Requested claims surfaced for claim-level consent (view-data `claims`, OIDC4IDA §5.7.3). Each
 * entry is a list of claim names; denying a name removes it from all three at grant build time.
 */
export type RequestedClaims = {
  id_token?: string[];
  userinfo?: string[];
  verified_claims?: string[];
};

/**
 * One element of an array-valued custom property, as stored: a plain value or an object with its
 * own fields (an account with a branch, a card with a brand).
 */
export type ClaimValue = string | number | boolean | Record<string, unknown>;

export type ViewData = {
  client_name?: string;
  logo_uri?: string;
  scopes?: string[];
  claims?: RequestedClaims;
  /**
   * Candidate values for claims released by a `claims:*` scope, keyed by claim name (backend
   * #1816).
   *
   * Present only once the transaction has an authenticated user, and only for arrays — a scalar
   * has nothing to select between. The consent screen offers the elements individually and sends
   * the kept ones back as `granted_claim_values`; the server matches whole elements, so an element
   * has to be echoed exactly as it was received here.
   */
  claim_values?: Record<string, ClaimValue[]>;
  authentication_policy?: AuthenticationPolicy;
  /**
   * True when the existing OP session can complete this authorization without re-authenticating.
   *
   * The server decides this (`OAuthViewDataCreator.isSessionEnabled`): it is false when there is no
   * active session, when `prompt=login` forces re-authentication, when `max_age` has elapsed, or
   * when `acr_values` does not match the session. The screen must not second-guess it.
   */
  session_enabled?: boolean;
  available_federations?: Federation[];
  custom_params?: Record<string, string>;
  tos_uri?: string;
  policy_uri?: string;
  [key: string]: unknown;
};

/** One entry of `authentication-status.interaction_results` (mirrors AuthenticationInteractionResult.toMap). */
export type InteractionResult = {
  operation_type: string; // "challenge" | "authentication" | "registration" | "deny" | ...
  method: string;
  call_count: number;
  success_count: number;
  failure_count: number;
  interaction_time?: string;
};

/** A step augmented with progress derived from the server status. */
export type StepView = StepDefinition & {
  completed: boolean;
  current: boolean;
};

export type FlowStatus = "in_progress" | "success" | "failure" | "locked";

export type AuthStatus = {
  status: FlowStatus | string;
  interaction_results: Record<string, InteractionResult>;
  authentication_methods: string[];
};
