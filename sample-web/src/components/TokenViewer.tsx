"use client";

import {
  Accordion,
  AccordionDetails,
  AccordionSummary,
  Box,
  Card,
  CardContent,
  Chip,
  IconButton,
  Stack,
  Typography,
} from "@mui/material";
import { Session } from "next-auth";
import ExpandMoreIcon from "@mui/icons-material/ExpandMore";
import ContentCopyIcon from "@mui/icons-material/ContentCopy";
import CodeIcon from "@mui/icons-material/Code";
import { useState } from "react";

interface TokenViewerProps {
  session: Session;
}

const TokenViewer = ({ session }: TokenViewerProps) => {
  const [copySuccess, setCopySuccess] = useState<string | null>(null);

  const copyToClipboard = (text: string, label: string) => {
    navigator.clipboard.writeText(text);
    setCopySuccess(label);
    setTimeout(() => setCopySuccess(null), 2000);
  };

  const decodeJwt = (token: string | undefined) => {
    if (!token) return null;
    try {
      const parts = token.split(".");
      if (parts.length !== 3) return null;

      const header = JSON.parse(atob(parts[0]));
      const payload = JSON.parse(atob(parts[1]));

      return {
        header,
        payload,
        signature: parts[2],
      };
    } catch (error) {
      console.error("Failed to decode JWT", error);
      return null;
    }
  };

  const TokenSection = ({
    title,
    token,
    label,
  }: {
    title: string;
    token: string | undefined;
    label: string;
  }) => {
    const decoded = decodeJwt(token);

    return (
      <Accordion>
        <AccordionSummary expandIcon={<ExpandMoreIcon />}>
          <Box sx={{ display: "flex", alignItems: "center", gap: 1, width: "100%" }}>
            <CodeIcon color="primary" />
            <Typography variant="subtitle1">{title}</Typography>
            {token && (
              <Chip
                label={copySuccess === label ? "Copied!" : "Available"}
                size="small"
                color={copySuccess === label ? "success" : "default"}
              />
            )}
          </Box>
        </AccordionSummary>
        <AccordionDetails>
          {token ? (
            <Stack spacing={2}>
              {/* Raw Token */}
              <Box>
                <Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center", mb: 1 }}>
                  <Typography variant="caption" color="text.secondary">
                    Raw Token
                  </Typography>
                  <IconButton
                    size="small"
                    onClick={() => copyToClipboard(token, label)}
                  >
                    <ContentCopyIcon fontSize="small" />
                  </IconButton>
                </Box>
                <Box
                  component="pre"
                  sx={{
                    backgroundColor: "grey.100",
                    p: 2,
                    borderRadius: 1,
                    overflow: "auto",
                    fontSize: "0.7rem",
                    fontFamily: "monospace",
                    wordBreak: "break-all",
                    whiteSpace: "pre-wrap",
                  }}
                >
                  {token}
                </Box>
              </Box>

              {/* Decoded Token */}
              {decoded && (
                <>
                  <Box>
                    <Typography variant="caption" color="text.secondary" gutterBottom>
                      Header
                    </Typography>
                    <Box
                      component="pre"
                      sx={{
                        backgroundColor: "grey.100",
                        p: 2,
                        borderRadius: 1,
                        overflow: "auto",
                        fontSize: "0.75rem",
                        fontFamily: "monospace",
                      }}
                    >
                      {JSON.stringify(decoded.header, null, 2)}
                    </Box>
                  </Box>

                  <Box>
                    <Typography variant="caption" color="text.secondary" gutterBottom>
                      Payload
                    </Typography>
                    <Box
                      component="pre"
                      sx={{
                        backgroundColor: "grey.100",
                        p: 2,
                        borderRadius: 1,
                        overflow: "auto",
                        fontSize: "0.75rem",
                        fontFamily: "monospace",
                        maxHeight: "400px",
                      }}
                    >
                      {JSON.stringify(decoded.payload, null, 2)}
                    </Box>
                  </Box>

                  <Box>
                    <Typography variant="caption" color="text.secondary" gutterBottom>
                      Signature
                    </Typography>
                    <Typography
                      variant="body2"
                      sx={{
                        backgroundColor: "grey.100",
                        p: 2,
                        borderRadius: 1,
                        fontFamily: "monospace",
                        wordBreak: "break-all",
                      }}
                    >
                      {decoded.signature}
                    </Typography>
                  </Box>
                </>
              )}
            </Stack>
          ) : (
            <Typography variant="body2" color="text.secondary">
              Token not available
            </Typography>
          )}
        </AccordionDetails>
      </Accordion>
    );
  };

  return (
    <Card>
      <CardContent>
        <Stack spacing={2}>
          <Box>
            <Typography variant="h5" gutterBottom sx={{ display: "flex", alignItems: "center", gap: 1 }}>
              <CodeIcon color="primary" />
              トークン情報（開発者向け）
            </Typography>
            <Typography variant="body2" color="text.secondary">
              OAuth 2.0 Tokens - Access Token, Refresh Token, ID Token
            </Typography>
          </Box>

          <TokenSection
            title="Access Token"
            token={session.accessToken}
            label="access_token"
          />

          <TokenSection
            title="Refresh Token"
            token={session.refreshToken}
            label="refresh_token"
          />

          <TokenSection
            title="ID Token"
            token={session.idToken}
            label="id_token"
          />
        </Stack>
      </CardContent>
    </Card>
  );
};

export default TokenViewer;
