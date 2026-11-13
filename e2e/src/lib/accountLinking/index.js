import { expect } from "@jest/globals";
import {
  backendUrl,
  clientSecretPostClient,
  federationServerConfig,
  serverConfig,
} from "../../tests/testConfig";
import { authorize, postAuthentication, requestToken } from "../../api/oauthClient";
import { get, post, postWithJson } from "../http";
import { requestAuthorizations } from "../../oauth/request";
import { convertNextAction } from "../util";

/**
 * Account linking (#1531) の一連の操作。外部IdP役は federation テナント、RP は test-tenant の
 * clientSecretPost。scenario-16 と同じ手順を、他のテストから使える形にしたもの。
 */

export const RETURN_TO = "https://client.example.org/linking/callback";
export const MANAGE_SCOPE = "linked_account:manage";
export const READ_TOKEN_SCOPE = "linked_account:read_token";

export const ownerUser = {
  username: "ito.ichiro@gmail.com",
  password: "successUserCode001",
};

/** RP でログインし、アクセストークンを得る。 */
export const login = async (user, scope = `openid profile email ${MANAGE_SCOPE}`) => {
  const { status, authorizationResponse } = await requestAuthorizations({
    endpoint: serverConfig.authorizationEndpoint,
    clientId: clientSecretPostClient.clientId,
    redirectUri: clientSecretPostClient.redirectUri,
    responseType: "code",
    state: "account-linking-e2e",
    scope,
    user,
  });
  expect(status).toBe(200);

  const tokenResponse = await requestToken({
    endpoint: serverConfig.tokenEndpoint,
    code: authorizationResponse.code,
    grantType: "authorization_code",
    redirectUri: clientSecretPostClient.redirectUri,
    clientId: clientSecretPostClient.clientId,
    clientSecret: clientSecretPostClient.clientSecret,
  });
  expect(tokenResponse.status).toBe(200);
  return tokenResponse.data.access_token;
};

const requestAdminAccessToken = async () => {
  const response = await requestToken({
    endpoint: serverConfig.tokenEndpoint,
    grantType: "password",
    username: serverConfig.oauth.username,
    password: serverConfig.oauth.password,
    scope: clientSecretPostClient.scope,
    clientId: clientSecretPostClient.clientId,
    clientSecret: clientSecretPostClient.clientSecret,
  });
  expect(response.status).toBe(200);
  return response.data.access_token;
};

/** 外部IdP で email 認証して同意し、連携 callback の URL を得る。 */
const consentAtExternalIdp = async (externalAuthorizationUri, user) => {
  const authorizationResponse = await get({ url: externalAuthorizationUri });
  expect(authorizationResponse.status).toBe(302);
  const { params } = convertNextAction(authorizationResponse.headers.location);
  const id = params.get("id");

  const challengeResponse = await postAuthentication({
    endpoint: `${backendUrl}/${federationServerConfig.tenantId}/v1/authorizations/{id}/email-authentication-challenge`,
    id,
    body: { email: user.username, email_template: "authentication" },
  });
  expect(challengeResponse.status).toBe(200);

  const adminAccessToken = await requestAdminAccessToken();
  const transactionResponse = await get({
    url: `${backendUrl}/v1/management/organizations/${serverConfig.organizationId}/tenants/${federationServerConfig.tenantId}/authentication-transactions?authorization_id=${id}`,
    headers: { Authorization: `Bearer ${adminAccessToken}` },
  });
  expect(transactionResponse.status).toBe(200);
  const transactionId = transactionResponse.data.list[0].id;

  const interactionResponse = await get({
    url: `${backendUrl}/v1/management/organizations/${serverConfig.organizationId}/tenants/${federationServerConfig.tenantId}/authentication-interactions/${transactionId}/email-authentication-challenge`,
    headers: { Authorization: `Bearer ${adminAccessToken}` },
  });
  expect(interactionResponse.status).toBe(200);

  const verificationResponse = await postAuthentication({
    endpoint: `${backendUrl}/${federationServerConfig.tenantId}/v1/authorizations/{id}/email-authentication`,
    id,
    body: { verification_code: interactionResponse.data.payload.verification_code },
  });
  expect(verificationResponse.status).toBe(200);

  const authorizeResponse = await authorize({
    endpoint: `${backendUrl}/${federationServerConfig.tenantId}/v1/authorizations/{id}/authorize`,
    id,
    body: {},
  });
  expect(authorizeResponse.status).toBe(200);
  return authorizeResponse.data.redirect_uri;
};

/** 連携を 1 回通して alias を返す。 */
export const linkOnce = async (accessToken, provider, scope = "account offline_access", user = ownerUser) => {
  const linkResponse = await postWithJson({
    url: `${backendUrl}/${serverConfig.tenantId}/v1/me/linked-external-accounts/link/${provider}`,
    headers: { Authorization: `Bearer ${accessToken}` },
    body: { redirect_uri: RETURN_TO, scope },
  });
  expect(linkResponse.status).toBe(201);

  const startResponse = await get({ url: linkResponse.data.start_url });
  expect(startResponse.status).toBe(302);

  const callbackUri = await consentAtExternalIdp(startResponse.headers.location, user);
  const callbackResponse = await get({ url: callbackUri });
  expect(callbackResponse.status).toBe(302);

  const completeResponse = await postWithJson({
    url: `${backendUrl}/${serverConfig.tenantId}/v1/me/linked-external-accounts/complete`,
    headers: { Authorization: `Bearer ${accessToken}` },
    body: { state: linkResponse.data.state },
  });
  expect(completeResponse.status).toBe(201);
  return completeResponse.data.alias;
};

/** 保管された外部トークンを、クライアント認証 + ユーザーのアクセストークンで取り出す。 */
export const retrieveToken = async (alias, accessToken) => {
  const params = new URLSearchParams();
  params.append("token", accessToken);
  params.append("client_id", clientSecretPostClient.clientId);
  params.append("client_secret", clientSecretPostClient.clientSecret);
  return await post({
    url: `${backendUrl}/${serverConfig.tenantId}/v1/linked-external-accounts/${alias}/token`,
    body: params.toString(),
  });
};
