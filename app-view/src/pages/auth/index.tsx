"use client";

import { Divider, Link, Stack, Typography } from "@mui/material";
import { useState } from "react";
import { useRouter } from "next/router";
import { useAuthFlow } from "@/auth/useAuthFlow";
import { AuthCard } from "@/components/auth/AuthCard";
import { ConfigDrivenStepper } from "@/components/auth/ConfigDrivenStepper";
import { SsoButtons } from "@/components/auth/SsoButtons";
import { StepRenderer } from "@/components/auth/StepRenderer";
import { ConsentStep } from "@/components/auth/steps/ConsentStep";
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
    status,
    isComplete,
    isLoading,
    isError,
    refetch,
  } = useAuthFlow(tenantId, id);

  const [registerMode, setRegisterMode] = useState(false);

  // initial-registration works without any configuration, so account creation is available by
  // default; it is hidden only when the step explicitly disables registration.
  const canRegister = currentStep?.registration_mode !== "disabled";
  const isPasswordStep = currentStep?.method === "password";

  // Federated sign-in belongs on the initial identification step (1st factor).
  const federations = viewData?.available_federations ?? [];
  const showSso =
    !isComplete &&
    currentStep?.requires_user === false &&
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
        <Typography color="error" align="center">
          {"We couldn't load this sign-in. Please return to the app and try again."}
        </Typography>
      );
    }
    if (status === "failure" || status === "locked") {
      return (
        <Typography color="error" align="center">
          {status === "locked"
            ? "Too many attempts. Your account is temporarily locked — please try again later."
            : "We couldn't sign you in. Please return to the app and start over."}
        </Typography>
      );
    }
    if (isComplete) {
      return <ConsentStep tenantId={tenantId} id={id} viewData={viewData} />;
    }
    if (!currentStep) {
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
          step={currentStep}
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
      </Stack>
    );
  };

  return (
    <AuthCard title={title} subtitle={subtitle} logoUri={viewData?.logo_uri}>
      {steps.length > 1 && (
        <ConfigDrivenStepper steps={steps} isComplete={isComplete} />
      )}
      {renderBody()}
    </AuthCard>
  );
}
