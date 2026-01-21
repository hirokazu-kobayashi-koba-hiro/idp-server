"use client";

import { useState } from "react";
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogContentText,
  DialogTitle,
  Divider,
  IconButton,
  Stack,
  Typography,
} from "@mui/material";
import { Session } from "next-auth";
import AccountCircleIcon from "@mui/icons-material/AccountCircle";
import EmailIcon from "@mui/icons-material/Email";
import PhoneIcon from "@mui/icons-material/Phone";
import HomeIcon from "@mui/icons-material/Home";
import FingerprintIcon from "@mui/icons-material/Fingerprint";
import DevicesIcon from "@mui/icons-material/Devices";
import KeyIcon from "@mui/icons-material/Key";
import DeleteIcon from "@mui/icons-material/Delete";

interface AuthenticationDevice {
  id: string;
  app_name?: string;
  platform?: string;
  os?: string;
  model?: string;
  available_methods?: string[];
  priority?: number;
}

interface UserInfoProps {
  session: Session;
}

const UserInfo = ({ session }: UserInfoProps) => {
  const { user } = session;
  const [deletedDeviceIds, setDeletedDeviceIds] = useState<Set<string>>(new Set());
  const [deletingDeviceId, setDeletingDeviceId] = useState<string | null>(null);
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);
  const [deviceToDelete, setDeviceToDelete] = useState<AuthenticationDevice | null>(null);
  const [error, setError] = useState<string | null>(null);

  // ID Tokenをデコード（簡易実装）
  const decodeIdToken = (idToken: string | undefined) => {
    if (!idToken) return null;
    try {
      const payload = idToken.split(".")[1];
      const decoded = JSON.parse(atob(payload));
      return decoded;
    } catch (error) {
      console.error("Failed to decode ID token", error);
      return null;
    }
  };

  const idTokenClaims = decodeIdToken(session.idToken);

  const handleDeleteClick = (device: AuthenticationDevice) => {
    setDeviceToDelete(device);
    setDeleteDialogOpen(true);
    setError(null);
  };

  const handleCloseDialog = () => {
    setDeleteDialogOpen(false);
    setDeviceToDelete(null);
    setError(null);
  };

  const handleConfirmDelete = async () => {
    if (!deviceToDelete) return;

    setDeletingDeviceId(deviceToDelete.id);
    setError(null);

    try {
      const response = await fetch(
        `/api/authentication-devices/${encodeURIComponent(deviceToDelete.id)}`,
        {
          method: "DELETE",
        }
      );

      if (!response.ok) {
        const data = await response.json();
        setError(data.error_description || "パスキーの削除に失敗しました。");
        setDeletingDeviceId(null);
        return;
      }

      // Mark device as deleted
      setDeletedDeviceIds((prev) => new Set([...prev, deviceToDelete.id]));
      setDeleteDialogOpen(false);
      setDeviceToDelete(null);
    } catch {
      setError("エラーが発生しました。もう一度お試しください。");
    } finally {
      setDeletingDeviceId(null);
    }
  };

  // Filter out deleted devices
  const authenticationDevices: AuthenticationDevice[] = (
    idTokenClaims?.authentication_devices || []
  ).filter((device: AuthenticationDevice) => !deletedDeviceIds.has(device.id));

  return (
    <Card>
      <CardContent>
        <Stack spacing={3}>
          <Box>
            <Typography variant="h5" gutterBottom sx={{ display: "flex", alignItems: "center", gap: 1 }}>
              <AccountCircleIcon color="primary" />
              ユーザー情報
            </Typography>
            <Typography variant="body2" color="text.secondary">
              ID Token Claims
            </Typography>
          </Box>

          <Divider />

          {/* 基本情報 */}
          <Stack spacing={2}>
            <Box>
              <Typography variant="caption" color="text.secondary" sx={{ display: "flex", alignItems: "center", gap: 0.5 }}>
                <FingerprintIcon fontSize="small" />
                Subject (sub)
              </Typography>
              <Typography variant="body1" sx={{ fontFamily: "monospace", wordBreak: "break-all" }}>
                {user.sub || idTokenClaims?.sub || "N/A"}
              </Typography>
            </Box>

            {(user.name || idTokenClaims?.name) && (
              <Box>
                <Typography variant="caption" color="text.secondary" sx={{ display: "flex", alignItems: "center", gap: 0.5 }}>
                  <AccountCircleIcon fontSize="small" />
                  Name
                </Typography>
                <Typography variant="body1">
                  {user.name || idTokenClaims?.name}
                </Typography>
              </Box>
            )}

            {(user.email || idTokenClaims?.email) && (
              <Box>
                <Typography variant="caption" color="text.secondary" sx={{ display: "flex", alignItems: "center", gap: 0.5 }}>
                  <EmailIcon fontSize="small" />
                  Email
                </Typography>
                <Typography variant="body1">
                  {user.email || idTokenClaims?.email}
                  {idTokenClaims?.email_verified && (
                    <Chip label="Verified" size="small" color="success" sx={{ ml: 1 }} />
                  )}
                </Typography>
              </Box>
            )}

            {idTokenClaims?.phone_number && (
              <Box>
                <Typography variant="caption" color="text.secondary" sx={{ display: "flex", alignItems: "center", gap: 0.5 }}>
                  <PhoneIcon fontSize="small" />
                  Phone Number
                </Typography>
                <Typography variant="body1">
                  {idTokenClaims.phone_number}
                  {idTokenClaims?.phone_number_verified && (
                    <Chip label="Verified" size="small" color="success" sx={{ ml: 1 }} />
                  )}
                </Typography>
              </Box>
            )}

            {idTokenClaims?.address && (
              <Box>
                <Typography variant="caption" color="text.secondary" sx={{ display: "flex", alignItems: "center", gap: 0.5 }}>
                  <HomeIcon fontSize="small" />
                  Address
                </Typography>
                <Typography variant="body2" component="pre" sx={{ whiteSpace: "pre-wrap", fontFamily: "monospace" }}>
                  {JSON.stringify(idTokenClaims.address, null, 2)}
                </Typography>
              </Box>
            )}
          </Stack>

          {/* 認証デバイス情報 */}
          {idTokenClaims?.authentication_devices && idTokenClaims.authentication_devices.length > 0 && (
            <>
              <Divider />
              <Box>
                <Typography variant="subtitle1" gutterBottom sx={{ display: "flex", alignItems: "center", gap: 1 }}>
                  <DevicesIcon color="primary" />
                  登録済みパスキー ({authenticationDevices.length})
                </Typography>

                {deletedDeviceIds.size > 0 && (
                  <Alert severity="info" sx={{ mb: 2 }}>
                    パスキーを削除しました。変更を完全に反映するには、再ログインしてください。
                    <br />
                    <Typography variant="caption" color="text.secondary">
                      ※ デバイス側のパスキーは手動で削除する必要があります（ブラウザ設定やOS設定から）
                    </Typography>
                  </Alert>
                )}

                <Stack spacing={2}>
                  {authenticationDevices.map((device: AuthenticationDevice, index: number) => (
                    <Box
                      key={device.id || index}
                      sx={{
                        p: 2,
                        borderRadius: 1,
                        backgroundColor: "grey.50",
                        border: "1px solid",
                        borderColor: "grey.200",
                      }}
                    >
                      <Stack direction="row" alignItems="center" justifyContent="space-between" sx={{ mb: 1 }}>
                        <Stack direction="row" alignItems="center" spacing={1}>
                          <KeyIcon color="action" fontSize="small" />
                          <Typography variant="body2" fontWeight="bold">
                            パスキー #{index + 1}
                          </Typography>
                          {device.available_methods?.includes("fido2") && (
                            <Chip label="FIDO2" size="small" color="primary" />
                          )}
                        </Stack>
                        <IconButton
                          size="small"
                          color="error"
                          onClick={() => handleDeleteClick(device)}
                          disabled={deletingDeviceId === device.id}
                          title="このパスキーを削除"
                        >
                          {deletingDeviceId === device.id ? (
                            <CircularProgress size={20} color="error" />
                          ) : (
                            <DeleteIcon fontSize="small" />
                          )}
                        </IconButton>
                      </Stack>
                      <Stack spacing={0.5}>
                        {device.platform && (
                          <Typography variant="caption" color="text.secondary">
                            Platform: {device.platform}
                          </Typography>
                        )}
                        {device.os && (
                          <Typography variant="caption" color="text.secondary">
                            OS: {device.os}
                          </Typography>
                        )}
                        {device.model && (
                          <Typography variant="caption" color="text.secondary">
                            Model: {device.model}
                          </Typography>
                        )}
                        <Typography variant="caption" color="text.secondary" sx={{ fontFamily: "monospace", wordBreak: "break-all" }}>
                          ID: {device.id}
                        </Typography>
                      </Stack>
                    </Box>
                  ))}
                </Stack>

                {authenticationDevices.length === 0 && (
                  <Alert severity="warning">
                    すべてのパスキーが削除されました。再ログインすると、新しいパスキーを登録できます。
                  </Alert>
                )}
              </Box>
            </>
          )}

          {/* その他のクレーム */}
          {idTokenClaims && (
            <>
              <Divider />
              <Box>
                <Typography variant="caption" color="text.secondary" gutterBottom>
                  All ID Token Claims
                </Typography>
                <Box
                  component="pre"
                  sx={{
                    backgroundColor: "grey.100",
                    p: 2,
                    borderRadius: 1,
                    overflow: "auto",
                    fontSize: "0.75rem",
                    fontFamily: "monospace",
                    maxHeight: "300px",
                  }}
                >
                  {JSON.stringify(idTokenClaims, null, 2)}
                </Box>
              </Box>
            </>
          )}
        </Stack>
      </CardContent>

      {/* 削除確認ダイアログ */}
      <Dialog
        open={deleteDialogOpen}
        onClose={handleCloseDialog}
        aria-labelledby="delete-device-dialog-title"
        aria-describedby="delete-device-dialog-description"
      >
        <DialogTitle id="delete-device-dialog-title">
          パスキーの削除確認
        </DialogTitle>
        <DialogContent>
          <DialogContentText id="delete-device-dialog-description">
            このパスキーを削除してもよろしいですか？
            {deviceToDelete && (
              <Box sx={{ mt: 2, p: 2, backgroundColor: "grey.100", borderRadius: 1 }}>
                <Typography variant="body2">
                  {deviceToDelete.platform && `Platform: ${deviceToDelete.platform}`}
                  {deviceToDelete.os && ` / OS: ${deviceToDelete.os}`}
                </Typography>
                <Typography variant="caption" sx={{ fontFamily: "monospace", wordBreak: "break-all" }}>
                  ID: {deviceToDelete.id}
                </Typography>
              </Box>
            )}
          </DialogContentText>
          {error && (
            <Alert severity="error" sx={{ mt: 2 }}>
              {error}
            </Alert>
          )}
          <Alert severity="warning" sx={{ mt: 2 }}>
            <Typography variant="body2">
              <strong>注意:</strong> サーバーからパスキーを削除しても、デバイス側のパスキーは残ります。
              デバイス側のパスキーは、ブラウザ設定やOS設定から手動で削除してください。
            </Typography>
          </Alert>
        </DialogContent>
        <DialogActions>
          <Button onClick={handleCloseDialog} disabled={!!deletingDeviceId}>
            キャンセル
          </Button>
          <Button
            onClick={handleConfirmDelete}
            color="error"
            variant="contained"
            disabled={!!deletingDeviceId}
          >
            {deletingDeviceId ? "削除中..." : "削除する"}
          </Button>
        </DialogActions>
      </Dialog>
    </Card>
  );
};

export default UserInfo;
