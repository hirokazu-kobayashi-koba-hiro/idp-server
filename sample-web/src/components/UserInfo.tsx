"use client";

import { useState, useEffect, useCallback } from "react";
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
  Tooltip,
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
import RefreshIcon from "@mui/icons-material/Refresh";

interface AuthenticationDevice {
  id: string;
  app_name?: string;
  platform?: string;
  os?: string;
  model?: string;
  available_methods?: string[];
  priority?: number;
}

interface UserinfoResponse {
  sub?: string;
  name?: string;
  email?: string;
  email_verified?: boolean;
  phone_number?: string;
  phone_number_verified?: boolean;
  address?: Record<string, unknown>;
  authentication_devices?: AuthenticationDevice[];
  [key: string]: unknown;
}

interface UserInfoProps {
  session: Session;
}

const UserInfo = ({ session }: UserInfoProps) => {
  const [userinfo, setUserinfo] = useState<UserinfoResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [deletedDeviceIds, setDeletedDeviceIds] = useState<Set<string>>(new Set());
  const [deletingDeviceId, setDeletingDeviceId] = useState<string | null>(null);
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);
  const [deviceToDelete, setDeviceToDelete] = useState<AuthenticationDevice | null>(null);
  const [deleteError, setDeleteError] = useState<string | null>(null);

  const fetchUserinfo = useCallback(async () => {
    if (!session.accessToken) {
      setError("Access token is not available");
      setLoading(false);
      return;
    }

    setLoading(true);
    setError(null);

    try {
      const response = await fetch("/api/userinfo", {
        method: "GET",
        headers: {
          Authorization: `Bearer ${session.accessToken}`,
        },
      });

      if (!response.ok) {
        const errorText = await response.text();
        throw new Error(`Failed to fetch userinfo: ${response.status} ${errorText}`);
      }

      const data = await response.json();
      setUserinfo(data);
      // Reset deleted device IDs on successful refresh
      setDeletedDeviceIds(new Set());
    } catch (err) {
      setError(err instanceof Error ? err.message : "Unknown error occurred");
    } finally {
      setLoading(false);
    }
  }, [session.accessToken]);

  useEffect(() => {
    fetchUserinfo();
  }, [fetchUserinfo]);

  const handleDeleteClick = (device: AuthenticationDevice) => {
    setDeviceToDelete(device);
    setDeleteDialogOpen(true);
    setDeleteError(null);
  };

  const handleCloseDialog = () => {
    setDeleteDialogOpen(false);
    setDeviceToDelete(null);
    setDeleteError(null);
  };

  const handleConfirmDelete = async () => {
    if (!deviceToDelete) return;

    setDeletingDeviceId(deviceToDelete.id);
    setDeleteError(null);

    try {
      const response = await fetch(
        `/api/authentication-devices/${encodeURIComponent(deviceToDelete.id)}`,
        {
          method: "DELETE",
        }
      );

      if (!response.ok) {
        const data = await response.json();
        setDeleteError(data.error_description || "パスキーの削除に失敗しました。");
        setDeletingDeviceId(null);
        return;
      }

      // Mark device as deleted locally
      setDeletedDeviceIds((prev) => new Set([...prev, deviceToDelete.id]));
      setDeleteDialogOpen(false);
      setDeviceToDelete(null);
    } catch {
      setDeleteError("エラーが発生しました。もう一度お試しください。");
    } finally {
      setDeletingDeviceId(null);
    }
  };

  // Filter out deleted devices
  const authenticationDevices: AuthenticationDevice[] = (
    userinfo?.authentication_devices || []
  ).filter((device: AuthenticationDevice) => !deletedDeviceIds.has(device.id));

  if (loading) {
    return (
      <Card>
        <CardContent>
          <Box display="flex" justifyContent="center" alignItems="center" py={4}>
            <CircularProgress size={32} />
            <Typography variant="body2" color="text.secondary" sx={{ ml: 2 }}>
              ユーザー情報を取得中...
            </Typography>
          </Box>
        </CardContent>
      </Card>
    );
  }

  if (error) {
    return (
      <Card>
        <CardContent>
          <Alert severity="error" sx={{ mb: 2 }}>
            {error}
          </Alert>
          <Button variant="outlined" onClick={fetchUserinfo} startIcon={<RefreshIcon />}>
            再試行
          </Button>
        </CardContent>
      </Card>
    );
  }

  return (
    <Card>
      <CardContent>
        <Stack spacing={3}>
          <Box display="flex" justifyContent="space-between" alignItems="center">
            <Box>
              <Typography variant="h5" gutterBottom sx={{ display: "flex", alignItems: "center", gap: 1 }}>
                <AccountCircleIcon color="primary" />
                ユーザー情報
              </Typography>
              <Typography variant="body2" color="text.secondary">
                Userinfo Endpoint
              </Typography>
            </Box>
            <Tooltip title="再取得">
              <IconButton onClick={fetchUserinfo} disabled={loading} size="small">
                <RefreshIcon />
              </IconButton>
            </Tooltip>
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
                {userinfo?.sub || "N/A"}
              </Typography>
            </Box>

            {userinfo?.name && (
              <Box>
                <Typography variant="caption" color="text.secondary" sx={{ display: "flex", alignItems: "center", gap: 0.5 }}>
                  <AccountCircleIcon fontSize="small" />
                  Name
                </Typography>
                <Typography variant="body1">
                  {userinfo.name}
                </Typography>
              </Box>
            )}

            {userinfo?.email && (
              <Box>
                <Typography variant="caption" color="text.secondary" sx={{ display: "flex", alignItems: "center", gap: 0.5 }}>
                  <EmailIcon fontSize="small" />
                  Email
                </Typography>
                <Typography variant="body1">
                  {userinfo.email}
                  {userinfo.email_verified && (
                    <Chip label="Verified" size="small" color="success" sx={{ ml: 1 }} />
                  )}
                </Typography>
              </Box>
            )}

            {userinfo?.phone_number && (
              <Box>
                <Typography variant="caption" color="text.secondary" sx={{ display: "flex", alignItems: "center", gap: 0.5 }}>
                  <PhoneIcon fontSize="small" />
                  Phone Number
                </Typography>
                <Typography variant="body1">
                  {userinfo.phone_number}
                  {userinfo.phone_number_verified && (
                    <Chip label="Verified" size="small" color="success" sx={{ ml: 1 }} />
                  )}
                </Typography>
              </Box>
            )}

            {userinfo?.address && (
              <Box>
                <Typography variant="caption" color="text.secondary" sx={{ display: "flex", alignItems: "center", gap: 0.5 }}>
                  <HomeIcon fontSize="small" />
                  Address
                </Typography>
                <Typography variant="body2" component="pre" sx={{ whiteSpace: "pre-wrap", fontFamily: "monospace" }}>
                  {JSON.stringify(userinfo.address, null, 2)}
                </Typography>
              </Box>
            )}
          </Stack>

          {/* 認証デバイス情報 */}
          {userinfo?.authentication_devices && userinfo.authentication_devices.length > 0 && (
            <>
              <Divider />
              <Box>
                <Typography variant="subtitle1" gutterBottom sx={{ display: "flex", alignItems: "center", gap: 1 }}>
                  <DevicesIcon color="primary" />
                  登録済みパスキー ({authenticationDevices.length})
                </Typography>

                {deletedDeviceIds.size > 0 && (
                  <Alert severity="info" sx={{ mb: 2 }}>
                    パスキーを削除しました。
                    <Button size="small" onClick={fetchUserinfo} sx={{ ml: 1 }}>
                      最新情報を取得
                    </Button>
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
                            {device.app_name || `パスキー #${index + 1}`}
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
                    すべてのパスキーが削除されました。
                    <Button size="small" onClick={fetchUserinfo} sx={{ ml: 1 }}>
                      最新情報を取得
                    </Button>
                  </Alert>
                )}
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
                  {deviceToDelete.app_name || "パスキー"}
                </Typography>
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
          {deleteError && (
            <Alert severity="error" sx={{ mt: 2 }}>
              {deleteError}
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
