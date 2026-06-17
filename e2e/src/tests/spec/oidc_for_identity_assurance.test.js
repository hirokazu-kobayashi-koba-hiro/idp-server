import { beforeAll, describe, expect, it, xit } from "@jest/globals";

import { getConfiguration, getJwks, requestToken } from "../../api/oauthClient";
import {
  backendUrl,
  clientSecretPostClient,
  serverConfig
} from "../testConfig";
import { requestAuthorizations } from "../../oauth/request";
import { verifyAndDecodeJwt } from "../../lib/jose";
import { get } from "../../lib/http";

describe("OpenID Connect for Identity Assurance 1.0 ", () => {
  it("success pattern", async () => {
    const verifiedClaims = {
      "claims": {
        "address": {
          "country": "UK",
          "locality": "Edinburgh",
          "postal_code": "EH1 9GP",
          "street_address": "122 Burns Crescent"
        },
        "birthdate": "1976-03-11",
        "given_name": "Sarah",
        "family_name": "Meredyth",
        "place_of_birth": {
          "country": "UK"
        }
      },
      "verification": {
        "evidence": [
          {
            "time": "2021-04-09T14:12Z",
            "type": "electronic_record",
            "record": {
              "type": "mortgage_account",
              "source": {
                "name": "TheCreditBureau"
              }
            },
            "check_details": [
              {
                "txn": "kbv1-hf934hn09234ng03jj3",
                "check_method": "kbv",
                "organization": "TheCreditBureau"
              }
            ]
          },
          {
            "time": "2021-04-09T14:12Z",
            "type": "electronic_record",
            "record": {
              "type": "bank_account",
              "source": {
                "name": "TheBank"
              }
            },
            "check_details": [
              {
                "txn": "kbv2-nm0f23u9459fj38u5j6",
                "check_method": "kbv",
                "organization": "OpenBankingTPP"
              }
            ]
          }
        ],
        "trust_framework": "eidas"
      }
    };

    const { authorizationResponse } = await requestAuthorizations({
      endpoint: serverConfig.authorizationEndpoint,
      clientId: clientSecretPostClient.clientId,
      responseType: "code",
      state: "aiueo",
      scope: "openid profile phone email verified_claims:family_name " + clientSecretPostClient.scope,
      redirectUri: clientSecretPostClient.redirectUri,
      customParams: {
        organizationId: "123",
        organizationName: "test",
      },
      claims: JSON.stringify({
        id_token: {
          verified_claims: verifiedClaims,
        }
      }),
    });
    console.log(authorizationResponse);
    expect(authorizationResponse.code).not.toBeNull();

    const tokenResponse = await requestToken({
      endpoint: serverConfig.tokenEndpoint,
      code: authorizationResponse.code,
      grantType: "authorization_code",
      redirectUri: clientSecretPostClient.redirectUri,
      clientId: clientSecretPostClient.clientId,
      clientSecret: clientSecretPostClient.clientSecret,
    });
    console.log(tokenResponse.data);
    expect(tokenResponse.data).toHaveProperty("id_token");

    const jwksResponse = await getJwks({ endpoint: serverConfig.jwksEndpoint });
    console.log(jwksResponse.data);
    expect(jwksResponse.status).toBe(200);

    const decodedIdToken = verifyAndDecodeJwt({
      jwt: tokenResponse.data.id_token,
      jwks: jwksResponse.data,
    });
    console.log(JSON.stringify(decodedIdToken, null, 2));
    const payload = decodedIdToken.payload;
    expect(decodedIdToken.verifyResult).toBe(true);
    expect(payload.iss).not.toBeNull();
    expect(payload.iss).toEqual(serverConfig.issuer);
    expect(payload.verified_claims.claims).not.toBeNull();
    expect(payload.verified_claims.verification).not.toBeNull();

    const userinfoResponse = await get({
      url: `${backendUrl}/${serverConfig.tenantId}/v1/userinfo`,
      headers: {
        "Authorization": `Bearer ${tokenResponse.data.access_token}`
      }
    });

    console.log(JSON.stringify(userinfoResponse.data, null, 2));
    // expect(userinfoResponse.data.verified_claims.claims).not.toBeNull();

  });

  // OpenID Connect for Identity Assurance 1.0, Section 5.7 (Returning less data than requested).
  // Test names quote the spec verbatim, as in the Section 8 block. verified_claims is requested via
  // the `claims` parameter and returned in the ID Token (handled by VerifiedClaimsCreator).
  //
  // Regression for #1512. §5.7.4 (Data not matching requirements) states:
  //   "If the respective requirement was expressed for a claim within verified_claims/verification,
  //    the OP shall omit the whole verified_claims element."
  //   "Otherwise, the OP shall omit the respective claim from the response."
  //   "In both cases, the OP shall not return an error to the RP."
  describe("Section 5.7 Returning less data than requested", () => {
    // The default end-user (ito.ichiro@gmail.com) has stored verified_claims with
    // verification.trust_framework = "eidas" and claims given_name/family_name/birthdate/address.
    // middle_name and gender are intentionally NOT stored, so requests for them are "unavailable".
    const idTokenVerifiedClaims = async (verifiedClaimsRequest) => {
      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        responseType: "code",
        state: "verified_claims_5_7",
        scope: "openid " + clientSecretPostClient.scope,
        redirectUri: clientSecretPostClient.redirectUri,
        claims: JSON.stringify({ id_token: { verified_claims: verifiedClaimsRequest } }),
      });
      expect(authorizationResponse.code).not.toBeNull();

      const tokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        code: authorizationResponse.code,
        grantType: "authorization_code",
        redirectUri: clientSecretPostClient.redirectUri,
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });
      // §5.7.4: "In both cases, the OP shall not return an error to the RP."
      expect(tokenResponse.status).toBe(200);
      expect(tokenResponse.data).toHaveProperty("id_token");

      const jwksResponse = await getJwks({ endpoint: serverConfig.jwksEndpoint });
      const decodedIdToken = verifyAndDecodeJwt({
        jwt: tokenResponse.data.id_token,
        jwks: jwksResponse.data,
      });
      expect(decodedIdToken.verifyResult).toBe(true);
      return decodedIdToken.payload;
    };

    it("If the OP does not have data about a certain claim, does not understand/support the respective claim, OPs shall omit the respective claim from any corresponding ID Token or UserInfo response.", async () => {
      // given_name is available; middle_name is not. The unavailable claim is dropped, but
      // verified_claims is still returned because at least one requested claim is available.
      const payload = await idTokenVerifiedClaims({
        verification: { trust_framework: null },
        claims: { given_name: null, middle_name: null },
      });
      console.log(JSON.stringify(payload.verified_claims, null, 2));

      expect(payload.verified_claims).toBeDefined();
      expect(payload.verified_claims.verification.trust_framework).toEqual("eidas");
      expect(payload.verified_claims.claims.given_name).toEqual("Sarah");
      expect(payload.verified_claims.claims).not.toHaveProperty("middle_name");
    });

    it("If an element is to be omitted according to the rules above, but is a requirement for a valid response, the OP shall omit its parent element as well.", async () => {
      // #1512: every requested verified claim is unavailable, so the claims element would be empty.
      // claims is a requirement for a valid verified_claims, so the whole verified_claims element is
      // omitted — it must NOT be returned as {"verification": {...}, "claims": {}}.
      const payload = await idTokenVerifiedClaims({
        verification: { trust_framework: null },
        claims: { middle_name: null, gender: null },
      });
      console.log(JSON.stringify(payload, null, 2));

      expect(payload.verified_claims).toBeUndefined();
    });

    // §5.7.4 value/values constraint enforcement (#1624). The user holds trust_framework "eidas"
    // and given_name "Sarah" / family_name "Meredyth".
    it("If the respective requirement was expressed for a claim within verified_claims/verification, the OP shall omit the whole verified_claims element.", async () => {
      // RP constrains verification.trust_framework to "gold", but the user has "eidas" → the
      // verification requirement is unmet, so the whole verified_claims is omitted.
      const payload = await idTokenVerifiedClaims({
        verification: { trust_framework: { value: "gold" } },
        claims: { given_name: null },
      });
      console.log(JSON.stringify(payload, null, 2));

      expect(payload.verified_claims).toBeUndefined();
    });

    it("Otherwise, the OP shall omit the respective claim from the response.", async () => {
      // RP constrains given_name to "Bob" (mismatch → dropped), family_name is unconstrained (kept).
      // The mismatch is on a claims element, so only that claim is omitted; verified_claims remains.
      const payload = await idTokenVerifiedClaims({
        verification: { trust_framework: null },
        claims: { given_name: { value: "Bob" }, family_name: null },
      });
      console.log(JSON.stringify(payload.verified_claims, null, 2));

      expect(payload.verified_claims).toBeDefined();
      expect(payload.verified_claims.claims.family_name).toEqual("Meredyth");
      expect(payload.verified_claims.claims).not.toHaveProperty("given_name");
    });

    it("returns the claim when its value/values constraint is satisfied", async () => {
      const payload = await idTokenVerifiedClaims({
        verification: { trust_framework: { value: "eidas" } },
        claims: { given_name: { values: ["Hanako", "Sarah"] } },
      });
      console.log(JSON.stringify(payload.verified_claims, null, 2));

      expect(payload.verified_claims).toBeDefined();
      expect(payload.verified_claims.verification.trust_framework).toEqual("eidas");
      expect(payload.verified_claims.claims.given_name).toEqual("Sarah");
    });
  });

  // OpenID Connect for Identity Assurance 1.0, Section 8 (OpenID Provider Metadata).
  // Test names quote the spec verbatim. See issue #1513.
  describe("Section 8 OpenID Provider Metadata", () => {
    let metadata;

    beforeAll(async () => {
      const response = await getConfiguration({
        endpoint: serverConfig.discoveryEndpoint,
      });
      expect(response.status).toBe(200);
      metadata = response.data;
      console.log(JSON.stringify(metadata, null, 2));
    });

    it("verified_claims_supported: Boolean value indicating support for verified_claims. If omitted, the default value is false.", () => {
      expect(metadata.verified_claims_supported).toBe(true);
    });

    it('documents_supported: Required when evidence_supported contains "document". JSON array containing all identity document types utilized by the OP for identity verification.', () => {
      expect(Array.isArray(metadata.documents_supported)).toBe(true);
      expect(metadata.documents_supported).toContain("passport");
    });

    it('documents_methods_supported: Optional. JSON array containing the verification methods the OP supports for evidences of type "document".', () => {
      expect(metadata.documents_methods_supported).toContain("pipp");
    });

    it("legacy pre-1.0 draft names id_documents_supported / id_documents_verification_methods_supported are no longer advertised", () => {
      expect(metadata).not.toHaveProperty("id_documents_supported");
      expect(metadata).not.toHaveProperty("id_documents_verification_methods_supported");
    });
  });

});

const getIdToken = async ({ client, nonce, acrValues, maxAge }) => {
  const { authorizationResponse } = await requestAuthorizations({
    endpoint: serverConfig.authorizationEndpoint,
    clientId: client.clientId,
    redirectUri: client.redirectUri,
    responseType: "code",
    scope: "openid " + client.scope,
    state: "state",
    responseMode: "query",
    nonce,
    display: "page",
    prompt: "login",
    acrValues,
    maxAge,
  });
  console.log(authorizationResponse);
  expect(authorizationResponse.code).not.toBeNull();

  const tokenResponse = await requestToken({
    endpoint: serverConfig.tokenEndpoint,
    code: authorizationResponse.code,
    grantType: "authorization_code",
    redirectUri: client.redirectUri,
    clientId: client.clientId,
    clientSecret: client.clientSecret,
  });
  console.log(tokenResponse.data);
  expect(tokenResponse.status).toBe(200);
  expect(tokenResponse.status).toBe(200);
  expect(tokenResponse.data).toHaveProperty("access_token");
  expect(tokenResponse.data.token_type).toEqual("Bearer");
  expect(tokenResponse.data).toHaveProperty("expires_in");
  expect(tokenResponse.data).toHaveProperty("scope");
  expect(tokenResponse.data).toHaveProperty("id_token");

  const jwkResponse = await getJwks({
    endpoint: serverConfig.jwksEndpoint,
  });
  const decodedIdToken = verifyAndDecodeJwt({
    jwt: tokenResponse.data.id_token,
    jwks: jwkResponse.data,
  });
  console.log(decodedIdToken);
  return decodedIdToken;
};
