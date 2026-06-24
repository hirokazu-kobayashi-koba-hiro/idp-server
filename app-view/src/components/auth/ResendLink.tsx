"use client";

import { useEffect, useState } from "react";
import { Link, Typography } from "@mui/material";

type Props = {
  onResend: () => void | Promise<unknown>;
  cooldownSeconds?: number;
};

/**
 * Subtle "didn't get a code? resend" affordance with a cooldown.
 *
 * Starts in cooldown (a code was just sent), counts down to a clickable "Resend" link, and
 * re-arms the cooldown after each resend to prevent spamming.
 */
export const ResendLink = ({ onResend, cooldownSeconds = 30 }: Props) => {
  const [seconds, setSeconds] = useState(cooldownSeconds);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (seconds <= 0) return;
    const timer = setTimeout(() => setSeconds((s) => s - 1), 1000);
    return () => clearTimeout(timer);
  }, [seconds]);

  const handleResend = async () => {
    if (seconds > 0 || busy) return;
    setBusy(true);
    try {
      await onResend();
      setSeconds(cooldownSeconds);
    } finally {
      setBusy(false);
    }
  };

  return (
    <Typography variant="body2" color="text.secondary" textAlign="center">
      Didn&apos;t get a code?{" "}
      {busy ? (
        "Sending…"
      ) : seconds > 0 ? (
        `Resend in ${seconds}s`
      ) : (
        <Link
          component="button"
          type="button"
          underline="hover"
          onClick={handleResend}
        >
          Resend
        </Link>
      )}
    </Typography>
  );
};
