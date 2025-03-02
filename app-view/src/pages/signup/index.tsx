import {
  Box,
  Button,
  Container,
  InputAdornment,
  Paper,
  TextField,
  Typography,
} from "@mui/material";
import { useState } from "react";
import { backendUrl, useAppContext } from "@/pages/_app";
import { useRouter } from "next/router";
import { Email, Lock } from "@mui/icons-material";
import { SignupStepper } from "@/components/SignupStepper";
import PersonAddIcon from "@mui/icons-material/PersonAdd";

export default function SignUp() {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const router = useRouter();
  const { setUserId } = useAppContext();
  const { id, tenant_id: tenantId } = router.query;

  const handleClick = async () => {
    const response = await fetch(
      `${backendUrl}/${tenantId}/api/v1/authorizations/${id}/signup`,
      {
        method: "POST",
        credentials: "include",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          username: email,
          password: password,
        }),
      },
    );
    if (!response.ok) {
      console.error(response.status);
      return;
    }
    const body = await response.json();
    console.log(body);
    setUserId(body.id);

    const sendingEmailResponse = await fetch(
      `${backendUrl}/${tenantId}/api/v1/authorizations/${id}/email-verification/challenge`,
      {
        method: "POST",
        credentials: "include",
      },
    );

    if (!sendingEmailResponse.ok) {
      console.error("sending email verification code is failed");
    }

    router.push(`/signup/email?id=${id}&tenant_id=${tenantId}`);
  };

  return (
    <>
      <Container maxWidth="sm">
        <Paper sx={{ p: 3, boxShadow: 3 }}>
          <Typography variant="h5">Sign Up</Typography>

          <Box mt={2} display="flex" flexDirection="column" sx={{ gap: 3 }}>
            <SignupStepper activeStep={0} />

            <Box display="flex" gap={2} alignItems="center">
              <PersonAddIcon sx={{ fontSize: 40, color: "primary.main" }} />
              <Typography variant="h6">Account Registration</Typography>
            </Box>

            <TextField
              name="email"
              label="Email"
              placeholder="test@gmail.com"
              inputMode="email"
              value={email}
              required
              InputProps={{
                startAdornment: (
                  <InputAdornment position="start">
                    <Email />
                  </InputAdornment>
                ),
              }}
              onChange={(e) => setEmail(e.target.value)}
            />
            <TextField
              name="password"
              label="Password"
              type="password"
              value={password}
              required
              InputProps={{
                startAdornment: (
                  <InputAdornment position="start">
                    <Lock />
                  </InputAdornment>
                ),
              }}
              onChange={(e) => setPassword(e.target.value)}
            />
            <Button
              variant="contained"
              disabled={!email || !password}
              onClick={handleClick}
              sx={{ textTransform: "none", alignSelf: "flex-end" }}
            >
              Next
            </Button>
          </Box>
        </Paper>
      </Container>
    </>
  );
}
