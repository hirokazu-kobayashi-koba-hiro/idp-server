import { get } from "../lib/http"

export const getAuthorizations = async ({
  endpoint,
  scope,
  responseType,
  clientId,
  redirectUri,
  state,
  responseMode,
  nonce,
  display,
  prompt,
  maxAge,
  uiLocales,
  idTokenHint,
  loginHint,
  acrValues,
  claims,
  request,
  requestUri
}) => {
  const query = new URLSearchParams({
    scope: scope? scope: "",
    response_type: responseType? responseType: "",
    client_id: clientId? clientId: "",
    redirect_uri: redirectUri? requestUri: "",
    state: state? state: "",
    response_mode: responseMode? responseMode: "",
    nonce: nonce? nonce: "",
    display: display? display: "",
    prompt: prompt? prompt: "",
    max_age: maxAge? maxAge: "",
    ui_locales: uiLocales? uiLocales: "",
    id_token_hint: idTokenHint? idTokenHint: "",
    login_hint: loginHint? loginHint: "",
    acr_values: acrValues? acrValues: "",
    claims: claims? claims: "",
    request: request? request: "",
    request_uri: requestUri? requestUri: "",
  }).toString();
  const url = `${endpoint}?${query}`
  console.log(url)
  return await get({
    url: url,
    headers: {},
  })
}
