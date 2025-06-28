import { Loading } from "@/components/Loading";
import { useRouter } from "next/router";
import { useQuery } from "@tanstack/react-query";
import { backendUrl } from "@/pages/_app";
import { Stack, Typography } from "@mui/material";
import { BaseLayout } from "@/components/layout/BaseLayout";
import { useState } from "react";

const decodeSsoState = (state: string) => {

  const base64 = state.replace(/-/g, '+').replace(/_/g, '/');
  const padded = base64.padEnd(base64.length + (4 - base64.length % 4) % 4, '=');

  const json = JSON.parse(atob(padded));
  const sessionId: string = json.session_id;
  const tenantId: string = json.tenant_id;
  const provider: string = json.provider;

  return { sessionId, tenantId, provider };
}

const SsoCallback = () => {
  const router = useRouter();
  const [message, setMessage] = useState("");

  const { isPending } = useQuery({
    queryKey: ["postFederationsCallback", router.query],
    queryFn: async () => {
      if (!router.isReady || Object.keys(router.query).length === 0) return; // Ensure query params exist
      const query = router.query;
      console.log(query);
      const params = new URLSearchParams(query as Record<string, string>);
      const state = params.get("state")
      if (!state) {
        console.error("state is null")
        return
      }
      const stateObject = decodeSsoState(state);

      const response = await fetch(
        `${backendUrl}/${stateObject.tenantId}/v1/authorizations/federations/oidc/callback`,
        {
          method: "POST",
          credentials: "include",
          headers: {
            "Content-Type": "application/x-www-form-urlencoded",
          },
          body: params.toString(),
        },
      );

      console.info(response);
      if (!response.ok) {
        console.error(response);
        throw new Error(response.status.toString());
      }

      const { id, tenant_id: tenantId } = await response.json();

      const authorizeResponse = await fetch(
        `${backendUrl}/${tenantId}/v1/authorizations/${id}/authorize`,
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
        return;
      }
      setMessage("failed social login. server occurred unexpected error");
      throw new Error("failed authorization");
    },
  });

  if (isPending) return <Loading />;

  return (
    <BaseLayout>
      {message ? (
        <Stack spacing={2}>
          <Typography variant={"subtitle1"}>Social Login is Failed</Typography>
          <Typography variant={"body1"} color={"error"}>
            {message}
          </Typography>
        </Stack>
      ) : (
        <Loading />
      )}
    </BaseLayout>
  );
};

export default SsoCallback;
