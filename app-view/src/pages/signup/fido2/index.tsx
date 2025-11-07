"use client";

import { useState } from "react";
import {
  Container,
  Typography,
  Button,
  Box,
  CircularProgress,
  Paper,
  Stack,
  useTheme,
  alpha,
} from "@mui/material";
import { v4 as uuidv4 } from "uuid";
import KeyIcon from "@mui/icons-material/Key";
import { useRouter } from "next/router";
import { backendUrl, useAppContext } from "@/pages/_app";
import { SignupStepper } from "@/components/SignupStepper";

export default function Fido2RegistrationPage() {
  const router = useRouter();
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState("");
  const { userId } = useAppContext();
  const { id, tenant_id: tenantId } = router.query;
  const theme = useTheme();

  const handleRegister = async () => {
    setLoading(true);
    setMessage("");

    try {
      const res = await fetch(
        `${backendUrl}/${tenantId}/v1/authorizations/${id}/fido2-registration-challenge`,
        {
          credentials: "include",
        },
      );
      const { challenge } = await res.json();
      const decodedChallenge = challenge.replace(/-/g, "+").replace(/_/g, "/");

      const userIdBytes = new TextEncoder().encode(userId || "");

      const credential = await navigator.credentials.create({
        publicKey: {
          challenge: new Uint8Array(
            atob(decodedChallenge)
              .split("")
              .map((c) => c.charCodeAt(0)),
          ),
          rp: { name: "Passkey Demo" },
          user: {
            id: userIdBytes,
            name: userId || uuidv4(),
            displayName: "test",
          },
          pubKeyCredParams: [{ type: "public-key", alg: -7 }],
        },
      });

      const registerRes = await fetch(
        `${backendUrl}/${tenantId}/v1/authorizations/${id}/fido2-registration`,
        {
          method: "POST",
          credentials: "include",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(credential),
        },
      );

      if (registerRes.ok) {
        setMessage("Passkey registration successful!");
        router.push(`/signup/authorize?id=${id}&tenant_id=${tenantId}`);
        return;
      }
      setMessage("Registration failed.");
    } catch (error) {
      console.error(error);
      setMessage("An error occurred during registration.");
    }

    setLoading(false);
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
          boxShadow:
            theme.palette.mode === "light"
              ? "0 6px 24px rgba(0,0,0,0.025)"
              : "0 0 0 1px rgba(255,255,255,0.06)",
        }}
      >
        <Typography variant="h5" fontWeight={600} gutterBottom>
          Passkey Registration
        </Typography>
        <Typography variant="body2" color="text.secondary" mb={4}>
          Secure your account with a passkey for fast and passwordless sign-in.
        </Typography>

        <Stack spacing={3}>
          <SignupStepper activeStep={2} />

          <Box display="flex" justifyContent="center">
            <KeyIcon sx={{ fontSize: 40, color: "primary.main" }} />
          </Box>

          <Box>
            <Button
              variant="contained"
              color="primary"
              onClick={handleRegister}
              disabled={loading}
              fullWidth
              sx={{ textTransform: "none" }}
            >
              {loading ? <CircularProgress size={24} /> : "Register Passkey"}
            </Button>
          </Box>

          {message && (
            <Typography mt={2} color="error" align="center">
              {message}
            </Typography>
          )}
        </Stack>
      </Paper>
    </Container>
  );
}
