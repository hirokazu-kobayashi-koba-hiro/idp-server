"use client";

import { Divider, Link, Stack, Typography } from "@mui/material";
import { useState } from "react";
import { useRouter } from "next/router";
import { useAuthFlow } from "@/auth/useAuthFlow";
import { StepView } from "@/auth/types";
import { AuthCard } from "@/components/auth/AuthCard";
import { ConfigDrivenStepper } from "@/components/auth/ConfigDrivenStepper";
import { MethodPicker } from "@/components/auth/MethodPicker";
import { SsoButtons } from "@/components/auth/SsoButtons";
import { StepRenderer } from "@/components/auth/StepRenderer";
import { ConsentStep } from "@/components/auth/steps/ConsentStep";
import { AuthAlert } from "@/components/auth/AuthAlert";
import { Loading } from "@/components/Loading";

const asString = (value: string | string[] | undefined): string =>
  Array.isArray(value) ? (value[0] ?? "") : (value ?? "");

/**
 * Config-driven authentication screen (Issue #1373).
 *
 * A single URL (`/auth?id=..&tenant_id=..`) renders the whole flow. The step order comes from
 * `authentication_policy.step_definitions` (view-data) and the current step is derived from the
 * server `authentication-status`, so reload and direct access reconstruct state instead of breaking.
 */
export default function AuthPage() {
  const router = useRouter();
  const tenantId = asString(router.query.tenant_id);
  const id = asString(router.query.id);

  const {
    viewData,
    steps,
    currentStep,
    availableMethods,
    status,
    isComplete,
    isLoading,
    isError,
    refetch,
  } = useAuthFlow(tenantId, id);

  const [registerMode, setRegisterMode] = useState(false);
  const [selectedMethod, setSelectedMethod] = useState<string | null>(null);

  // When a policy offers several methods with no fixed order, the user picks one and we render it
  // as a single step.
  const showPicker =
    !isComplete &&
    steps.length === 0 &&
    availableMethods.length >= 2 &&
    !selectedMethod;
  const pickedStep: StepView | null = selectedMethod
    ? {
        method: selectedMethod,
        order: 1,
        requires_user: false,
        allow_registration: false,
        completed: false,
        current: true,
      }
    : null;
  const effectiveStep = currentStep ?? pickedStep;

  // initial-registration works without any configuration, so account creation is available by
  // default; it is hidden only when the step explicitly disables registration.
  const canRegister = effectiveStep?.registration_mode !== "disabled";
  const isPasswordStep = effectiveStep?.method === "password";
  const canPickAnother = availableMethods.length >= 2 && selectedMethod !== null;

  // Federated sign-in belongs on the initial identification step (1st factor).
  const federations = viewData?.available_federations ?? [];
  const showSso =
    !isComplete &&
    !showPicker &&
    effectiveStep?.requires_user === false &&
    federations.length > 0;

  if (!router.isReady || isLoading) return <Loading />;

  const title = isComplete
    ? "You're all set"
    : registerMode && isPasswordStep
      ? "Create your account"
      : "Sign in";
  const subtitle =
    !isComplete && viewData?.client_name
      ? `Continue to ${viewData.client_name}`
      : undefined;

  const renderBody = () => {
    if (isError) {
      return (
        <AuthAlert message="We couldn't load this sign-in. Please return to the app and try again." />
      );
    }
    if (status === "failure" || status === "locked") {
      return (
        <AuthAlert
          message={
            status === "locked"
              ? "Too many attempts. Your account is temporarily locked — please try again later."
              : "We couldn't sign you in. Please return to the app and start over."
          }
        />
      );
    }
    if (isComplete) {
      return <ConsentStep tenantId={tenantId} id={id} viewData={viewData} />;
    }
    if (showPicker) {
      return (
        <MethodPicker methods={availableMethods} onSelect={setSelectedMethod} />
      );
    }
    if (!effectiveStep) {
      // All known steps are done but the server has not reported success yet (transient between an
      // interaction and the status refetch).
      return <Loading />;
    }
    return (
      <Stack spacing={2.5}>
        {showSso && (
          <>
            <SsoButtons tenantId={tenantId} id={id} federations={federations} />
            <Divider>
              <Typography variant="caption" color="text.secondary">
                or
              </Typography>
            </Divider>
          </>
        )}
        <StepRenderer
          tenantId={tenantId}
          id={id}
          step={effectiveStep}
          onCompleted={refetch}
          passwordRegister={isPasswordStep ? registerMode : undefined}
        />
        {isPasswordStep && canRegister && (
          <Link
            component="button"
            type="button"
            variant="body2"
            underline="hover"
            onClick={() => setRegisterMode((prev) => !prev)}
            sx={{ alignSelf: "center" }}
          >
            {registerMode
              ? "Already have an account? Sign in"
              : "Don't have an account? Sign up"}
          </Link>
        )}
        {canPickAnother && (
          <Link
            component="button"
            type="button"
            variant="body2"
            underline="hover"
            onClick={() => setSelectedMethod(null)}
            sx={{ alignSelf: "center" }}
          >
            Use a different method
          </Link>
        )}
      </Stack>
    );
  };

  return (
    <AuthCard
      title={title}
      subtitle={subtitle}
      logoUri={viewData?.logo_uri}
      tosUri={viewData?.tos_uri}
      policyUri={viewData?.policy_uri}
    >
      {steps.length > 1 && (
        <ConfigDrivenStepper steps={steps} isComplete={isComplete} />
      )}
      {renderBody()}
    </AuthCard>
  );
}
