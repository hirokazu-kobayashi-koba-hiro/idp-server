import { Step, StepLabel, Stepper } from "@mui/material";

const steps = ["password", "Passkey", "Confirm"];

export const SignupStepper = ({ activeStep }: { activeStep: number }) => {
  return (
    <Stepper activeStep={activeStep} alternativeLabel>
      {steps.map((label) => (
        <Step key={label}>
          <StepLabel>{label}</StepLabel>
        </Step>
      ))}
    </Stepper>
  );
};
