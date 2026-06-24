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

export type ViewData = {
  client_name?: string;
  logo_uri?: string;
  scopes?: string[];
  authentication_policy?: AuthenticationPolicy;
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
