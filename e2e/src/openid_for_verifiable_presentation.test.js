import { describe, expect, it, xit } from "@jest/globals";

import { getJwks, requestCredentials, requestToken } from "./api/oauthClient";
import { clientSecretPostClient, serverConfig } from "./testConfig";
import { requestAuthorizations } from "./oauth";
import {
  createJwe,
  createJwt,
  createJwtWithNoneSignature,
  createJwtWithPrivateKey,
  generateJti,
  verifyAndDecodeJwt,
} from "./lib/jose";
import { toEpocTime } from "./lib/util";

describe("OpenID for Verifiable Presentations - draft 19", () => {
  xit("success pattern normal", async () => {
    const codeVerifier = "aiueo12345678";
    const codeChallenge = codeVerifier;
    const presentationDefinition =
      {
        "id": "vp token example",
        "input_descriptors": [
          {
            "id": "id card credential",
            "format": {
              "ldp_vc": {
                "proof_type": [
                  "Ed25519Signature2018"
                ]
              }
            },
            "constraints": {
              "fields": [
                {
                  "path": [
                    "$.type"
                  ],
                  "filter": {
                    "type": "string",
                    "pattern": "IDCardCredential"
                  }
                }
              ]
            }
          }
        ]
      };
    const { authorizationResponse } = await requestAuthorizations({
      client_id: clientSecretPostClient.clientId,
      responseType: "vp_token",
      state: "aiueo",
      scope: "openid profile phone email " + clientSecretPostClient.scope,
      redirectUri: clientSecretPostClient.redirectUri,
      aud: serverConfig.issuer,
      iss: clientSecretPostClient.clientId,
      endpoint: serverConfig.authorizationEndpoint,
      clientId: clientSecretPostClient.clientId,
      responseMode: "query",
      nonce: "nonce",
      display: "page",
      prompt: "login",
      codeChallenge,
      codeChallengeMethod: "plain",
      authorizationDetails: JSON.stringify(presentationDefinition),
    });
    console.log(authorizationResponse);
    expect(authorizationResponse.vpToken).not.toBeNull();

  });

});
