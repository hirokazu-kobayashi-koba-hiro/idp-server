"use client";

import { useState } from "react";
import { Alert, Box, Button, Stack, Typography } from "@mui/material";
import CloudSyncIcon from "@mui/icons-material/CloudSync";

/**
 * 保管された外部トークンで外部APIを呼ぶボタン。
 *
 * トークン自体はサーバー側のルートで使い切られ、ここには結果だけが返る。
 */
export const CallExternalApi = ({ alias }: { alias: string }) => {
  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState<{ ok: boolean; body: unknown } | null>(null);

  const call = async () => {
    setLoading(true);
    try {
      const response = await fetch(`/api/linked-accounts/${encodeURIComponent(alias)}/call`, {
        cache: "no-store",
      });
      setResult({ ok: response.ok, body: await response.json() });
    } catch (error) {
      setResult({ ok: false, body: { error: String(error) } });
    } finally {
      setLoading(false);
    }
  };

  return (
    <Stack spacing={1}>
      <Button
        onClick={call}
        disabled={loading}
        variant="outlined"
        size="small"
        startIcon={<CloudSyncIcon />}
        sx={{ alignSelf: "flex-start" }}
      >
        {loading ? "呼び出し中…" : "保管トークンで外部APIを呼ぶ"}
      </Button>
      {result && (
        <Alert severity={result.ok ? "success" : "error"}>
          <Typography variant="caption" component="div">
            {result.ok ? "外部APIの呼び出しに成功しました" : "呼び出しに失敗しました"}
          </Typography>
          <Box component="pre" sx={{ m: 0, fontSize: 12, whiteSpace: "pre-wrap" }}>
            {JSON.stringify(result.body, null, 2)}
          </Box>
        </Alert>
      )}
    </Stack>
  );
};
