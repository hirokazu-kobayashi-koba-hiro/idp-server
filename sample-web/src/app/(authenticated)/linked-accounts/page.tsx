import {
  Alert,
  Button,
  Card,
  CardContent,
  Chip,
  Container,
  Divider,
  Stack,
  Typography,
} from "@mui/material";
import LinkIcon from "@mui/icons-material/Link";
import ArrowBackIcon from "@mui/icons-material/ArrowBack";
import { auth, internalIssuer } from "@/app/auth";
import { redirect } from "next/navigation";
import { CallExternalApi } from "./CallExternalApi";
import { UnlinkButton } from "./UnlinkButton";

type LinkedExternalAccount = {
  alias: string;
  provider: string;
  federated_username: string | null;
  scope: string | null;
  access_token_expires_at: string | null;
  refresh_token_expires_at: string | null;
  created_at: string | null;
  updated_at: string | null;
};

const PROVIDER = "account-linking";

/**
 * idp-server の日時はオフセットを持たない。どの API もサーバーのタイムゾーン（既定 UTC）で返すので、
 * UTC として読み、表示用のタイムゾーンに直して略称を添える。
 */
const DISPLAY_TIME_ZONE = process.env.NEXT_PUBLIC_DISPLAY_TIME_ZONE || "Asia/Tokyo";

const formatDateTime = (value: string | null): string => {
  if (!value) return "-";
  const date = new Date(/([zZ]|[+-]\d{2}:\d{2})$/.test(value) ? value : `${value}Z`);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat("ja-JP", {
    timeZone: DISPLAY_TIME_ZONE,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
    timeZoneName: "short",
  }).format(date);
};

const fetchLinkedAccounts = async (accessToken: string): Promise<LinkedExternalAccount[]> => {
  const response = await fetch(`${internalIssuer}/v1/me/linked-external-accounts`, {
    headers: { Authorization: `Bearer ${accessToken}` },
    cache: "no-store",
  });

  if (!response.ok) {
    console.error("failed to list linked accounts", response.status, await response.text());
    return [];
  }

  const body = await response.json();
  return body.list ?? [];
};

const LinkedAccounts = async ({
  searchParams,
}: {
  searchParams: Promise<{ linked?: string; unlinked?: string; error?: string }>;
}) => {
  const session = await auth();
  if (!session?.accessToken) {
    redirect("/");
  }

  const { linked, unlinked, error } = await searchParams;
  const accounts = await fetchLinkedAccounts(session.accessToken);

  return (
    <Container maxWidth="md" sx={{ py: 4 }}>
      <Stack spacing={3}>
        <Stack direction="row" justifyContent="space-between" alignItems="center">
          <Typography variant="h4" component="h1">
            外部アカウント連携
          </Typography>
          <Button href="/home" startIcon={<ArrowBackIcon />} size="small">
            ダッシュボード
          </Button>
        </Stack>

        <Typography variant="body2" color="text.secondary">
          外部IdPのアカウントを紐付けると、そのアクセストークンとリフレッシュトークンが
          暗号化して保管されます。保管したトークンは外部APIの呼び出しに使えます。
        </Typography>

        {linked && (
          <Alert severity="success">
            連携が完了しました（{linked}）。
          </Alert>
        )}
        {unlinked && (
          <Alert severity="info">
            連携を解除しました（{unlinked}）。
          </Alert>
        )}
        {error && <Alert severity="error">連携に失敗しました：{error}</Alert>}

        <Button
          href={`/api/linked-accounts/link?provider=${PROVIDER}`}
          variant="contained"
          startIcon={<LinkIcon />}
          sx={{ alignSelf: "flex-start" }}
        >
          外部アカウントを連携する
        </Button>

        <Divider />

        {accounts.length === 0 ? (
          <Typography variant="body2" color="text.secondary">
            連携済みのアカウントはありません。
          </Typography>
        ) : (
          <Stack spacing={2}>
            {accounts.map((account) => (
              <Card key={account.alias} variant="outlined">
                <CardContent>
                  <Stack spacing={1}>
                    <Stack direction="row" spacing={1} alignItems="center">
                      <Typography variant="h6">
                        {account.federated_username ?? account.provider}
                      </Typography>
                      <Chip label={account.provider} size="small" />
                      <Chip label={account.alias} size="small" variant="outlined" />
                    </Stack>
                    <Typography variant="body2" color="text.secondary">
                      scope: {account.scope}
                    </Typography>
                    <Typography variant="body2" color="text.secondary">
                      アクセストークン有効期限: {formatDateTime(account.access_token_expires_at)}
                    </Typography>
                    <Typography variant="body2" color="text.secondary">
                      リフレッシュトークン有効期限: {formatDateTime(account.refresh_token_expires_at)}
                    </Typography>
                    <Typography variant="caption" color="text.secondary">
                      連携日時: {formatDateTime(account.created_at)}
                      {account.updated_at && account.updated_at !== account.created_at
                        ? ` ／ 更新日時: ${formatDateTime(account.updated_at)}`
                        : ""}
                    </Typography>
                    <CallExternalApi alias={account.alias} />
                    <UnlinkButton
                      alias={account.alias}
                      label={account.federated_username ?? account.alias}
                    />
                  </Stack>
                </CardContent>
              </Card>
            ))}
          </Stack>
        )}
      </Stack>
    </Container>
  );
};

export default LinkedAccounts;
