"use client";

import {
  Container,
  Link,
  Paper,
  Stack,
  Typography,
  alpha,
  useTheme,
} from "@mui/material";
import { useState } from "react";
import { useRouter } from "next/router";
import { useAuthFlow } from "@/auth/useAuthFlow";
import { ConfigDrivenStepper } from "@/components/auth/ConfigDrivenStepper";
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
  const theme = useTheme();
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

  if (!router.isReady || isLoading) return <Loading />;

  const renderBody = () => {
    if (isError) {
      return <Typography color="error">Failed to load the authentication flow.</Typography>;
    }
    if (status === "failure" || status === "locked") {
      return (
        <Typography color="error">
          {status === "locked"
            ? "Your account is temporarily locked. Please try again later."
            : "Authentication failed. Please start over."}
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
    const showAccountToggle = currentStep.method === "password" && canRegister;
    return (
      <Stack spacing={2}>
        <StepRenderer
          tenantId={tenantId}
          id={id}
          step={currentStep}
          onCompleted={refetch}
          passwordRegister={
            currentStep.method === "password" ? registerMode : undefined
          }
        />
        {showAccountToggle && (
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
    <Container maxWidth="xs">
      <Paper
        elevation={0}
        sx={{
          borderRadius: 4,
          px: 5,
          py: 6,
          mt: 8,
          backgroundColor:
            theme.palette.mode === "light"
              ? "#fcfcfd"
              : alpha(theme.palette.common.white, 0.035),
          border: `1px solid ${alpha(theme.palette.divider, 0.08)}`,
        }}
      >
        <Typography variant="h5" fontWeight={600} gutterBottom>
          {viewData?.client_name ?? "Sign in"}
        </Typography>
        <Stack spacing={4} mt={2}>
          {steps.length > 0 && (
            <ConfigDrivenStepper steps={steps} isComplete={isComplete} />
          )}
          {renderBody()}
        </Stack>
      </Paper>
    </Container>
  );
}
