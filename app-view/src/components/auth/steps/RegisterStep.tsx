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
 * 1st-factor identify + account creation step (method "password" with registration allowed).
 *
 * Persists the entered email to {@link authIdentifierAtom} so later steps (e.g. FIDO2) can recover
 * the username after a reload.
 */
export const RegisterStep = ({ tenantId, id, onCompleted }: StepProps) => {
  const [identifier, setIdentifier] = useAtom(authIdentifierAtom);
  const [email, setEmail] = useState(identifier);
  const [password, setPassword] = useState("");
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState("");

  const handleSubmit = async () => {
    if (!email.includes("@") || password.length < 8) {
      setMessage("Enter a valid email and a password with at least 8 characters.");
      return;
    }
    setLoading(true);
    setMessage("");
    try {
      const response = await fetch(
        `${backendUrl}/${tenantId}/v1/authorizations/${id}/initial-registration`,
        {
          method: "POST",
          credentials: "include",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ name: email, email, password }),
        },
      );
      if (!response.ok) {
        setMessage("We couldn't create your account. Please try again.");
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
        Enter your email and choose a password to get started.
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
