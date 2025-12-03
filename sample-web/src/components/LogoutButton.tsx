"use client";

import { useState } from "react";
import { Button, CircularProgress } from "@mui/material";
import LogoutIcon from "@mui/icons-material/Logout";

const issuer = process.env.NEXT_PUBLIC_IDP_SERVER_ISSUER;
const clientId = process.env.NEXT_PUBLIC_IDP_CLIENT_ID;
const frontendUrl = process.env.NEXT_PUBLIC_FRONTEND_URL;

interface LogoutButtonProps {
  idToken?: string;
}

/**
 * RP-Initiated Logout Button
 *
 * OpenID Connect RP-Initiated Logout 1.0 に準拠したログアウトボタン。
 * ブラウザから fetch で IDP server の /v1/logout を呼び出し、
 * Cookie を送信してセッションを削除する。
 *
 * フロー:
 * 1. ボタンクリック → ブラウザから fetch で /v1/logout を呼び出し（Cookie 送信）
 * 2. IDP server がセッションを削除
 * 3. NextAuth の Cookie を削除するためにコールバックAPIへリダイレクト
 *
 * @see https://openid.net/specs/openid-connect-rpinitiated-1_0.html
 */
const LogoutButton = ({ idToken }: LogoutButtonProps) => {
  const [isLoading, setIsLoading] = useState(false);

  const handleLogout = async () => {
    setIsLoading(true);

    try {
      const params = new URLSearchParams({
        client_id: clientId || "",
      });

      // id_token_hint を追加（RFC推奨: セッション特定の精度向上）
      if (idToken) {
        params.set("id_token_hint", idToken);
      }

      // ブラウザから fetch で IDP server の logout を呼び出し（Cookie が送信される）
      await fetch(`${issuer}/v1/logout?${params.toString()}`, {
        method: "GET",
        credentials: "include",
      });

      // NextAuth の Cookie を削除してホームへリダイレクト
      window.location.href = `${frontendUrl}/api/auth/logout`;
    } catch (error) {
      console.error("Logout error:", error);
      // エラーでも NextAuth のセッションは削除
      window.location.href = `${frontendUrl}/api/auth/logout`;
    }
  };

  return (
    <Button
      onClick={handleLogout}
      variant="outlined"
      color="error"
      startIcon={isLoading ? <CircularProgress size={20} /> : <LogoutIcon />}
      disabled={isLoading}
    >
      {isLoading ? "ログアウト中..." : "ログアウト"}
    </Button>
  );
};

export default LogoutButton;
