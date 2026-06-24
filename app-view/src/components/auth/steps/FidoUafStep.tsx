"use client";

import { useEffect, useRef, useState } from "react";
import { Box, Button, CircularProgress, Stack, Typography } from "@mui/material";
import PhonelinkLockIcon from "@mui/icons-material/PhonelinkLock";
import { backendUrl } from "@/pages/_app";
import { readErrorMessage } from "@/auth/http";
import { AuthAlert } from "@/components/auth/AuthAlert";
import { StepProps } from "./StepProps";

/**
 * FIDO-UAF device approval step (method "fido-uaf").
 *
 * Sends a push challenge to the user's registered device, then polls the flow (via onCompleted →
 * status refetch) until the device approves out-of-band and the step advances. Polling stops on
 * unmount, which happens automatically once the flow moves past this step.
 */
export const FidoUafStep = ({ tenantId, id, onCompleted }: StepProps) => {
  const [waiting, setWaiting] = useState(false);
  const [message, setMessage] = useState("");
  const pollRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const stopPolling = () => {
    if (pollRef.current) {
      clearInterval(pollRef.current);
      pollRef.current = null;
    }
  };

  useEffect(() => stopPolling, []);

  const start = async () => {
    setMessage("");
    setWaiting(true);
    try {
      const response = await fetch(
        `${backendUrl}/${tenantId}/v1/authorizations/${id}/fido-uaf-authentication-challenge`,
        {
          method: "POST",
          credentials: "include",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({}),
        },
      );
      if (!response.ok) {
        setMessage(
          await readErrorMessage(
            response,
            "We couldn't reach your device. Please try again.",
          ),
        );
        setWaiting(false);
        return;
      }
      // Approval happens on the device; poll the flow until the status advances past this step.
      stopPolling();
      pollRef.current = setInterval(() => {
        void onCompleted();
      }, 2500);
    } catch {
      setMessage("We couldn't reach your device. Please try again.");
      setWaiting(false);
    }
  };

  const cancel = () => {
    stopPolling();
    setWaiting(false);
  };

  return (
    <Stack spacing={3} alignItems="center" textAlign="center">
      <Box sx={{ color: "primary.main" }}>
        <PhonelinkLockIcon sx={{ fontSize: 40 }} />
      </Box>
      <Typography variant="body2" color="text.secondary">
        {waiting
          ? "Approve the sign-in on your registered device…"
          : "We'll send a sign-in request to your registered device."}
      </Typography>
      {waiting && <CircularProgress size={28} />}
      <AuthAlert message={message} />
      {waiting ? (
        <Button variant="text" onClick={cancel} sx={{ textTransform: "none" }}>
          Cancel
        </Button>
      ) : (
        <Button
          variant="contained"
          fullWidth
          onClick={start}
          sx={{ textTransform: "none" }}
        >
          Send request
        </Button>
      )}
    </Stack>
  );
};
