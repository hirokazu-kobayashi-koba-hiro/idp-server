import { Button, Stack, Typography } from "@mui/material";
import Image from "next/image";
import { backendUrl } from "@/pages/_app";
import { useState } from "react";
import { useAtom } from "jotai";
import { authSessionIdAtom, authSessionTenantIdAtom } from "@/state/AuthState";

type IdpConfig = {
  type: string;
  name: string;
  label: string;
  logo: string;
};

type IdpConfigs = IdpConfig[];

//TODO getting backend
const idpConfigs = [
  {
    type: "oidc",
    name: "google",
    label: "Google",
    logo: "/logos/google.svg",
  },
  // {
  //   type: "oidc",
  //   name: "facebook",
  //   label: "Facebook",
  //   logo: "https://upload.wikimedia.org/wikipedia/commons/5/51/Facebook_f_logo_%282019%29.svg",
  // },
  // {
  //   type: "oidc",
  //   name: "yahoo",
  //   label: "Yahoo! JAPAN ID",
  //   logo: "/logos/yahoo_japan_icon_64.png",
  // },
] as IdpConfigs;

export const SsoComponent = () => {
  const [authSessionId] = useAtom(authSessionIdAtom);
  const [authSessionTenantId] = useAtom(authSessionTenantIdAtom);
  const [message, setMessage] = useState("");

  const handleClick = async (type: string, name: string) => {
    const response = await fetch(
      `${backendUrl}/${authSessionTenantId}/v1/authorizations/${authSessionId}/federations/${type}/${name}`,
      {
        method: "POST",
        credentials: "include",
        headers: { "Content-Type": "application/json" },
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
      {idpConfigs.map(({ type, name, label, logo }, index) => (
        <Button
          key={index}
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
          onClick={() => handleClick(type, name)}
        >
          <Image
            src={logo}
            alt={`${name} logo`}
            width={24}
            height={24}
            style={{ marginRight: 8 }}
          />
          Sign in with {label}
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
