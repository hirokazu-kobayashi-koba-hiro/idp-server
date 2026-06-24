import { useQuery } from "@tanstack/react-query";
import { backendUrl } from "@/pages/_app";
import {
  AuthStatus,
  FlowStatus,
  StepDefinition,
  StepView,
  ViewData,
} from "./types";

/**
 * Operation types that mark a step's method as "done".
 *
 * A `challenge` also records a success_count (the code was sent), so it must be excluded — the step
 * is only complete once the matching `authentication` / `registration` succeeds. Using
 * `interaction_results` (not `authentication_methods`) is required because the latter excludes
 * registration operations, which the signup flow relies on.
 */
const TERMINAL_OPERATIONS = new Set(["authentication", "registration"]);

/** Interaction method that bootstraps account creation for a 1st-factor identify step. */
const INITIAL_REGISTRATION = "initial-registration";

/** Methods the UI can render; others in available_methods are ignored for step derivation. */
const KNOWN_METHODS = new Set(["password", "email", "sms", "fido2", "fido-uaf"]);

const singleStep = (method: string): StepDefinition => ({
  method,
  order: 1,
  requires_user: false,
  allow_registration: false,
});

/**
 * Fallback flow used when a policy defines neither `step_definitions` nor usable
 * `available_methods`: a single password authentication step.
 */
const DEFAULT_STEPS: StepDefinition[] = [singleStep("password")];

const computeCompletedMethods = (status?: AuthStatus): Set<string> => {
  const completed = new Set<string>();
  const results = status?.interaction_results;
  if (!results) return completed;
  for (const result of Object.values(results)) {
    if (
      result.success_count > 0 &&
      TERMINAL_OPERATIONS.has((result.operation_type ?? "").toLowerCase())
    ) {
      completed.add(result.method);
    }
  }
  return completed;
};

/**
 * Interaction methods that satisfy a step.
 *
 * The step's own method always counts. Account creation (`initial-registration`, recorded method
 * "initial-registration") only satisfies a password-based 1st-factor step, where creating the
 * account with a password *is* the factor. For email / sms 1st-factor steps the factor is a
 * separate verification (email-authentication / sms-authentication), so the bridge must NOT apply
 * there — otherwise the step would complete prematurely on account creation, before verification.
 */
const acceptingMethods = (step: StepDefinition): string[] => {
  const methods = [step.method];
  if (step.method === "password" && step.requires_user === false) {
    methods.push(INITIAL_REGISTRATION);
  }
  return methods;
};

const fetchJson = async <T>(url: string, label: string): Promise<T> => {
  const response = await fetch(url, { credentials: "include" });
  if (!response.ok) throw new Error(`${label} ${response.status}`);
  return response.json();
};

/**
 * Drives the config-driven authentication flow from server data only.
 *
 * @returns the ordered steps (with completion derived from the server), the current step
 *     (lowest-order incomplete), the terminal flow status, and a `refetch` to advance after each
 *     interaction.
 */
export const useAuthFlow = (tenantId?: string, id?: string) => {
  const enabled = Boolean(tenantId && id);

  const viewDataQuery = useQuery({
    queryKey: ["auth", "view-data", tenantId, id],
    enabled,
    queryFn: () =>
      fetchJson<ViewData>(
        `${backendUrl}/${tenantId}/v1/authorizations/${id}/view-data`,
        "view-data",
      ),
  });

  const statusQuery = useQuery({
    queryKey: ["auth", "status", tenantId, id],
    enabled,
    queryFn: () =>
      fetchJson<AuthStatus>(
        `${backendUrl}/${tenantId}/v1/authorizations/${id}/authentication-status`,
        "authentication-status",
      ),
  });

  const viewData = viewDataQuery.data;
  const status = statusQuery.data;

  const configured: StepDefinition[] = [
    ...(viewData?.authentication_policy?.step_definitions ?? []),
  ].sort((a, b) => a.order - b.order);

  const availableMethods = (
    viewData?.authentication_policy?.available_methods ?? []
  ).filter((method) => method !== "initial-registration" && KNOWN_METHODS.has(method));

  // Step source: explicit step_definitions win. Otherwise derive from available_methods — exactly
  // one method becomes a single step, several methods are offered via a picker (steps stay empty),
  // and none falls back to password.
  const definitions =
    configured.length > 0
      ? configured
      : availableMethods.length === 1
        ? [singleStep(availableMethods[0])]
        : availableMethods.length === 0
          ? DEFAULT_STEPS
          : [];

  const completedMethods = computeCompletedMethods(status);
  const flowStatus: FlowStatus | string = status?.status ?? "in_progress";
  const isComplete = flowStatus === "success";

  const firstIncompleteOrder = definitions.find(
    (step) => !acceptingMethods(step).some((m) => completedMethods.has(m)),
  )?.order;

  const steps: StepView[] = definitions.map((step) => {
    const completed =
      isComplete ||
      acceptingMethods(step).some((m) => completedMethods.has(m));
    return {
      ...step,
      completed,
      current: !isComplete && step.order === firstIncompleteOrder,
    };
  });

  const currentStep = steps.find((step) => step.current) ?? null;

  return {
    viewData,
    steps,
    currentStep,
    availableMethods,
    status: flowStatus,
    isComplete,
    isLoading: enabled && (viewDataQuery.isPending || statusQuery.isPending),
    isError: viewDataQuery.isError || statusQuery.isError,
    refetch: async () => {
      await Promise.all([viewDataQuery.refetch(), statusQuery.refetch()]);
    },
  };
};
