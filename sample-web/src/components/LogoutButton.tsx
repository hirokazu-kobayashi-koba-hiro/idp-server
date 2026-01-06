"use client";

import { useState } from "react";
import { Button, CircularProgress } from "@mui/material";
import LogoutIcon from "@mui/icons-material/Logout";
import { signOut } from "next-auth/react";

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
    console.log("[LogoutButton] handleLogout called");
    console.log("[LogoutButton] issuer:", issuer);
    console.log("[LogoutButton] clientId:", clientId);
    console.log("[LogoutButton] frontendUrl:", frontendUrl);
    console.log("[LogoutButton] idToken:", idToken ? "present" : "not present");

    setIsLoading(true);

    try {
      const params = new URLSearchParams({
        client_id: clientId || "",
      });

      // id_token_hint を追加（RFC推奨: セッション特定の精度向上）
      if (idToken) {
        params.set("id_token_hint", idToken);
      }

      const logoutUrl = `${issuer}/v1/logout?${params.toString()}`;
      console.log("[LogoutButton] Calling logout URL:", logoutUrl);

      // ブラウザから fetch で IDP server の logout を呼び出し（Cookie が送信される）
      const response = await fetch(logoutUrl, {
        method: "GET",
        credentials: "include",
      });

      console.log("[LogoutButton] Logout response status:", response.status);
      console.log("[LogoutButton] Logout response ok:", response.ok);

      // NextAuth のセッションを削除してホームへリダイレクト（確認画面スキップ）
      console.log("[LogoutButton] Calling NextAuth signOut");
      await signOut({ callbackUrl: "/" });
    } catch (error) {
      console.error("[LogoutButton] Logout error:", error);
      // エラーでも NextAuth のセッションは削除
      await signOut({ callbackUrl: "/" });
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
