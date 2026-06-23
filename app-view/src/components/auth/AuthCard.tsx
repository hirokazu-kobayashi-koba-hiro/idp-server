"use client";

import { ReactNode } from "react";
import { Avatar, Box, Paper, Stack, Typography } from "@mui/material";
import ShieldOutlinedIcon from "@mui/icons-material/ShieldOutlined";

const BrandMark = ({ logoUri }: { logoUri?: string }) => {
  // Prefer the client's own logo (view-data logo_uri); fall back to a neutral brand mark.
  if (logoUri) {
    return (
      <Avatar
        src={logoUri}
        variant="rounded"
        sx={{ width: 56, height: 56, bgcolor: "background.default", color: "text.secondary" }}
      >
        <ShieldOutlinedIcon />
      </Avatar>
    );
  }
  return (
    <Box
      sx={{
        width: 48,
        height: 48,
        borderRadius: 2.5,
        bgcolor: "primary.main",
        color: "primary.contrastText",
        display: "grid",
        placeItems: "center",
        boxShadow: "0 4px 12px rgba(99,91,255,0.35)",
      }}
    >
      <ShieldOutlinedIcon />
    </Box>
  );
};

type Props = {
  title?: string;
  subtitle?: string;
  logoUri?: string;
  children: ReactNode;
};

/**
 * Shared authentication screen frame: a vertically centered, softly elevated card with a branded
 * header. Every auth screen renders through this so spacing, elevation and the header stay
 * consistent in one place.
 */
export const AuthCard = ({ title, subtitle, logoUri, children }: Props) => (
  <Box
    sx={{
      minHeight: "100vh",
      display: "flex",
      alignItems: "center",
      justifyContent: "center",
      bgcolor: "background.default",
      px: 2,
      py: 6,
    }}
  >
    <Paper
      elevation={0}
      sx={{
        width: "100%",
        maxWidth: 420,
        p: { xs: 3, sm: 5 },
        borderRadius: 3,
        border: "1px solid",
        borderColor: "divider",
        boxShadow:
          "0 8px 24px rgba(60,66,87,0.08), 0 2px 6px rgba(0,0,0,0.04)",
      }}
    >
      <Stack spacing={3.5}>
        <Stack spacing={1.5} alignItems="center" textAlign="center">
          <BrandMark logoUri={logoUri} />
          {title && (
            <Typography variant="h5" component="h1">
              {title}
            </Typography>
          )}
          {subtitle && (
            <Typography variant="body2" color="text.secondary">
              {subtitle}
            </Typography>
          )}
        </Stack>
        {children}
      </Stack>
    </Paper>
  </Box>
);
