"use client";

import { useState } from "react";
import {
  Card,
  CardContent,
  Typography,
  TextField,
  Button,
  Alert,
  Stack,
  Box,
} from "@mui/material";
import LockIcon from "@mui/icons-material/Lock";

const PasswordChange = () => {
  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setSuccess(false);

    // Validation
    if (!currentPassword || !newPassword) {
      setError("現在のパスワードと新しいパスワードを入力してください。");
      return;
    }

    if (newPassword !== confirmPassword) {
      setError("新しいパスワードが一致しません。");
      return;
    }

    if (newPassword.length < 8) {
      setError("新しいパスワードは8文字以上である必要があります。");
      return;
    }

    setLoading(true);

    try {
      const response = await fetch("/api/password/change", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          current_password: currentPassword,
          new_password: newPassword,
        }),
      });

      const data = await response.json();

      if (!response.ok) {
        setError(data.error_description || "パスワード変更に失敗しました。");
        return;
      }

      setSuccess(true);
      setCurrentPassword("");
      setNewPassword("");
      setConfirmPassword("");
    } catch {
      setError("エラーが発生しました。もう一度お試しください。");
    } finally {
      setLoading(false);
    }
  };

  return (
    <Card>
      <CardContent>
        <Stack spacing={3}>
          <Box display="flex" alignItems="center" gap={1}>
            <LockIcon color="primary" />
            <Typography variant="h6" component="h2">
              パスワード変更
            </Typography>
          </Box>

          {error && <Alert severity="error">{error}</Alert>}
          {success && (
            <Alert severity="success">
              パスワードが正常に変更されました。
            </Alert>
          )}

          <form onSubmit={handleSubmit}>
            <Stack spacing={2}>
              <TextField
                type="password"
                label="現在のパスワード"
                value={currentPassword}
                onChange={(e) => setCurrentPassword(e.target.value)}
                fullWidth
                required
                disabled={loading}
              />

              <TextField
                type="password"
                label="新しいパスワード"
                value={newPassword}
                onChange={(e) => setNewPassword(e.target.value)}
                fullWidth
                required
                disabled={loading}
                helperText="8文字以上で入力してください"
              />

              <TextField
                type="password"
                label="新しいパスワード（確認）"
                value={confirmPassword}
                onChange={(e) => setConfirmPassword(e.target.value)}
                fullWidth
                required
                disabled={loading}
              />

              <Button
                type="submit"
                variant="contained"
                fullWidth
                disabled={loading}
                startIcon={<LockIcon />}
              >
                {loading ? "変更中..." : "パスワードを変更"}
              </Button>
            </Stack>
          </form>
        </Stack>
      </CardContent>
    </Card>
  );
};

export default PasswordChange;
