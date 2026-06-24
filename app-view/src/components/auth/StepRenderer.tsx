"use client";

import { ComponentType } from "react";
import { Typography } from "@mui/material";
import { useAtom } from "jotai";
import { StepView } from "@/auth/types";
import { shouldFido2Authenticate } from "@/auth/stepHelpers";
import { authUserStatusAtom } from "@/state/AuthState";
import { StepProps } from "./steps/StepProps";
import { RegisterStep } from "./steps/RegisterStep";
import { PasswordAuthStep } from "./steps/PasswordAuthStep";
import { EmailVerifyStep } from "./steps/EmailVerifyStep";
import { SmsStep } from "./steps/SmsStep";
import { Fido2Step } from "./steps/Fido2Step";
import { Fido2AuthStep } from "./steps/Fido2AuthStep";
import { FidoUafStep } from "./steps/FidoUafStep";

/**
 * Whether a step verifies an existing credential rather than registering a new one, based on the
 * step's flags alone (used for the password method, where the page also offers an explicit toggle).
 */
const isAuthenticateOnly = (step: StepView): boolean =>
  step.allow_registration === false || step.registration_mode === "disabled";

/**
 * Resolves a step to its component from `method`, the register/authenticate distinction, and the
 * current `user.status`.
 *
 * Adding a new method (or a new register/authenticate variant) means editing only this resolver —
 * no new page or route. Steps with no mapping fall back to a notice.
 */
const resolveStepComponent = (
  step: StepView,
  userStatus: string,
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
      // Established user → authenticate an existing passkey; new / initial user → register one
      // (unless the policy forces a mode). See shouldFido2Authenticate.
      return shouldFido2Authenticate(step, userStatus) ? Fido2AuthStep : Fido2Step;
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

export const StepRenderer = ({
  passwordRegister,
  ...props
}: StepRendererProps) => {
  const [userStatus] = useAtom(authUserStatusAtom);
  const Component = resolveStepComponent(props.step, userStatus, passwordRegister);
  if (!Component) {
    return (
      <Typography color="text.secondary" variant="body2">
        Unsupported authentication step: {props.step.method}
      </Typography>
    );
  }
  return <Component {...props} />;
};
