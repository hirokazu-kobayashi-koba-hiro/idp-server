/**
 * 認可フローの終わりで、ブラウザを idp-server 経由で RP へ返す。
 *
 * ここまでの API 呼び出しは、この画面から idp-server への fetch。認可画面が
 * idp-server と別サイトに置かれている場合、それらはサードパーティのリクエストになる。
 * Safari は既定でサードパーティ Cookie を無効にしているため、
 *
 *   - ブラウザ束縛の Cookie は「送られない」ので検証できない
 *   - OP セッションの Cookie は「保存されない」のでセッションが残らない
 *
 * という状態になる。RP はトークンを受け取れるので一見成功して見えるが、
 * ブラウザと idp-server の関係は何も残っていない。
 *
 * そこで RP へ直接飛ばさず、一度 idp-server の `/complete` にトップレベル遷移する。
 * そこは first-party なので、束縛 Cookie を読め、セッション Cookie を保存できる。
 */
/** Next.js のルータクエリは string | string[] | undefined で渡ってくるので、ここで揃える。 */
type RouteParam = string | string[] | undefined;

const one = (value: RouteParam): string =>
  Array.isArray(value) ? (value[0] ?? "") : (value ?? "");

export const completeAuthorization = ({
  backendUrl,
  tenantId,
  id,
  authProof,
  redirectUri,
}: {
  backendUrl: string;
  tenantId: RouteParam;
  id: RouteParam;
  authProof?: string;
  redirectUri?: string;
}) => {
  // auth_proof が無いのは、認可画面が同一サイトにある場合。そこでは code が
  // redirect_uri に入って返ってくるので、従来どおりそのまま RP へ返す。
  if (!authProof) {
    if (redirectUri) window.location.href = redirectUri;
    return;
  }
  window.location.href = `${backendUrl}/${one(tenantId)}/v1/authorizations/${one(id)}/complete?auth_proof=${encodeURIComponent(authProof)}`;
};
