import {
  Button,
  Divider,
  InputAdornment,
  Link,
  Stack,
  TextField,
  Typography,
} from "@mui/material";
import { useEffect, useState } from "react";
import { useRouter } from "next/router";
import { useQuery } from "@tanstack/react-query";
import { backendUrl } from "@/pages/_app";
import { Loading } from "@/components/Loading";
import { Email, Lock } from "@mui/icons-material";
import { useMediaQuery, useTheme } from "@mui/material";
import { BaseLayout } from "@/components/layout/BaseLayout";
import { getUserAgentData, parseUserAgent } from "@/functions/device";
import { SsoComponent } from "@/components/sso/SsoComponent";
import { useAtom } from "jotai/index";
import { authSessionIdAtom, authSessionTenantIdAtom } from "@/state/AuthState";

export default function SignIn() {
  const [, setAuthSessionId] = useAtom(authSessionIdAtom);
  const [, setAuthSessionTenantId] = useAtom(authSessionTenantIdAtom);
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const router = useRouter();
  const { id, tenant_id: tenantId } = router.query;
  const theme = useTheme();
  const isMobile = useMediaQuery(theme.breakpoints.down("sm"));

  const { data, isPending } = useQuery({
    queryKey: ["fetchViewData", router.query],
    queryFn: async () => {
      if (!router.isReady || Object.keys(router.query).length === 0) return; // Ensure query params exist
      const { id, tenant_id: tenantId } = router.query;
      if (typeof id === "string") {
        setAuthSessionId(id);
      }
      if (typeof tenantId === "string") {
        setAuthSessionTenantId(tenantId);
      }

      const ua = await getUserAgentData(navigator);
      const parsedUA = parseUserAgent(navigator.userAgent);
      console.log(ua, parsedUA);

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

  const handleNext = async () => {
    const response = await fetch(
      `${backendUrl}/${tenantId}/api/v1/authorizations/${id}/password-authentication`,
      {
        method: "POST",
        credentials: "include",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          username: email,
          password: password,
        }),
      },
    );
    if (response.ok) {
      router.push(`/signin/authorize?id=${id}&tenant_id=${tenantId}`);
    }
  };

  useEffect(() => {
    const execute = async () => {
      const response = await fetch(
        `${backendUrl}/${tenantId}/api/v1/authorizations/${id}/authorize-with-session`,
        {
          method: "POST",
          credentials: "include",
          headers: {
            "Content-Type": "application/json",
          },
        },
      );
      if (!response.ok) {
        console.error(response);
        return;
      }
      const body = await response.json();
      console.log(response.status, body);
      if (body.redirect_uri) {
        window.location.href = body.redirect_uri;
      }
    };
    console.log(data);
    if (data && data.session_enabled === true) {
      execute();
    }
  }, [data]);

  if (isPending) return <Loading />;
  if (!data) return <Loading />;
  if (data && data.session_enabled) return <Loading />;

  return (
    <BaseLayout>
      <Stack spacing={isMobile ? 2 : 3} alignItems="center">
        <Typography variant="subtitle1" fontWeight="medium">
          IdP Server
        </Typography>
        <Stack spacing={2} width="100%">
          <TextField
            placeholder="Your email"
            inputMode="email"
            fullWidth
            required
            // variant="standard"
            InputProps={{
              disableUnderline: true,
              sx: {
                height: 48,
                fontSize: 17,
                px: 2,
                backgroundColor: "transparent",
              },
              startAdornment: (
                <InputAdornment position="start">
                  <Email sx={{ color: "gray" }} />
                </InputAdornment>
              ),
            }}
            onChange={(e) => setEmail(e.target.value)}
          />
          <TextField
            placeholder="Your password"
            type="password"
            fullWidth
            required
            // variant="standard"
            InputProps={{
              disableUnderline: true,
              sx: {
                height: 48,
                fontSize: 17,
                px: 2,
                backgroundColor: "transparent",
              },
              startAdornment: (
                <InputAdornment position="start">
                  <Lock sx={{ color: "gray" }} />
                </InputAdornment>
              ),
            }}
            onChange={(e) => {
              navigator.credentials.preventSilentAccess();
              setPassword(e.target.value);
            }}
          />
        </Stack>
        <Stack spacing={1} width="100%">
          <Button
            variant="contained"
            disabled={!email || !password}
            onClick={handleNext}
            fullWidth
            sx={{
              textTransform: "none",
              borderRadius: 8,
              height: 44,
              fontSize: 16,
              fontWeight: "bold",
              backgroundColor: "#007AFF",
              boxShadow: "0px 2px 5px rgba(0, 0, 0, 0.1)",
              "&:hover": { opacity: 0.8 },
            }}
          >
            Next
          </Button>
          {data.show_cancel && (
            <Button
              variant="outlined"
              onClick={handleCancel}
              sx={{
                textTransform: "none",
                fontSize: 16,
                fontWeight: "medium",
                color: "#505050",
                borderColor: "rgba(0, 0, 0, 0.2)",
              }}
            >
              Cancel
            </Button>
          )}
        </Stack>

        <Stack
          spacing={1}
          direction="row"
          justifyContent="center"
          sx={{ mt: 3 }}
        >
          <Typography variant="body2">{"Don't have an account?"}</Typography>
          <Link
            onClick={() =>
              router.push(`/signup?id=${id}&tenant_id=${tenantId}`)
            }
            sx={{
              fontWeight: "bold",
              cursor: "pointer",
              color: "primary.main",
              fontSize: 16,
            }}
          >
            Sign Up
          </Link>
        </Stack>

        <Stack spacing={2} sx={{ width: "100%" }}>
          <Divider variant={"fullWidth"} />
          <SsoComponent />
        </Stack>

        <Typography
          variant="caption"
          color="text.secondary"
          textAlign="center"
          sx={{ mt: 2 }}
        >
          By signing in, you agree to our
          <Link href={data?.tos_uri} sx={{ fontWeight: "bold", mx: 0.5 }}>
            Terms of Use
          </Link>
          and
          <Link href={data?.policy_uri} sx={{ fontWeight: "bold", mx: 0.5 }}>
            Privacy Policy
          </Link>
        </Typography>
      </Stack>
    </BaseLayout>
  );
}
