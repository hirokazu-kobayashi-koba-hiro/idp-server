import { describe, expect, it, beforeAll, afterAll } from "@jest/globals";
import { deletion, get, patchWithJson, postWithJson } from "../../../lib/http";
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
  // The same accounts as verified claims (#1947), deliberately not in the shape of the custom
  // property: each selection is matched against its own source only.
  const VERIFIED_ACCOUNTS = [
    { id: "acc-1", bank: "Bank A" },
    { id: "acc-2", bank: "Bank B" },
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
            "verified_claims:accounts",
            "management",
            "org-management",
            "account",
          ],
          response_types_supported: ["code", "code id_token"],
          response_modes_supported: ["query", "fragment"],
          subject_types_supported: ["public"],
          grant_types_supported: ["authorization_code", "password"],
          id_token_signing_alg_values_supported: ["ES256"],
          token_endpoint_auth_methods_supported: ["client_secret_post"],
          claims_supported: ["sub", "email", "email_verified"],
          claims_parameter_supported: true,
          extension: {
            access_token_type: "JWT",
            // Required for claims:* scopes to reach the token at all.
            custom_claims_scope_mapping: true,
            // Required for verified_claims:* scopes to reach the token and UserInfo.
            access_token_selective_verified_claims: true,
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
          verified_claims: {
            verification: { trust_framework: "jp_aml" },
            claims: { accounts: VERIFIED_ACCOUNTS, given_name: "Taro" },
          },
        },
        client: {
          client_id: clientId,
          client_secret: clientSecret,
          redirect_uris: [redirectUri],
          grant_types: ["authorization_code", "password"],
          response_types: ["code", "code id_token"],
          scope:
            "openid profile email claims:accounts claims:branch claims:cards verified_claims:accounts management org-management account",
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
  async function authorizeWith(consentBody, authorizationOverrides = {}) {
    const authId = await startAuthorization(authorizationOverrides);
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
      accessTokenValue: tokenResponse.data.access_token,
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

  it("does not release an element the user gained after the selection was made", async () => {
    // granted_claim_values is an allow-list: naming elements says "these and no others", and the
    // grant keeps that list for as long as it lives. An account opened after the consent was given
    // was never granted, so it must not start appearing on a token issued from that grant.
    const { accessToken, accessTokenValue } = await authorizeWith({
      granted_claim_values: { accounts: ["acc-2"] },
    });
    expect(accessToken.accounts).toEqual(["acc-2"]);

    // The org-level user API needs a token issued by the organization's own tenant.
    const orgTokenResponse = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "password",
      username: userEmail,
      password: userPassword,
      scope: "org-management account management",
      clientId,
      clientSecret,
    });
    expect(orgTokenResponse.status).toBe(200);

    const added = [...OWNED_ACCOUNTS, "acc-4-opened-later"];
    const patched = await patchWithJson({
      url: `${backendUrl}/v1/management/organizations/${organizationId}/tenants/${tenantId}/users/${accessToken.sub}`,
      headers: { Authorization: `Bearer ${orgTokenResponse.data.access_token}` },
      body: { custom_properties: { accounts: added, branch: "tokyo", cards: OWNED_CARDS } },
    });
    expect(patched.status).toBe(200);

    // UserInfo reads the user as it is now, so it sees the added account and has to narrow it away.
    const refreshed = await getUserinfo({
      endpoint: `${backendUrl}/${tenantId}/v1/userinfo`,
      authorizationHeader: { Authorization: `Bearer ${accessTokenValue}` },
    });
    console.log("userinfo after the user gained an account:", JSON.stringify(refreshed.data));

    expect(refreshed.status).toBe(200);
    expect(refreshed.data.accounts).toEqual(["acc-2"]);
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

  /**
   * The same choice over the arrays under `verified_claims.claims` (Issue #1947), sent under the key
   * that mirrors where they appear in a token. It is kept apart from the custom property of the same
   * name: the two need not hold the same elements.
   */
  describe("verified_claims (Issue #1947)", () => {
    const BOTH_SCOPES = { scope: "openid claims:accounts verified_claims:accounts" };
    const ACC_2 = { id: "acc-2", bank: "Bank B" };
    // An earlier test adds an account to the custom property, so a custom property left alone is
    // checked as holding every originally owned element rather than exactly them.
    const NOT_NARROWED = expect.arrayContaining(OWNED_ACCOUNTS);

    // Requests verified_claims for the ID Token through the claims parameter, the only way a
    // verified claim reaches the ID Token.
    const ID_TOKEN_CLAIMS = {
      scope: "openid",
      claims: JSON.stringify({
        id_token: {
          verified_claims: {
            verification: { trust_framework: null },
            claims: { accounts: null },
          },
        },
      }),
    };

    it("offers the verified elements apart from the custom property", async () => {
      const authId = await startAuthorization(BOTH_SCOPES);
      await authenticate(authId);

      const viewData = await viewDataOf(authId);
      console.log("view-data claim_values:", JSON.stringify(viewData.claim_values));

      expect(viewData.claim_values).toEqual({
        accounts: NOT_NARROWED,
        verified_claims: { claims: { accounts: VERIFIED_ACCOUNTS } },
      });
    }, 90000);

    it("does not expose claim values for a user named by login_hint before authentication", async () => {
      // A login_hint resolves the user up front, before anyone has proven to be them. The values
      // are the user's to choose from on the consent screen, so they appear only once the
      // authentication has succeeded — not to whoever named the user.
      const authId = await startAuthorization({
        ...BOTH_SCOPES,
        loginHint: `email:${userEmail}`,
      });

      const beforeAuth = await viewDataOf(authId);
      console.log("view-data with login_hint before auth:", JSON.stringify(beforeAuth.claim_values));
      expect(beforeAuth.claim_values).toBeUndefined();

      await authenticate(authId);

      const afterAuth = await viewDataOf(authId);
      expect(afterAuth.claim_values).toEqual({
        accounts: NOT_NARROWED,
        verified_claims: { claims: { accounts: VERIFIED_ACCOUNTS } },
      });
    }, 90000);

    it("offers the verified elements requested through the claims parameter", async () => {
      const authId = await startAuthorization(ID_TOKEN_CLAIMS);
      await authenticate(authId);

      const viewData = await viewDataOf(authId);

      expect(viewData.claim_values).toEqual({
        verified_claims: { claims: { accounts: VERIFIED_ACCOUNTS } },
      });
    }, 90000);

    it("releases only the selected verified element", async () => {
      const { userinfo, accessToken } = await authorizeWith(
        { granted_claim_values: { verified_claims: { claims: { accounts: [ACC_2] } } } },
        BOTH_SCOPES
      );
      console.log("verified selection:", JSON.stringify({ userinfo, accessToken }));

      expect(accessToken.verified_claims.claims.accounts).toEqual([ACC_2]);
      expect(userinfo.verified_claims.claims.accounts).toEqual([ACC_2]);
      // verification is not part of the choice.
      expect(accessToken.verified_claims.verification).toEqual({ trust_framework: "jp_aml" });
      // The custom property of the same name is a separate choice, left alone here.
      expect(accessToken.accounts).toEqual(NOT_NARROWED);
      expect(userinfo.accounts).toEqual(NOT_NARROWED);
    }, 90000);

    it("does not narrow the verified claim by the custom property selection", async () => {
      const { userinfo, accessToken } = await authorizeWith(
        { granted_claim_values: { accounts: ["acc-2"] } },
        BOTH_SCOPES
      );

      expect(accessToken.accounts).toEqual(["acc-2"]);
      expect(accessToken.verified_claims.claims.accounts).toEqual(VERIFIED_ACCOUNTS);
      expect(userinfo.verified_claims.claims.accounts).toEqual(VERIFIED_ACCOUNTS);
    }, 90000);

    it("cannot introduce a verified value the user does not own", async () => {
      const { accessToken } = await authorizeWith(
        {
          granted_claim_values: {
            verified_claims: { claims: { accounts: [{ id: "acc-9", bank: "Bank Z" }] } },
          },
        },
        BOTH_SCOPES
      );

      // Nothing selected is owned, so the only requested verified claim is gone, and with it the
      // whole verified_claims (OIDC4IDA §5.7.4).
      expect(accessToken.verified_claims).toBeUndefined();
    }, 90000);

    it("narrows the verified claim in the ID Token requested through the claims parameter", async () => {
      const { idToken, userinfo } = await authorizeWith(
        { granted_claim_values: { verified_claims: { claims: { accounts: [ACC_2] } } } },
        ID_TOKEN_CLAIMS
      );
      console.log("ID Token verified_claims:", JSON.stringify(idToken.verified_claims));

      expect(idToken.verified_claims.claims.accounts).toEqual([ACC_2]);
      // Nothing was requested for UserInfo.
      expect(userinfo.verified_claims).toBeUndefined();
    }, 90000);

    it("omits the verified claim in the ID Token when no element is selected", async () => {
      const { idToken } = await authorizeWith(
        { granted_claim_values: { verified_claims: { claims: { accounts: [] } } } },
        ID_TOKEN_CLAIMS
      );

      // The claims parameter path keeps verification with an empty claims object (IDA schema
      // §5.3): the claim is dropped, not emptied.
      expect(idToken.verified_claims.verification).toEqual({ trust_framework: "jp_aml" });
      expect(idToken.verified_claims.claims.accounts).toBeUndefined();
    }, 90000);
  });
});
