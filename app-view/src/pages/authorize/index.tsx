import {
  Avatar,
  Box,
  Button,
  Card,
  Chip,
  Container,
  List,
  ListItem,
  ListItemIcon,
  ListItemText,
  Stack,
  Typography
} from "@mui/material";
import { useRouter } from "next/router";
import { useQuery } from "@tanstack/react-query";
import { backendUrl } from "@/pages/_app";
import PolicyIcon from "@mui/icons-material/Policy";
import InfoIcon from "@mui/icons-material/Info";
import { Loading } from "@/components/Loading";

export default function Authorize() {

  const router = useRouter();
  const { id, tenant_id: tenantId } = router.query;
  const {data, isPending } = useQuery({
    queryKey: ["fetchViewData"],
    queryFn: async () => {
      const { id, tenant_id: tenantId } = router.query;
      const response = await fetch(`${backendUrl}/${tenantId}/api/v1/authorizations/${id}/view-data`, {
        credentials: "include",
      })
      if (!response.ok) {
        console.error(response)
        throw new Error(response.status.toString());
      }
      return await response.json()
    },
  })

  const handleCancel = async () => {
    const response = await fetch(`${backendUrl}/${tenantId}/api/v1/authorizations/${id}/deny`, {
      method: "POST",
      credentials: "include",
      headers: {
        "Content-Type": "application/json"
      },
    })
    const body = await response.json()
    console.log(response.status, body)
    if (body.redirect_uri) {
      window.location.href = body.redirect_uri;
    }
  }

  const handleApprove = async () => {
    const response = await fetch(`${backendUrl}/${tenantId}/api/v1/authorizations/${id}/authorize`, {
      method: "POST",
      credentials: "include",
      headers: {
        "Content-Type": "application/json"
      },
    })
    const body = await response.json()
    console.log(response.status, body)
    if (body.redirect_uri) {
      window.location.href = body.redirect_uri;
    }
  }


  if (isPending) return <Loading />
  if (!data) return  <Loading />

  return (
    <>
      <Container maxWidth={"xs"}>
        <Box sx={{ mt: 6, textAlign: "center" }}>
          <Card variant="outlined" sx={{ p: 3, boxShadow: 3 }}>
            <Avatar src={data.logo_uri} sx={{ width: 80, height: 80, mx: "auto", mb: 2 }} />
            <Typography variant="h5">{data.client_name}</Typography>

            <Typography variant="h6" sx={{ mt: 4 }}>
              request scope
            </Typography>

            <Stack direction="row" spacing={1} justifyContent="center" sx={{ mt: 2 }}>
              {data.scopes.map((scope: string) => (
                <Chip key={scope} label={scope} color="primary" variant="outlined" />
              ))}
            </Stack>

            <List sx={{ mt: 2 }}>
              <ListItem>
                <ListItemIcon>
                  <PolicyIcon color="action" />
                </ListItemIcon>
                <ListItemText
                  primary={
                    <a href={data.tos_uri} target="_blank" rel="noopener noreferrer">
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
                    <a href={data.policy_uri} target="_blank" rel="noopener noreferrer">
                      privacy policy
                    </a>
                  }
                />
              </ListItem>
            </List>

            <Box sx={{ display: "flex", justifyContent: "space-between", mt: 2 }}>
              <Button variant="contained" color="error" onClick={handleCancel}>
                cancel
              </Button>
              <Button variant="contained" color="primary" onClick={handleApprove}>
                approve
              </Button>
            </Box>
          </Card>
        </Box>
      </Container>
    </>
  );
}
