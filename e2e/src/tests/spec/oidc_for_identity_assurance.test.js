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
