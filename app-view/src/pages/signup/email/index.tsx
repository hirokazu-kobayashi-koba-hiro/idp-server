import {
  Box,
  Button,
  Container,
  Divider,
  Link,
  Paper,
  Stack,
  TextField,
  Typography,
} from "@mui/material";
import { useRouter } from "next/router";
import { backendUrl } from "@/pages/_app";
import { useState } from "react";
import { SignupStepper } from "@/components/SignupStepper";
import { Email } from "@mui/icons-material";

const EmailVerification = () => {
  const router = useRouter();
  const [verificationCode, setVerificationCode] = useState("");
  const { id, tenant_id: tenantId } = router.query;

  const handleReSend = async () => {
    const response = await fetch(
      `${backendUrl}/${tenantId}/api/v1/authorizations/${id}/email-verification/challenge`,
      {
        method: "POST",
        credentials: "include",
      },
    );
    if (!response.ok) {
      throw new Error("sending email verification code is failed");
    }
  };

  const handleNext = async () => {
    const response = await fetch(
      `${backendUrl}/${tenantId}/api/v1/authorizations/${id}/email-verification/verify`,
      {
        method: "POST",
        credentials: "include",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          verification_code: verificationCode,
        }),
      },
    );

    if (response.ok) {
      router.push(`/signup/authorize?id=${id}&tenant_id=${tenantId}`);
    }
  };

  return (
    <>
      <Container maxWidth={"sm"}>
        <Paper sx={{ p: 3, boxShadow: 3 }}>
          <Stack spacing={4}>
            <Typography variant={"h5"}>Sign Up</Typography>

            <SignupStepper activeStep={1} />

            <Box display={"flex"} gap={4} alignItems={"center"}>
              <Email sx={{ fontSize: 50, color: "primary.secondary" }} />
              <Typography variant="h5">Email Verification</Typography>
            </Box>

            <TextField
              label={"verification code"}
              inputMode={"numeric"}
              placeholder={"000000"}
              onChange={(e) => {
                setVerificationCode(e.target.value);
              }}
            />
            <Button
              variant={"contained"}
              sx={{ textTransform: "none" }}
              onClick={handleNext}
            >
              Next
            </Button>
            <Box sx={{ mt: 2 }}>
              <Divider />
            </Box>
            <Box sx={{ mt: 2, gap: 2 }} display="flex">
              <Link onClick={handleReSend}>ReSend verification code</Link>
            </Box>
          </Stack>
        </Paper>
      </Container>
    </>
  );
};

export default EmailVerification;
