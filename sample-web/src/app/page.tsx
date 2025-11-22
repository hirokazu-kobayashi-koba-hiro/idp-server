"use client";

import {
  Box,
  Button,
  Container,
  Paper,
  Stack,
  Typography,
} from "@mui/material";
import { signIn } from "next-auth/react";
import LoginIcon from "@mui/icons-material/Login";
import LockIcon from "@mui/icons-material/Lock";

const Login = () => {
  const handleSignIn = async () => {
    await signIn("idp-server");
  };

  return (
    <Container maxWidth="sm">
      <Box
        sx={{
          display: "flex",
          flexDirection: "column",
          alignItems: "center",
          justifyContent: "center",
          minHeight: "100vh",
        }}
      >
        <Paper
          elevation={3}
          sx={{
            p: 6,
            width: "100%",
            textAlign: "center",
          }}
        >
          <Stack spacing={4}>
            {/* アイコン */}
            <Box sx={{ display: "flex", justifyContent: "center" }}>
              <LockIcon sx={{ fontSize: 80, color: "primary.main" }} />
            </Box>

            {/* タイトル */}
            <Typography variant="h4" component="h1" fontWeight="bold">
              IdP Server Sample App
            </Typography>

            {/* 説明 */}
            <Typography variant="body1" color="text.secondary">
              FIDO2/Passkey認証のデモアプリケーション
            </Typography>

            <Box sx={{ borderTop: 1, borderColor: "divider", my: 2 }} />

            {/* 機能説明 */}
            <Stack spacing={1} sx={{ textAlign: "left" }}>
              <Typography variant="body2" color="text.secondary">
                ✨ Conditional UI (Autofill)
              </Typography>
              <Typography variant="body2" color="text.secondary">
                🔒 Platform Authenticator対応
              </Typography>
              <Typography variant="body2" color="text.secondary">
                🌐 OAuth 2.0 / OIDC準拠
              </Typography>
              <Typography variant="body2" color="text.secondary">
                🔑 Authorization Code Flow with PKCE
              </Typography>
            </Stack>

            <Box sx={{ borderTop: 1, borderColor: "divider", my: 2 }} />

            {/* ログインボタン */}
            <Button
              variant="contained"
              size="large"
              startIcon={<LoginIcon />}
              onClick={handleSignIn}
              sx={{
                py: 1.5,
                fontSize: "1.1rem",
              }}
            >
              Sign in with Passkey
            </Button>

            {/* フッター */}
            <Typography variant="caption" color="text.secondary" sx={{ mt: 4 }}>
              Powered by idp-server
            </Typography>
          </Stack>
        </Paper>
      </Box>
    </Container>
  );
};

export default Login;
