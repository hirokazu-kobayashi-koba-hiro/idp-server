import { Button, Container, Stack, Typography, Grid } from "@mui/material";
import { auth } from "@/app/auth";
import { redirect } from "next/navigation";
import UserInfo from "@/components/UserInfo";
import TokenViewer from "@/components/TokenViewer";
import PasswordChange from "@/components/PasswordChange";
import UserDelete from "@/components/UserDelete";
import LogoutIcon from "@mui/icons-material/Logout";

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
          <form
            action={async () => {
              "use server";
              const { signOut } = await import("@/app/auth");
              await signOut();
            }}
          >
            <Button
              type="submit"
              variant="outlined"
              color="error"
              startIcon={<LogoutIcon />}
            >
              ログアウト
            </Button>
          </form>
        </Stack>

        {/* User Info */}
        <UserInfo session={session} />

        {/* Token Viewer */}
        <TokenViewer session={session} />

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