import { Step, StepLabel, Stepper } from "@mui/material";

const steps = ["Enter Email", "Verify Email", "Register Passkey"];

interface AddPasskeyStepperProps {
  activeStep: number;
}

export function AddPasskeyStepper({ activeStep }: AddPasskeyStepperProps) {
  return (
    <Stepper activeStep={activeStep} alternativeLabel sx={{ mb: 2 }}>
      {steps.map((label) => (
        <Step key={label}>
          <StepLabel>{label}</StepLabel>
        </Step>
      ))}
    </Stepper>
  );
}
