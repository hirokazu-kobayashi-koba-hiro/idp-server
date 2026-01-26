import { Container, Stack, Typography, Grid, Button, Box } from "@mui/material";
import { auth } from "@/app/auth";
import { redirect } from "next/navigation";
import UserInfo from "@/components/UserInfo";
import TokenViewer from "@/components/TokenViewer";
import UserinfoViewer from "@/components/UserinfoViewer";
import PasswordChange from "@/components/PasswordChange";
import UserDelete from "@/components/UserDelete";
import LogoutButton from "@/components/LogoutButton";
import SecurityIcon from "@mui/icons-material/Security";
import FingerprintIcon from "@mui/icons-material/Fingerprint";
import AddIcon from "@mui/icons-material/Add";
import PasskeyRegistrationStatus from "@/components/PasskeyRegistrationStatus";

const Home = async () => {
  const session = await auth();

  if (!session) {
    redirect("/");
  }

  return (
    <Container maxWidth="lg" sx={{ py: 4 }}>
      <Stack spacing={4}>
        {/* Header */}
        <Stack direction="row" justifyContent="space-between" alignItems="center">
          <Typography variant="h4" component="h1">
            ダッシュボード
          </Typography>
          <Box sx={{ display: "flex", gap: 2 }}>
            <Button
              href="/api/passkey-registration"
              variant="contained"
              color="success"
              startIcon={<AddIcon />}
              size="small"
            >
              パスキー追加
            </Button>
            <Button
              href="/fido2-attestation-demo"
              variant="outlined"
              color="primary"
              startIcon={<FingerprintIcon />}
              size="small"
            >
              FIDO2 登録デモ
            </Button>
            <Button
              href="/fido2-rpid-demo"
              variant="outlined"
              color="info"
              startIcon={<FingerprintIcon />}
              size="small"
            >
              FIDO2 rpIdデモ
            </Button>
            <Button
              href="/security-demo"
              variant="outlined"
              color="warning"
              startIcon={<SecurityIcon />}
              size="small"
            >
              セキュリティデモ
            </Button>
            <LogoutButton idToken={session.idToken} />
          </Box>
        </Stack>

        {/* Passkey Registration Status */}
        <PasskeyRegistrationStatus />

        {/* User Info */}
        <UserInfo session={session} />

        {/* Token Viewer */}
        <TokenViewer session={session} />

        {/* Userinfo API Response */}
        <UserinfoViewer accessToken={session.accessToken} />

        {/* Account Settings */}
        <Grid container spacing={3}>
          <Grid item xs={12} md={6}>
            <PasswordChange />
          </Grid>
          <Grid item xs={12} md={6}>
            <UserDelete />
          </Grid>
        </Grid>
      </Stack>
    </Container>
  );
};

export default Home;