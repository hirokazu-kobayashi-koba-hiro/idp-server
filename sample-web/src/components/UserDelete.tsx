"use client";

import { useState } from "react";
import {
  Card,
  CardContent,
  Typography,
  Button,
  Alert,
  Stack,
  Box,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogContentText,
  DialogActions,
} from "@mui/material";
import DeleteForeverIcon from "@mui/icons-material/DeleteForever";
import WarningIcon from "@mui/icons-material/Warning";

const UserDelete = () => {
  const [openDialog, setOpenDialog] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleOpenDialog = () => {
    setOpenDialog(true);
    setError(null);
  };

  const handleCloseDialog = () => {
    setOpenDialog(false);
    setError(null);
  };

  const handleDelete = async () => {
    setLoading(true);
    setError(null);

    try {
      const response = await fetch("/api/user/delete", {
        method: "DELETE",
      });

      if (!response.ok) {
        const data = await response.json();
        setError(data.error_description || "アカウント削除に失敗しました。");
        setLoading(false);
        return;
      }

      // Delete successful - sign out and redirect
      const { signOut } = await import("@/app/auth");
      await signOut();
    } catch {
      setError("エラーが発生しました。もう一度お試しください。");
      setLoading(false);
    }
  };

  return (
    <>
      <Card>
        <CardContent>
          <Stack spacing={3}>
            <Box display="flex" alignItems="center" gap={1}>
              <DeleteForeverIcon color="error" />
              <Typography variant="h6" component="h2" color="error">
                アカウント削除
              </Typography>
            </Box>

            <Alert severity="warning" icon={<WarningIcon />}>
              <Typography variant="body2">
                <strong>この操作は取り消せません。</strong>
                <br />
                アカウントを削除すると、すべてのデータが完全に削除され、復元できなくなります。
              </Typography>
            </Alert>

            {error && <Alert severity="error">{error}</Alert>}

            <Button
              variant="outlined"
              color="error"
              fullWidth
              onClick={handleOpenDialog}
              disabled={loading}
              startIcon={<DeleteForeverIcon />}
            >
              アカウントを削除
            </Button>
          </Stack>
        </CardContent>
      </Card>

      <Dialog
        open={openDialog}
        onClose={handleCloseDialog}
        aria-labelledby="alert-dialog-title"
        aria-describedby="alert-dialog-description"
      >
        <DialogTitle id="alert-dialog-title">
          アカウント削除の確認
        </DialogTitle>
        <DialogContent>
          <DialogContentText id="alert-dialog-description">
            本当にアカウントを削除してもよろしいですか？
            <br />
            <br />
            この操作は取り消すことができません。すべてのデータが完全に削除されます。
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={handleCloseDialog} disabled={loading}>
            キャンセル
          </Button>
          <Button
            onClick={handleDelete}
            color="error"
            variant="contained"
            disabled={loading}
            autoFocus
          >
            {loading ? "削除中..." : "削除する"}
          </Button>
        </DialogActions>
      </Dialog>
    </>
  );
};

export default UserDelete;
