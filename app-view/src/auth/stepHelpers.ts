import { StepView } from "./types";

type StepFlags = Pick<
  StepView,
  "requires_user" | "allow_registration" | "registration_mode"
>;

/**
 * Statuses for a new / still-setting-up user: they register a credential and enter contact details.
 * Anything else (REGISTERED, IDENTITY_VERIFIED, ...) is treated as an established user who
 * authenticates with what is already on file.
 */
const INITIAL_STATUSES = new Set(["INITIALIZED", "FEDERATED"]);

export const isInitialUser = (userStatus: string): boolean =>
  INITIAL_STATUSES.has(userStatus);

/**
 * Whether the step must collect a fresh contact value (phone / email) from the user instead of
 * relying on one already on file.
 *
 * - 1st-factor step (`requires_user: false`): the user identifies themselves, so they always enter
 *   the value.
 * - 2nd-factor step: the value is normally resolved server-side from the identified user — except a
 *   new / initial user has nothing on file and may register one, when the step allows it
 *   (`allow_registration: true`).
 */
export const needsContactInput = (
  step: StepFlags,
  userStatus: string,
): boolean =>
  step.requires_user === false ||
  (step.allow_registration === true && isInitialUser(userStatus));

/**
 * Whether a fido2 step should authenticate an existing passkey rather than register a new one.
 *
 * The policy may force the mode (`registration_mode: "required"` / `"disabled"`,
 * `allow_registration: false`); otherwise a new / initial user registers and everyone else
 * authenticates, based on the latest `user.status`.
 */
export const shouldFido2Authenticate = (
  step: StepFlags,
  userStatus: string,
): boolean => {
  if (step.registration_mode === "required") return false;
  if (step.registration_mode === "disabled") return true;
  if (step.allow_registration === false) return true;
  return !isInitialUser(userStatus);
};
