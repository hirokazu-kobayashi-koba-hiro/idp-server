"use client";

import { useState } from "react";
import {
  Box,
  Button,
  CircularProgress,
  IconButton,
  InputAdornment,
  Stack,
  TextField,
  Typography,
} from "@mui/material";
import { Email, Lock, Visibility, VisibilityOff } from "@mui/icons-material";
import { useAtom } from "jotai";
import { backendUrl } from "@/pages/_app";
import { readErrorMessage, readUserStatus } from "@/auth/http";
import { authIdentifierAtom, authUserStatusAtom } from "@/state/AuthState";
import { AuthAlert } from "@/components/auth/AuthAlert";
import { StepProps } from "./StepProps";

/**
 * 1st-factor identify + account creation step (method "password" with registration allowed).
 *
 * Persists the entered email to {@link authIdentifierAtom} so later steps (e.g. FIDO2) can recover
 * the username after a reload.
 */
export const RegisterStep = ({ tenantId, id, onCompleted }: StepProps) => {
  const [identifier, setIdentifier] = useAtom(authIdentifierAtom);
  const [, setUserStatus] = useAtom(authUserStatusAtom);
  const [email, setEmail] = useState(identifier);
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
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
        setMessage(
          await readErrorMessage(
            response,
            "We couldn't create your account. Please try again.",
          ),
        );
        return;
      }
      setIdentifier(email);
      setUserStatus(await readUserStatus(response));
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
        type="email"
        autoComplete="email"
        autoFocus
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
        type={showPassword ? "text" : "password"}
        autoComplete="new-password"
        value={password}
        onChange={(e) => setPassword(e.target.value)}
        InputProps={{
          startAdornment: (
            <InputAdornment position="start">
              <Lock />
            </InputAdornment>
          ),
          endAdornment: (
            <InputAdornment position="end">
              <IconButton
                onClick={() => setShowPassword((prev) => !prev)}
                edge="end"
                aria-label={showPassword ? "Hide password" : "Show password"}
              >
                {showPassword ? <VisibilityOff /> : <Visibility />}
              </IconButton>
            </InputAdornment>
          ),
        }}
      />
      <AuthAlert message={message} />
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
