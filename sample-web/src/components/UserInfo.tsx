"use client";

import {
  Box,
  Card,
  CardContent,
  Chip,
  Divider,
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

interface UserInfoProps {
  session: Session;
}

const UserInfo = ({ session }: UserInfoProps) => {
  const { user } = session;

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
                  登録済みパスキー ({idTokenClaims.authentication_devices.length})
                </Typography>
                <Stack spacing={2}>
                  {idTokenClaims.authentication_devices.map((device: {
                    id: string;
                    app_name?: string;
                    platform?: string;
                    os?: string;
                    model?: string;
                    available_methods?: string[];
                    priority?: number;
                  }, index: number) => (
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
                      <Stack direction="row" alignItems="center" spacing={1} sx={{ mb: 1 }}>
                        <KeyIcon color="action" fontSize="small" />
                        <Typography variant="body2" fontWeight="bold">
                          パスキー #{index + 1}
                        </Typography>
                        {device.available_methods?.includes("fido2") && (
                          <Chip label="FIDO2" size="small" color="primary" />
                        )}
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
    </Card>
  );
};

export default UserInfo;
