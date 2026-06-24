"use client";

import { ComponentType } from "react";
import { Typography } from "@mui/material";
import { StepView } from "@/auth/types";
import { StepProps } from "./steps/StepProps";
import { RegisterStep } from "./steps/RegisterStep";
import { PasswordAuthStep } from "./steps/PasswordAuthStep";
import { EmailVerifyStep } from "./steps/EmailVerifyStep";
import { SmsStep } from "./steps/SmsStep";
import { Fido2Step } from "./steps/Fido2Step";
import { Fido2AuthStep } from "./steps/Fido2AuthStep";
import { FidoUafStep } from "./steps/FidoUafStep";

/**
 * Whether a step verifies an existing credential rather than registering a new one.
 *
 * `allow_registration: false` (or `registration_mode: "disabled"`) marks a pure 2nd-factor
 * verification — e.g. the financial-grade `fido2` step that uses `fido2-authentication`. Otherwise
 * the step may register a new credential.
 */
const isAuthenticateOnly = (step: StepView): boolean =>
  step.allow_registration === false || step.registration_mode === "disabled";

/**
 * Resolves a step to its component from `method` plus the register/authenticate distinction.
 *
 * Adding a new method (or a new register/authenticate variant) means editing only this resolver —
 * no new page or route. Steps with no mapping fall back to a notice.
 */
const resolveStepComponent = (
  step: StepView,
  passwordRegister?: boolean,
): ComponentType<StepProps> | undefined => {
  switch (step.method) {
    case "password":
      // The page may override login vs. registration via the account toggle; otherwise fall back
      // to the step's own register/authenticate distinction.
      if (passwordRegister !== undefined) {
        return passwordRegister ? RegisterStep : PasswordAuthStep;
      }
      return isAuthenticateOnly(step) ? PasswordAuthStep : RegisterStep;
    case "email":
      return EmailVerifyStep;
    case "sms":
      return SmsStep;
    case "fido2":
      return isAuthenticateOnly(step) ? Fido2AuthStep : Fido2Step;
    case "fido-uaf":
      return FidoUafStep;
    default:
      return undefined;
  }
};

type StepRendererProps = StepProps & {
  /** Overrides the password step between registration (true) and login (false). */
  passwordRegister?: boolean;
};

export const StepRenderer = ({ passwordRegister, ...props }: StepRendererProps) => {
  const Component = resolveStepComponent(props.step, passwordRegister);
  if (!Component) {
    return (
      <Typography color="text.secondary" variant="body2">
        Unsupported authentication step: {props.step.method}
      </Typography>
    );
  }
  return <Component {...props} />;
};
