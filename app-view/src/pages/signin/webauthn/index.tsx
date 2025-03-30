import { useEffect, useState } from "react";
import { Typography, Button, Stack, Link, Divider, Box } from "@mui/material";
import { useRouter } from "next/router";
import { backendUrl } from "@/pages/_app";
import { BaseLayout } from "@/components/layout/BaseLayout";
import { useQuery } from "@tanstack/react-query";
import { Loading } from "@/components/Loading";
import KeyIcon from "@mui/icons-material/Key";
import FingerprintIcon from "@mui/icons-material/Fingerprint";
import { SsoComponent } from "@/components/sso/SsoComponent";
import { useAtom } from "jotai";
import { authSessionIdAtom, authSessionTenantIdAtom } from "@/state/AuthState";

export default function Login() {
  const [, setAuthSessionId] = useAtom(authSessionIdAtom);
  const [, setAuthSessionTenantId] = useAtom(authSessionTenantIdAtom);

  const router = useRouter();
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState("");
  const { id, tenant_id: tenantId } = router.query;

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

  const handleNext = async () => {
    setLoading(true);
    setMessage("");

    try {
      const res = await fetch(
        `${backendUrl}/${tenantId}/api/v1/authorizations/${id}/webauthn/authentication/challenge`,
        {
          credentials: "include",
        },
      );
      const { challenge } = await res.json();
      const decodedChallenge = challenge.replace(/-/g, "+").replace(/_/g, "/");

      const credential = await navigator.credentials.get({
        publicKey: {
          challenge: new Uint8Array(
            atob(decodedChallenge)
              .split("")
              .map((c) => c.charCodeAt(0)),
          ),
        },
      });

      const loginRes = await fetch(
        `${backendUrl}/${tenantId}/api/v1/authorizations/${id}/webauthn/authentication/response`,
        {
          method: "POST",
          credentials: "include",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(credential),
        },
      );

      if (loginRes.ok) {
        const authorizeResponse = await fetch(
          `${backendUrl}/${tenantId}/api/v1/authorizations/${id}/authorize`,
          {
            method: "POST",
            credentials: "include",
            headers: {
              "Content-Type": "application/json",
            },
            body: JSON.stringify({
              action: "signin",
            }),
          },
        );
        const body = await authorizeResponse.json();
        console.log(authorizeResponse.status, body);
        if (body.redirect_uri) {
          window.location.href = body.redirect_uri;
        }
        return;
      }

      setMessage("Login failed.");
    } catch (error) {
      console.error(error);
      setMessage("An error occurred during login.");
    }

    setLoading(false);
  };

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
      <Stack spacing={3} alignItems="center">
        <Typography variant="subtitle1" fontWeight="medium">
          IdP Server
        </Typography>
        <Stack spacing={3} width="100%">
          <Box display={"flex"} justifyContent={"center"} sx={{ gap: 2 }}>
            <KeyIcon />
            <FingerprintIcon />
          </Box>

          <Box>
            <Typography variant={"body1"}>
              Sign in quickly and securely using Passkey
            </Typography>
            <Typography variant={"body1"}>—no password required</Typography>
          </Box>

          <Stack spacing={1} width="100%">
            <Button
              variant="contained"
              disabled={loading}
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
              Signin With Passkey
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
          {message && <Typography>{message}</Typography>}
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
        </Stack>

        <Stack spacing={2} sx={{ width: "100%" }}>
          <Divider variant={"fullWidth"} />
          <SsoComponent />
        </Stack>

        <Typography variant="caption" color="text.secondary" sx={{ mt: 2 }}>
          By signing in, you agree to our
          <Link href={data.tos_uri} sx={{ fontWeight: "bold", mx: 0.5 }}>
            Terms of Use
          </Link>
          and
          <Link href={data.policy_uri} sx={{ fontWeight: "bold", mx: 0.5 }}>
            Privacy Policy
          </Link>
        </Typography>
      </Stack>
    </BaseLayout>
  );
}
