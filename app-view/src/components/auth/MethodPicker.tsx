"use client";

import { Button, Stack, Typography } from "@mui/material";

const METHOD_LABELS: Record<string, string> = {
  password: "Password",
  email: "Email code",
  sms: "SMS code",
  fido2: "Passkey",
  "fido-uaf": "Registered device",
};

const labelFor = (method: string): string => METHOD_LABELS[method] ?? method;

type Props = {
  methods: string[];
  onSelect: (method: string) => void;
};

/**
 * Lets the user choose how to verify when a policy offers several methods but no fixed step order
 * (e.g. an ACR step-up allowing passkey or registered device).
 */
export const MethodPicker = ({ methods, onSelect }: Props) => (
  <Stack spacing={1.5}>
    <Typography variant="body2" color="text.secondary">
      Choose how to verify your identity.
    </Typography>
    {methods.map((method) => (
      <Button
        key={method}
        variant="outlined"
        color="inherit"
        fullWidth
        onClick={() => onSelect(method)}
        sx={{
          justifyContent: "flex-start",
          borderColor: "divider",
          color: "text.primary",
          py: 1.2,
        }}
      >
        {labelFor(method)}
      </Button>
    ))}
  </Stack>
);
