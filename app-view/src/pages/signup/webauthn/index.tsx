import { useState } from "react";
import {
  Container,
  Typography,
  Button,
  Box,
  CircularProgress,
  Paper,
  Stack,
} from "@mui/material";
import { v4 as uuidv4 } from "uuid";
import KeyIcon from "@mui/icons-material/Key";
import { useRouter } from "next/router";
import { backendUrl, useAppContext } from "@/pages/_app";
import { SignupStepper } from "@/components/SignupStepper";

export default function Register() {
  const router = useRouter();
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState("");
  const { userId } = useAppContext();
  const { id, tenant_id: tenantId } = router.query;

  const handleRegister = async () => {
    setLoading(true);
    setMessage("");

    try {
      const res = await fetch(
        `${backendUrl}/${tenantId}/api/v1/authorizations/${id}/webauthn/registration/challenge`,
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
            name: uuidv4(),
            displayName: "test",
          },
          pubKeyCredParams: [{ type: "public-key", alg: -7 }],
        },
      });

      const registerRes = await fetch(
        `${backendUrl}/${tenantId}/api/v1/authorizations/${id}/webauthn/registration/response`,
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
    <Container maxWidth={"sm"}>
      <Paper sx={{ m: 4, p: 4, boxShadow: 3 }}>
        <Stack spacing={4}>
          <Typography variant={"h5"}>Sign Up</Typography>

          <SignupStepper activeStep={2} />

          <Box display={"flex"} gap={4} alignItems={"center"}>
            <KeyIcon sx={{ fontSize: 50, color: "primary.secondary" }} />
            <Typography variant="h5">Passkey Registration</Typography>
          </Box>

          <Typography variant="body2" color="text.secondary">
            Secure your account with a passkey for fast authentication.
          </Typography>

          <Box mt={3}>
            <Button
              variant="contained"
              color="primary"
              onClick={handleRegister}
              disabled={loading}
              sx={{ width: "100%", textTransform: "none" }}
            >
              {loading ? <CircularProgress size={24} /> : "Next"}
            </Button>
          </Box>
          {message && (
            <Typography mt={2} color="primary">
              {message}
            </Typography>
          )}
        </Stack>
      </Paper>
    </Container>
  );
}
