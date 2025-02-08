import { Box, Button, Container, Paper, TextField, Typography } from "@mui/material";
import { useState } from "react";

export default function Home() {
  const [email, setEmail] = useState("")
  const [password, setPassword] = useState("")

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
            <Button variant={"contained"} onClick={async () => {
              const params = new URLSearchParams({
                username: email,
                password: password,
                grant_type: "password"
              });
              const response = await fetch("http://localhost:8080/123/api/v1/tokens", {
                method: "POST",
                headers: {
                  "Content-Type": "application/x-www-form-urlencoded"
                },
                body: params
              })
              const body = await response.json()
              console.log(response.status, body)
            }}>
              register
            </Button>
          </Box>
        </Paper>
      </Container>
    </>
  );
}
