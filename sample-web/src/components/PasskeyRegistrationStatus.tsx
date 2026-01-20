"use client";

import { useSearchParams } from "next/navigation";
import { Alert, AlertTitle, Collapse } from "@mui/material";
import { useState, useEffect } from "react";

const PasskeyRegistrationStatus = () => {
  const searchParams = useSearchParams();
  const [open, setOpen] = useState(false);

  const passkeySuccess = searchParams.get("passkey_success");
  const passkeyError = searchParams.get("passkey_error");
  const passkeyErrorDescription = searchParams.get("passkey_error_description");

  useEffect(() => {
    if (passkeySuccess || passkeyError) {
      setOpen(true);
      // 5秒後に自動的に閉じる
      const timer = setTimeout(() => setOpen(false), 5000);
      return () => clearTimeout(timer);
    }
  }, [passkeySuccess, passkeyError]);

  if (!passkeySuccess && !passkeyError) {
    return null;
  }

  const getErrorMessage = (error: string, description: string | null): string => {
    switch (error) {
      case "session_expired":
        return "セッションが期限切れです。もう一度お試しください。";
      case "state_mismatch":
        return "リクエストの検証に失敗しました。もう一度お試しください。";
      case "token_exchange_failed":
        return "認証に失敗しました。もう一度お試しください。";
      case "access_denied":
        return description || "アクセスが拒否されました。";
      default:
        return description || `エラーが発生しました: ${error}`;
    }
  };

  return (
    <Collapse in={open}>
      {passkeySuccess && (
        <Alert
          severity="success"
          onClose={() => setOpen(false)}
          sx={{ mb: 2 }}
        >
          <AlertTitle>パスキー登録完了</AlertTitle>
          新しいパスキーが正常に登録されました。バックアップ用のデバイスとして使用できます。
        </Alert>
      )}
      {passkeyError && (
        <Alert
          severity="error"
          onClose={() => setOpen(false)}
          sx={{ mb: 2 }}
        >
          <AlertTitle>パスキー登録エラー</AlertTitle>
          {getErrorMessage(passkeyError, passkeyErrorDescription)}
        </Alert>
      )}
    </Collapse>
  );
};

export default PasskeyRegistrationStatus;
