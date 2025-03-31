"use client";

import {
  Box,
  Container,
  Paper,
  Stack,
  Typography,
  useTheme,
  alpha,
} from "@mui/material";
import { useRouter } from "next/router";
import ErrorOutlineRoundedIcon from "@mui/icons-material/ErrorOutlineRounded";
import { Loading } from "@/components/Loading";

export default function AuthorizationError() {
  const router = useRouter();
  const theme = useTheme();

  if (!router.isReady) {
    return <Loading />
  }

  const { error, error_description: errorDescription } = router.query;

  return (
    <Container maxWidth="xs">
      <Paper
        elevation={0}
        sx={{
          borderRadius: 4,
          px: 5,
          py: 6,
          mt: 10,
          textAlign: "center",
          backgroundColor:
            theme.palette.mode === "light"
              ? "#fff"
              : alpha(theme.palette.background.paper, 0.1),
          border: `1px solid ${alpha(theme.palette.divider, 0.08)}`,
          boxShadow:
            theme.palette.mode === "light"
              ? "0 8px 24px rgba(0,0,0,0.04)"
              : "0 0 0 1px rgba(255,255,255,0.06)",
        }}
      >
        <Stack spacing={3} alignItems="center">
          <ErrorOutlineRoundedIcon
            color="error"
            sx={{ fontSize: 50 }}
          />

          <Typography variant="h5" fontWeight={700} color="error.main">
            Authorization Failed
          </Typography>

          <Typography variant="body2" color="text.secondary">
            {"We couldn't complete the authorization request."}
          </Typography>

          <Box width="100%" textAlign="left" mt={2}>
            <Typography
              variant="caption"
              color="text.secondary"
              fontWeight={600}
              gutterBottom
            >
              Error
            </Typography>
            <Typography
              variant="body2"
              sx={{
                backgroundColor:
                  theme.palette.mode === "light"
                    ? "grey.100"
                    : alpha(theme.palette.common.white, 0.05),
                px: 2,
                py: 1,
                borderRadius: 2,
                fontWeight: 500,
                wordBreak: "break-word",
              }}
            >
              {error || "Unknown Error"}
            </Typography>

            <Typography
              variant="caption"
              color="text.secondary"
              fontWeight={600}
              mt={2}
              gutterBottom
              display="block"
            >
              Description
            </Typography>
            <Typography
              variant="body2"
              sx={{
                backgroundColor:
                  theme.palette.mode === "light"
                    ? "grey.50"
                    : alpha(theme.palette.common.white, 0.03),
                px: 2,
                py: 1,
                borderRadius: 2,
                wordBreak: "break-word",
              }}
            >
              {errorDescription || "No description provided."}
            </Typography>
          </Box>

        </Stack>
      </Paper>
    </Container>
  );
}
