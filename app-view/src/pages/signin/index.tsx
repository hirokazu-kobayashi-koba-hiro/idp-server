import { Box, Button, Container, Paper, TextField, Typography } from "@mui/material";
import { useState } from "react";
import { useRouter } from "next/router";

export default function SignIn() {
  const [email, setEmail] = useState("")
  const [password, setPassword] = useState("")
  const router = useRouter();
  const { id, session_key: sessionKey } = router.query;

  return (
    <>
      <Container maxWidth={"xs"}>
        <Paper sx={{ m: 4, p: 4}}>
          <Typography variant={"h5"}>Sign In</Typography>
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
            <Button variant={"contained"} onClick={async () => {
              const response = await fetch(`http://localhost:8080/123/api/v1/authorizations/${id}/authorize`, {
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

            }}>
              next
            </Button>
          </Box>
        </Paper>
      </Container>
    </>
  );
}
