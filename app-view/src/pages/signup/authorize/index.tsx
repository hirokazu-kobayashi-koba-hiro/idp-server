import {
  Avatar,
  Box,
  Button,
  Chip,
  Container,
  List,
  ListItem,
  ListItemIcon,
  ListItemText,
  Paper,
  Stack,
  Typography,
} from "@mui/material";
import { useRouter } from "next/router";
import { useQuery } from "@tanstack/react-query";
import { backendUrl } from "@/pages/_app";
import PolicyIcon from "@mui/icons-material/Policy";
import InfoIcon from "@mui/icons-material/Info";
import { Loading } from "@/components/Loading";
import { SignupStepper } from "@/components/SignupStepper";

export default function Authorize() {
  const router = useRouter();
  const { id, tenant_id: tenantId } = router.query;
  const { data, isPending } = useQuery({
    queryKey: ["fetchViewData"],
    queryFn: async () => {
      const { id, tenant_id: tenantId } = router.query;
      const response = await fetch(
        `${backendUrl}/${tenantId}/api/v1/authorizations/${id}/view-data`,
        {
          credentials: "include",
        },
      );
      if (!response.ok) {
        console.error(response);
        throw new Error(response.status.toString());
      }
      return await response.json();
    },
  });

  const handleCancel = async () => {
    const response = await fetch(
      `${backendUrl}/${tenantId}/api/v1/authorizations/${id}/deny`,
      {
        method: "POST",
        credentials: "include",
        headers: {
          "Content-Type": "application/json",
        },
      },
    );
    const body = await response.json();
    console.log(response.status, body);
    if (body.redirect_uri) {
      window.location.href = body.redirect_uri;
    }
  };

  const handleApprove = async () => {
    const response = await fetch(
      `${backendUrl}/${tenantId}/api/v1/authorizations/${id}/authorize`,
      {
        method: "POST",
        credentials: "include",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          action: "signup",
        }),
      },
    );
    const body = await response.json();
    console.log(response.status, body);
    if (body.redirect_uri) {
      window.location.href = body.redirect_uri;
    }
  };

  if (isPending) return <Loading />;
  if (!data) return <Loading />;

  return (
    <>
      <Container maxWidth={"sm"}>
        <Paper sx={{ p: 3, boxShadow: 3 }}>
          <Stack spacing={4}>
            <Typography variant={"h5"}>Sign Up</Typography>

            <SignupStepper activeStep={2} />

            <Box display={"flex"} gap={4} alignItems={"center"}>
              <Avatar src={data.logo_uri} sx={{ width: 80, height: 80 }} />
              <Typography variant="h5">{data.client_name}</Typography>
            </Box>

            <Typography variant="h6">request scope</Typography>

            <Stack
              direction="row"
              spacing={1}
              justifyContent="center"
              sx={{ mt: 2 }}
            >
              {data.scopes.map((scope: string) => (
                <Chip
                  key={scope}
                  label={scope}
                  color="primary"
                  variant="outlined"
                />
              ))}
            </Stack>

            <List sx={{ p: 0 }}>
              <ListItem>
                <ListItemIcon>
                  <PolicyIcon color="action" />
                </ListItemIcon>
                <ListItemText
                  primary={
                    <a
                      href={data.tos_uri}
                      target="_blank"
                      rel="noopener noreferrer"
                    >
                      term of use
                    </a>
                  }
                />
              </ListItem>

              <ListItem>
                <ListItemIcon>
                  <InfoIcon color="action" />
                </ListItemIcon>
                <ListItemText
                  primary={
                    <a
                      href={data.policy_uri}
                      target="_blank"
                      rel="noopener noreferrer"
                    >
                      privacy policy
                    </a>
                  }
                />
              </ListItem>
            </List>

            <Box sx={{ display: "flex", justifyContent: "space-between" }}>
              <Button
                variant="contained"
                color="error"
                onClick={handleCancel}
                sx={{ textTransform: "none" }}
              >
                Cancel
              </Button>
              <Button
                variant="contained"
                color="primary"
                onClick={handleApprove}
                sx={{ textTransform: "none" }}
              >
                Approve
              </Button>
            </Box>
          </Stack>
        </Paper>
      </Container>
    </>
  );
}
