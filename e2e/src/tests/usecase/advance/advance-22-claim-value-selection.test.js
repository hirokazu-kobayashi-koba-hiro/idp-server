import { describe, expect, it, beforeAll, afterAll } from "@jest/globals";
import { deletion, get, postWithJson } from "../../../lib/http";
import { requestToken, getAuthorizations, getUserinfo } from "../../../api/oauthClient";
import { onboarding } from "../../../api/managementClient";
import { generateECP256JWKS } from "../../../lib/jose";
import { adminServerConfig, backendUrl } from "../../testConfig";
import { v4 as uuidv4 } from "uuid";
import crypto from "crypto";
import { convertNextAction } from "../../../lib/util";

/**
 * Advance Use Case: per-element consent for array claims (Issue #1816)
 *
 * `denied_claims` can only drop a claim whole, so a custom property holding several things the user
 * owns was all-or-nothing: consenting to `claims:accounts` released every account, denying it
 * released none.
 *
 * Two halves are pinned:
 *
 *   1. view-data surfaces the candidate values — but only once the transaction has a user, so the
 *      pre-authentication response stays free of user attributes.
 *   2. `granted_claim_values` on /authorize narrows what reaches the token, and can only narrow:
 *      a value the user does not own cannot be introduced by naming it.
 */
describe("Advance Use Case: claim value selection (Issue #1816)", () => {
  let systemAccessToken;
  let organizationId;
  let tenantId;
  let clientId;
  let clientSecret;
  let userEmail;
  let userPassword;
  const redirectUri = "https://app.example.com/callback";

  const OWNED_ACCOUNTS = ["acc-1", "acc-2", "acc-3"];
  // A property whose elements are objects, which is what a real deployment stores. Selection is by
  // whole element, so the consent body echoes the object back.
  const OWNED_CARDS = [
    { id: "card-1", brand: "visa", limit: 100000 },
    { id: "card-2", brand: "master", limit: 50000 },
  ];

  beforeAll(async () => {
    const timestamp = Date.now();
    organizationId = uuidv4();
    tenantId = uuidv4();
    clientId = uuidv4();
    clientSecret = crypto.randomBytes(32).toString("hex");
    userEmail = `claim-values-${timestamp}@test.example.com`;
    userPassword = `ClaimValuesPass${timestamp}!`;
    const jwksContent = await generateECP256JWKS();

    const systemTokenResponse = await requestToken({
      endpoint: adminServerConfig.tokenEndpoint,
      grantType: "password",
      username: adminServerConfig.oauth.username,
      password: adminServerConfig.oauth.password,
      scope: adminServerConfig.adminClient.scope,
      clientId: adminServerConfig.adminClient.clientId,
      clientSecret: adminServerConfig.adminClient.clientSecret,
    });
    expect(systemTokenResponse.status).toBe(200);
    systemAccessToken = systemTokenResponse.data.access_token;

    const onboardingResponse = await onboarding({
      headers: { Authorization: `Bearer ${systemAccessToken}` },
      body: {
        organization: {
          id: organizationId,
          name: `Claim Value Selection Org ${timestamp}`,
          description: "E2E for #1816",
        },
        tenant: {
          id: tenantId,
          name: `Claim Value Selection Tenant ${timestamp}`,
          domain: backendUrl,
          authorization_provider: "idp-server",
        },
        authorization_server: {
          issuer: `${backendUrl}/${tenantId}`,
          authorization_endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
          token_endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
          userinfo_endpoint: `${backendUrl}/${tenantId}/v1/userinfo`,
          jwks_uri: `${backendUrl}/${tenantId}/v1/jwks`,
          jwks: jwksContent,
          scopes_supported: [
            "openid",
            "profile",
            "email",
            "claims:accounts",
            "claims:branch",
            "claims:cards",
          ],
          response_types_supported: ["code", "code id_token"],
          response_modes_supported: ["query", "fragment"],
          subject_types_supported: ["public"],
          grant_types_supported: ["authorization_code", "password"],
          id_token_signing_alg_values_supported: ["ES256"],
          token_endpoint_auth_methods_supported: ["client_secret_post"],
          claims_supported: ["sub", "email", "email_verified"],
          extension: {
            access_token_type: "JWT",
            // Required for claims:* scopes to reach the token at all.
            custom_claims_scope_mapping: true,
          },
        },
        user: {
          sub: uuidv4(),
          provider_id: "idp-server",
          email: userEmail,
          email_verified: true,
          raw_password: userPassword,
          // accounts is the array the End-User selects from; branch is a scalar, which has nothing
          // to select between and must be left alone.
          custom_properties: { accounts: OWNED_ACCOUNTS, branch: "tokyo", cards: OWNED_CARDS },
        },
        client: {
          client_id: clientId,
          client_secret: clientSecret,
          redirect_uris: [redirectUri],
          grant_types: ["authorization_code", "password"],
          response_types: ["code", "code id_token"],
          scope: "openid profile email claims:accounts claims:branch claims:cards",
          client_name: "Claim Value Selection Client",
          token_endpoint_auth_method: "client_secret_post",
        },
      },
    });
    expect(onboardingResponse.status).toBe(201);
  });

  afterAll(async () => {
    if (systemAccessToken) {
      await deletion({
        url: `${backendUrl}/v1/management/orgs/${organizationId}`,
        headers: { Authorization: `Bearer ${systemAccessToken}` },
      }).catch(() => {});
    }
  });

  async function startAuthorization(overrides = {}) {
    const authResponse = await getAuthorizations({
      endpoint: `${backendUrl}/${tenantId}/v1/authorizations`,
      clientId,
      responseType: "code",
      state: `claim-values-${Date.now()}`,
      scope: "openid claims:accounts claims:branch claims:cards",
      redirectUri,
      ...overrides,
    });
    expect(authResponse.status).toBe(302);
    return convertNextAction(authResponse.headers.location).params.get("id");
  }

  const viewDataOf = async (authId) => {
    const response = await get({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId}/view-data`,
    });
    expect(response.status).toBe(200);
    return response.data;
  };

  async function authenticate(authId) {
    const response = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId}/password-authentication`,
      body: { username: userEmail, password: userPassword },
    });
    expect(response.status).toBe(200);
  }

  const decodeJwtPayload = (jwt) =>
    JSON.parse(Buffer.from(jwt.split(".")[1], "base64url").toString("utf-8"));

  /**
   * Runs the flow to a token, applying the given consent body at /authorize.
   *
   * @returns the three channels a claim can reach the client through, so a selection that only
   *   narrows one of them is visible as a failure rather than passing on the channel it happens to
   *   cover.
   */
  async function authorizeWith(consentBody) {
    const authId = await startAuthorization();
    await authenticate(authId);

    const authorizeResponse = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId}/authorize`,
      body: consentBody,
    });
    expect(authorizeResponse.status).toBe(200);
    const code = new URL(authorizeResponse.data.redirect_uri).searchParams.get("code");

    const tokenResponse = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "authorization_code",
      code,
      redirectUri,
      clientId,
      clientSecret,
    });
    expect(tokenResponse.status).toBe(200);

    const userinfoResponse = await getUserinfo({
      endpoint: `${backendUrl}/${tenantId}/v1/userinfo`,
      authorizationHeader: { Authorization: `Bearer ${tokenResponse.data.access_token}` },
    });
    expect(userinfoResponse.status).toBe(200);

    return {
      userinfo: userinfoResponse.data,
      accessToken: decodeJwtPayload(tokenResponse.data.access_token),
      idToken: decodeJwtPayload(tokenResponse.data.id_token),
    };
  }

  it("does not expose claim values before the transaction has a user", async () => {
    // view-data is fetched at the start of the flow, before anyone is identified. Returning the
    // candidates there would hand out user attributes to whoever holds the authorization id.
    const authId = await startAuthorization();

    const beforeAuth = await viewDataOf(authId);
    console.log("view-data before auth:", JSON.stringify(beforeAuth.claim_values));
    expect(beforeAuth.claim_values).toBeUndefined();

    await authenticate(authId);

    const afterAuth = await viewDataOf(authId);
    console.log("view-data after auth:", JSON.stringify(afterAuth.claim_values));
    expect(afterAuth.claim_values).toEqual({
      accounts: OWNED_ACCOUNTS,
      cards: OWNED_CARDS,
    });
  }, 90000);

  it("surfaces only array-valued custom properties as selectable", async () => {
    const authId = await startAuthorization();
    await authenticate(authId);

    const viewData = await viewDataOf(authId);

    // branch is requested (claims:branch) and the user has it, but a scalar has nothing to choose
    // between — denied_claims already expresses all-or-nothing for it.
    expect(viewData.claim_values).toEqual({
      accounts: OWNED_ACCOUNTS,
      cards: OWNED_CARDS,
    });
    expect(viewData.claim_values.branch).toBeUndefined();
  }, 90000);

  it("releases every element when the consent body selects nothing", async () => {
    const { userinfo, accessToken, idToken } = await authorizeWith({});
    console.log("without selection:", JSON.stringify({ userinfo, accessToken, idToken }));

    expect(userinfo.accounts).toEqual(OWNED_ACCOUNTS);
    expect(accessToken.accounts).toEqual(OWNED_ACCOUNTS);
    expect(idToken.accounts).toEqual(OWNED_ACCOUNTS);
    expect(userinfo.branch).toBe("tokyo");
  }, 90000);

  it("releases only the selected element", async () => {
    const { userinfo, accessToken, idToken } = await authorizeWith({
      granted_claim_values: { accounts: ["acc-2"] },
    });
    console.log("with selection:", JSON.stringify({ userinfo, accessToken, idToken }));

    // Every channel the claim can reach the client through, not just the one the narrowing
    // happens to be applied on.
    expect(accessToken.accounts).toEqual(["acc-2"]);
    expect(idToken.accounts).toEqual(["acc-2"]);
    expect(userinfo.accounts).toEqual(["acc-2"]);
    // Narrowing one claim must not disturb the others.
    expect(userinfo.branch).toBe("tokyo");
  }, 90000);

  it("cannot introduce a value the user does not own", async () => {
    // The security property: without the intersection, naming a value here would write it straight
    // into a token claim.
    const { userinfo, accessToken, idToken } = await authorizeWith({
      granted_claim_values: { accounts: ["acc-2", "acc-999-not-owned"] },
    });
    console.log("with a non-owned value:", JSON.stringify({ userinfo, accessToken, idToken }));

    expect(accessToken.accounts).toEqual(["acc-2"]);
    expect(idToken.accounts).toEqual(["acc-2"]);
    expect(userinfo.accounts).toEqual(["acc-2"]);
  }, 90000);

  it("selects one element of an object-valued array", async () => {
    // The elements a real deployment stores are objects, not strings. Matching is by whole element,
    // so the consent body echoes the object back; field order does not matter because both sides
    // are parsed JSON objects.
    const { accessToken, idToken } = await authorizeWith({
      granted_claim_values: {
        cards: [{ limit: 100000, brand: "visa", id: "card-1" }],
      },
    });
    console.log("with an object selection:", JSON.stringify({ accessToken, idToken }));

    expect(accessToken.cards).toEqual([OWNED_CARDS[0]]);
    expect(idToken.cards).toEqual([OWNED_CARDS[0]]);
    // Narrowing the object array must not disturb the string array.
    expect(accessToken.accounts).toEqual(OWNED_ACCOUNTS);
  }, 90000);

  it("does not select an object by its identifier alone", async () => {
    // The limit of whole-element matching: a partial object is not the owned object, so it matches
    // nothing and the claim is dropped. Selecting by a key field would require the selection to
    // name which field identifies an element.
    const { accessToken, idToken } = await authorizeWith({
      granted_claim_values: { cards: [{ id: "card-1" }] },
    });
    console.log("with a partial object:", JSON.stringify({ accessToken, idToken }));

    expect(accessToken).not.toHaveProperty("cards");
    expect(idToken).not.toHaveProperty("cards");
  }, 90000);

  it("keeps the selection when the same claim name is also denied", async () => {
    // denied_claims does not stop a custom claim — those are released by the claims:* scope, and
    // the creators read the grant's scopes. Dropping the selection on a denied name would hand the
    // client every account instead of the one the End-User picked.
    const { userinfo, accessToken } = await authorizeWith({
      denied_claims: ["accounts"],
      granted_claim_values: { accounts: ["acc-2"] },
    });
    console.log("denied name + selection:", JSON.stringify({ accessToken, userinfo }));

    expect(accessToken.accounts).toEqual(["acc-2"]);
    expect(userinfo.accounts).toEqual(["acc-2"]);
  }, 90000);

  it("carries the selection per authorization rather than from an earlier one", async () => {
    // Each authorization builds its own grant, so a selection does not survive into the next one:
    // the merge that keeps an earlier consent applies to the authorization_granted record used for
    // SSO decisions, not to the grant a token is issued from. Pinned because the alternative — an
    // earlier narrowing silently applying to a later token — would be just as defensible a design,
    // and the choice should be visible rather than incidental.
    const narrowed = await authorizeWith({
      granted_claim_values: { accounts: ["acc-2"] },
    });
    expect(narrowed.accessToken.accounts).toEqual(["acc-2"]);

    const withoutSelection = await authorizeWith({});
    console.log("re-consent without selection:", JSON.stringify(withoutSelection.accessToken));

    expect(withoutSelection.accessToken.accounts).toEqual(OWNED_ACCOUNTS);
    expect(withoutSelection.userinfo.accounts).toEqual(OWNED_ACCOUNTS);
  }, 90000);

  it("narrows the ID Token issued straight from the authorization endpoint", async () => {
    // The hybrid flow builds the ID Token at /authorize from the live user, not from the grant's
    // stored snapshot, so it is a separate path from the token endpoint and can leak on its own.
    const authId = await startAuthorization({
      responseType: "code id_token",
      responseMode: "fragment",
      nonce: `claim-values-nonce-${Date.now()}`,
    });
    await authenticate(authId);

    const authorizeResponse = await postWithJson({
      url: `${backendUrl}/${tenantId}/v1/authorizations/${authId}/authorize`,
      body: { granted_claim_values: { accounts: ["acc-2"] } },
    });
    expect(authorizeResponse.status).toBe(200);

    const fragment = new URL(authorizeResponse.data.redirect_uri).hash.substring(1);
    const idToken = decodeJwtPayload(new URLSearchParams(fragment).get("id_token"));
    console.log("hybrid id_token:", JSON.stringify(idToken));

    expect(idToken.accounts).toEqual(["acc-2"]);
  }, 90000);

  it("omits the claim when no element is selected", async () => {
    // Same result as denying the claim whole, rather than an empty array.
    const { userinfo, accessToken, idToken } = await authorizeWith({
      granted_claim_values: { accounts: [] },
    });
    console.log("with an empty selection:", JSON.stringify({ userinfo, accessToken, idToken }));

    expect(accessToken).not.toHaveProperty("accounts");
    expect(idToken).not.toHaveProperty("accounts");
    expect(userinfo).not.toHaveProperty("accounts");
    expect(userinfo.branch).toBe("tokyo");
  }, 90000);
});
