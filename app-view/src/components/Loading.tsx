"use client";

import { Backdrop, CircularProgress, Typography } from "@mui/material";
import { useEffect } from "react";
import { sleep } from "@/functions/sleep";

export const Loading = () => {
  useEffect(() => {
    const wait = async () => {
      await sleep(500);
    };
    wait();
  }, []);

  return (
    <Backdrop
      open={true}
      sx={{
        zIndex: (theme) => theme.zIndex.drawer + 2,
        backgroundColor: "rgba(255, 255, 255, 0.75)",
        color: "#1c1c1e",
        flexDirection: "column",
      }}
    >
      <CircularProgress
        size={48}
        thickness={4}
        sx={{
          color: "#007aff",
          mb: 2,
        }}
      />
      <Typography
        variant="body2"
        sx={{
          fontWeight: 500,
          letterSpacing: 0.5,
          color: "#1c1c1e",
        }}
      >
        Loading, please wait...
      </Typography>
    </Backdrop>
  );
};
