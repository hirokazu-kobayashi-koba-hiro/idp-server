import { Alert } from "@mui/material";

type Props = {
  message?: string;
  severity?: "error" | "success" | "info" | "warning";
};

/**
 * Inline alert for step feedback. Uses `role="alert"` so screen readers announce errors when they
 * appear, and renders nothing when there is no message.
 */
export const AuthAlert = ({ message, severity = "error" }: Props) => {
  if (!message) return null;
  return (
    <Alert
      severity={severity}
      variant="outlined"
      role="alert"
      sx={{ borderRadius: 2, alignItems: "center", py: 0.5 }}
    >
      {message}
    </Alert>
  );
};
