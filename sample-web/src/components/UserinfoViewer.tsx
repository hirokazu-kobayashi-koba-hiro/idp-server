"use client";

import { useState, useEffect } from "react";
import {
  Card,
  CardContent,
  Typography,
  Box,
  CircularProgress,
  Alert,
  IconButton,
  Tooltip,
} from "@mui/material";
import RefreshIcon from "@mui/icons-material/Refresh";
import ContentCopyIcon from "@mui/icons-material/ContentCopy";

interface UserinfoViewerProps {
  accessToken?: string;
}

export default function UserinfoViewer({ accessToken }: UserinfoViewerProps) {
  const [userinfo, setUserinfo] = useState<Record<string, unknown> | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [copied, setCopied] = useState(false);

  const fetchUserinfo = async () => {
    if (!accessToken) {
      setError("Access token is not available");
      return;
    }

    setLoading(true);
    setError(null);

    try {
      const response = await fetch("/api/userinfo", {
        method: "GET",
        headers: {
          Authorization: `Bearer ${accessToken}`,
        },
      });

      if (!response.ok) {
        const errorText = await response.text();
        throw new Error(`Failed to fetch userinfo: ${response.status} ${errorText}`);
      }

      const data = await response.json();
      setUserinfo(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Unknown error occurred");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchUserinfo();
  }, [accessToken]);

  const handleCopy = () => {
    if (userinfo) {
      navigator.clipboard.writeText(JSON.stringify(userinfo, null, 2));
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    }
  };

  return (
    <Card>
      <CardContent>
        <Box display="flex" justifyContent="space-between" alignItems="center" mb={2}>
          <Typography variant="h6" component="h2">
            Userinfo API Response
          </Typography>
          <Box>
            <Tooltip title={copied ? "Copied!" : "Copy JSON"}>
              <IconButton onClick={handleCopy} disabled={!userinfo} size="small">
                <ContentCopyIcon fontSize="small" />
              </IconButton>
            </Tooltip>
            <Tooltip title="Refresh">
              <IconButton onClick={fetchUserinfo} disabled={loading} size="small">
                <RefreshIcon fontSize="small" />
              </IconButton>
            </Tooltip>
          </Box>
        </Box>

        {loading && (
          <Box display="flex" justifyContent="center" py={3}>
            <CircularProgress size={24} />
          </Box>
        )}

        {error && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {error}
          </Alert>
        )}

        {userinfo && !loading && (
          <Box
            component="pre"
            sx={{
              backgroundColor: "#f5f5f5",
              p: 2,
              borderRadius: 1,
              overflow: "auto",
              maxHeight: 400,
              fontSize: "0.875rem",
              fontFamily: "monospace",
            }}
          >
            {JSON.stringify(userinfo, null, 2)}
          </Box>
        )}
      </CardContent>
    </Card>
  );
}
