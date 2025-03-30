import { Button, Stack, Typography } from "@mui/material";
import Image from "next/image";
import { backendUrl } from "@/pages/_app";
import { useState } from "react";
import { useAtom } from "jotai";
import { authSessionIdAtom, authSessionTenantIdAtom } from "@/state/AuthState";

type IdpConfig = {
  id: string;
  name: string;
  logo: string;
};

type IdpConfigs = IdpConfig[];

//TODO getting backend
const idpConfigs = [
  {
    id: "1e68932e-ed4a-43e7-b412-460665e42df3",
    name: "Google",
    logo: "/logos/google.svg",
  },
  {
    id: "3be20da2-eea5-4420-9e1e-90215803b4a8",
    name: "Facebook",
    logo: "https://upload.wikimedia.org/wikipedia/commons/5/51/Facebook_f_logo_%282019%29.svg",
  },
  {
    id: "4cc97cad-34ad-41e7-af53-c4ddee3f786b",
    name: "Yahoo! JAPAN ID",
    logo: "/logos/yahoo_japan_icon_64.png",
  },
] as IdpConfigs;

export const SsoComponent = () => {
  const [authSessionId] = useAtom(authSessionIdAtom);
  const [authSessionTenantId] = useAtom(authSessionTenantIdAtom);
  const [message, setMessage] = useState("");

  const handleClick = async (idpId: string) => {
    const response = await fetch(
      `${backendUrl}/${authSessionTenantId}/api/v1/authorizations/${authSessionId}/federations`,
      {
        method: "POST",
        credentials: "include",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ federatable_idp_id: idpId }),
      },
    );

    if (response.ok) {
      const body = await response.json();
      if (body.redirect_uri) {
        window.location.href = body.redirect_uri;
        return;
      }
    }
    setMessage("Failed SSO. System error occurred");
  };

  return (
    <Stack spacing={1}>
      {idpConfigs.map(({ id, name, logo }) => (
        <Button
          key={id}
          variant="outlined"
          color={"inherit"}
          sx={{
            textTransform: "none",
            display: "flex",
            alignItems: "center",
            justifyContent: "flex-start",
            padding: "8px 16px",
            borderColor: "#9e9e9e", // Gray border
          }}
          fullWidth
          onClick={() => handleClick(id)}
        >
          <Image
            src={logo}
            alt={`${name} logo`}
            width={24}
            height={24}
            style={{ marginRight: 8 }}
          />
          Sign in with {name}
        </Button>
      ))}
      {message && (
        <Typography variant="caption" color="error">
          {message}
        </Typography>
      )}
    </Stack>
  );
};
