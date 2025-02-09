import { Box, Button, Container, Paper, TextField, Typography } from "@mui/material";
import { useState } from "react";
import { backendUrl } from "@/pages/_app";
import { useRouter } from "next/router";

export default function SignUp() {
  const [email, setEmail] = useState("")
  const [password, setPassword] = useState("")
  const router = useRouter();
  const { id, tenant_id: tenantId, session_key: sessionKey } = router.query;

  const handleClick = async () => {
    const response = await fetch(`${backendUrl}/${tenantId}/api/v1/authorizations/${id}/signup`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json"
      },
      body: JSON.stringify({
        username: email,
        password: password,
        session_key: sessionKey,
      })
    })
    const body = await response.json()
    console.log(response.status, body)
    if (body.redirect_uri) {
      window.location.href = body.redirect_uri;
    }
  }

  return (
    <>
      <Container maxWidth={"xs"}>
        <Paper sx={{ m: 4, p: 4}}>
          <Typography variant={"h5"}>Sign Up</Typography>
          <Box mt={4} display="flex" flexDirection={"column"} sx={{ gap: 4,}}>
            <TextField
              name={"email"}
              label={"email"}
              placeholder={"test@gmail.com"}
              type="email"
              value={email}
              onChange={(e) => {
                setEmail(e.target.value)
              }} />
            <TextField
              name={"password"}
              label={"password"}
              type="password"
              value={password}
              onChange={(e) => {
                setPassword(e.target.value)
              }} />
            <Button variant={"contained"} onClick={handleClick}>
              next
            </Button>
          </Box>
        </Paper>
      </Container>
    </>
  );
}
