"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import {
  Alert,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogContentText,
  DialogTitle,
} from "@mui/material";
import LinkOffIcon from "@mui/icons-material/LinkOff";

/**
 * 連携を解除するボタン。
 *
 * 取り消せない操作なので、ブラウザ標準の confirm ではなくダイアログで確認する。
 * 解除できたら一覧を取り直し、完了メッセージは一覧の上に出す。
 */
export const UnlinkButton = ({ alias, label }: { alias: string; label: string }) => {
  const router = useRouter();
  const [open, setOpen] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const unlink = async () => {
    setLoading(true);
    setError(null);
    try {
      const response = await fetch(`/api/linked-accounts/${encodeURIComponent(alias)}/unlink`, {
        method: "POST",
      });
      if (!response.ok) {
        const body = await response.json().catch(() => ({}));
        setError(body.error_description || body.error || `HTTP ${response.status}`);
        return;
      }
      setOpen(false);
      router.replace(`/linked-accounts?unlinked=${encodeURIComponent(alias)}`);
      router.refresh();
    } catch (e) {
      setError(String(e));
    } finally {
      setLoading(false);
    }
  };

  return (
    <>
      <Button
        onClick={() => setOpen(true)}
        color="error"
        variant="text"
        size="small"
        startIcon={<LinkOffIcon />}
        sx={{ alignSelf: "flex-start" }}
      >
        連携を解除する
      </Button>
      <Dialog open={open} onClose={() => !loading && setOpen(false)}>
        <DialogTitle>連携を解除しますか？</DialogTitle>
        <DialogContent>
          <DialogContentText>
            {label} との連携を解除します。保管しているトークンは外部IdPでも失効させます。
            もう一度使うには、連携し直してください。
          </DialogContentText>
          {error && (
            <Alert severity="error" sx={{ mt: 2 }}>
              解除に失敗しました：{error}
            </Alert>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setOpen(false)} disabled={loading}>
            キャンセル
          </Button>
          <Button onClick={unlink} color="error" variant="contained" disabled={loading}>
            {loading ? "解除中…" : "解除する"}
          </Button>
        </DialogActions>
      </Dialog>
    </>
  );
};
