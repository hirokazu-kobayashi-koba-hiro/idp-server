"use client";

import { useState } from "react";
import {
  Box,
  Button,
  CircularProgress,
  InputAdornment,
  Stack,
  TextField,
  Typography,
} from "@mui/material";
import { Email, Lock } from "@mui/icons-material";
import { useAtom } from "jotai";
import { backendUrl } from "@/pages/_app";
import { authIdentifierAtom } from "@/state/AuthState";
import { StepProps } from "./StepProps";

/**
 * Password authentication step (method "password", verify-only).
 *
 * Used for existing-user login and as the default step when a policy has no `step_definitions`.
 * Persists the email to {@link authIdentifierAtom} so later steps can recover it after a reload.
 * Use {@code RegisterStep} instead when the step creates a new account.
 */
export const PasswordAuthStep = ({ tenantId, id, onCompleted }: StepProps) => {
  const [identifier, setIdentifier] = useAtom(authIdentifierAtom);
  const [email, setEmail] = useState(identifier);
  const [password, setPassword] = useState("");
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState("");

  const handleSubmit = async () => {
    setLoading(true);
    setMessage("");
    try {
      const response = await fetch(
        `${backendUrl}/${tenantId}/v1/authorizations/${id}/password-authentication`,
        {
          method: "POST",
          credentials: "include",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ username: email, password }),
        },
      );
      if (!response.ok) {
        setMessage("The email or password is incorrect. Please try again.");
        return;
      }
      setIdentifier(email);
      await onCompleted();
    } finally {
      setLoading(false);
    }
  };

  return (
    <Stack spacing={3}>
      <Typography variant="body2" color="text.secondary">
        Welcome back. Enter your email and password to continue.
      </Typography>
      <TextField
        label="Email"
        value={email}
        onChange={(e) => setEmail(e.target.value)}
        InputProps={{
          startAdornment: (
            <InputAdornment position="start">
              <Email />
            </InputAdornment>
          ),
        }}
      />
      <TextField
        label="Password"
        type="password"
        value={password}
        onChange={(e) => setPassword(e.target.value)}
        InputProps={{
          startAdornment: (
            <InputAdornment position="start">
              <Lock />
            </InputAdornment>
          ),
        }}
      />
      {message && (
        <Typography color="error" variant="body2">
          {message}
        </Typography>
      )}
      <Box display="flex" justifyContent="flex-end">
        <Button
          variant="contained"
          disabled={loading || !email || !password}
          onClick={handleSubmit}
          sx={{ textTransform: "none" }}
        >
          {loading ? <CircularProgress size={24} /> : "Continue"}
        </Button>
      </Box>
    </Stack>
  );
};
