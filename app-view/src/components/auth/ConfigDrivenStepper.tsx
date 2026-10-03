import { Step, StepLabel, Stepper } from "@mui/material";
import { StepView } from "@/auth/types";

const METHOD_LABELS: Record<string, string> = {
  password: "Account",
  email: "Email",
  sms: "SMS",
  fido2: "Passkey",
  "fido-uaf": "Device",
  "attribute-verification": "Verification",
};

const labelFor = (step: StepView): string => {
  // Two attribute-verification steps read the same by method; the account check reads apart.
  if (
    step.method === "attribute-verification" &&
    step.hints?.kind === "conditions"
  ) {
    return "Account check";
  }
  return METHOD_LABELS[step.method] ?? step.method;
};

type Props = {
  steps: StepView[];
  isComplete: boolean;
};

/**
 * Progress stepper generated from `step_definitions` (no hardcoded step list).
 *
 * Completion/active state is derived server-side in {@link useAuthFlow}, so the stepper survives
 * reload and direct page access.
 */
export const ConfigDrivenStepper = ({ steps, isComplete }: Props) => {
  if (steps.length === 0) return null;

  const activeIndex = isComplete
    ? steps.length
    : steps.findIndex((step) => step.current);

  return (
    <Stepper
      activeStep={activeIndex === -1 ? steps.length : activeIndex}
      alternativeLabel
    >
      {steps.map((step) => (
        <Step key={`${step.order}-${step.method}`} completed={step.completed}>
          <StepLabel>{labelFor(step)}</StepLabel>
        </Step>
      ))}
    </Stepper>
  );
};
