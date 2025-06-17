import { describe, expect, it, xit } from "@jest/globals";

import { getJwks, requestBatchCredentials, requestCredentials, requestToken } from "../../api/oauthClient";
import { clientSecretPostClient, serverConfig } from "../testConfig";
import { requestAuthorizations } from "../../oauth/signin";
import {
  createJwe,
  createJwt,
  createJwtWithNoneSignature,
  createJwtWithPrivateKey,
  generateJti,
  verifyAndDecodeJwt,
} from "../../lib/jose";
import { toEpocTime, toJsonString } from "../../lib/util";
import {decodeWithBase58, verifyBlockCert} from "../../lib/vc";

xdescribe("OpenID for Verifiable Credential Issuance - draft 13", () => {
  it("did-vc success pattern normal", async () => {
    const codeVerifier = "aiueo12345678";
    const codeChallenge = codeVerifier;
    const authorizationDetails = [
      {
        "type": "openid_credential",
        "locations": [
          "https://credential-issuer.example.com"
        ],
        "format": "jwt_vc_json",
        "credential_definition": {
          "type": [
            "VerifiableCredential",
            "UniversityDegreeCredential"
          ]
        }
      }
    ];
    const { authorizationResponse } = await requestAuthorizations({
      client_id: clientSecretPostClient.clientId,
      responseType: "code",
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
      authorizationDetails: JSON.stringify(authorizationDetails),
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
      codeVerifier,
    });
    console.log(JSON.stringify(tokenResponse.data));
    expect(tokenResponse.status).toBe(200);
    expect(tokenResponse.data).toHaveProperty("id_token");

    const jwksResponse = await getJwks({ endpoint: serverConfig.jwksEndpoint });
    console.log(jwksResponse.data);
    expect(jwksResponse.status).toBe(200);

    const decodedIdToken = verifyAndDecodeJwt({
      jwt: tokenResponse.data.id_token,
      jwks: jwksResponse.data,
    });
    console.log(decodedIdToken);
    const credentialResponse = await requestCredentials({
      endpoint: serverConfig.credentialEndpoint,
      params: {
        format: "jwt_vc_json",
      },
      authorizationHeader: {
        Authorization: `Bearer ${tokenResponse.data.access_token}`
      }
    });
    console.log(credentialResponse.data);
    expect(credentialResponse.status).toBe(200);
  });

  it("block-cert-vc success pattern normal", async () => {
    const codeVerifier = "aiueo12345678";
    const codeChallenge = codeVerifier;
    const authorizationDetails = [
      {
        "type": "openid_credential",
        "locations": [
          "https://credential-issuer.example.com"
        ],
        "format": "ldp_vc",
        "credential_definition": {
          "type": [
            "VerifiableCredential",
            "UniversityDegreeCredential"
          ]
        }
      }
    ];
    const { authorizationResponse } = await requestAuthorizations({
      client_id: clientSecretPostClient.clientId,
      responseType: "code",
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
      authorizationDetails: JSON.stringify(authorizationDetails),
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
      codeVerifier,
    });
    console.log(JSON.stringify(tokenResponse.data));
    expect(tokenResponse.status).toBe(200);
    expect(tokenResponse.data).toHaveProperty("id_token");

    const jwksResponse = await getJwks({ endpoint: serverConfig.jwksEndpoint });
    console.log(jwksResponse.data);
    expect(jwksResponse.status).toBe(200);

    const decodedIdToken = verifyAndDecodeJwt({
      jwt: tokenResponse.data.id_token,
      jwks: jwksResponse.data,
    });
    console.log(decodedIdToken);
    const credentialResponse = await requestCredentials({
      endpoint: serverConfig.credentialEndpoint,
      params: {
        format: "ldp_vc",
      },
      authorizationHeader: {
        Authorization: `Bearer ${tokenResponse.data.access_token}`
      }
    });
    console.log(toJsonString(credentialResponse.data));
    expect(credentialResponse.status).toBe(200);

    decodeWithBase58(credentialResponse.data.credential.proof.proofValue)

    const verifyResult = await verifyBlockCert(credentialResponse.data.credential);
    console.log(verifyResult);
    expect(verifyResult.status).toEqual("success");
  });

  it("success pattern request object", async () => {
    const codeVerifier = "aiueo12345678";
    const codeChallenge = codeVerifier;
    const requestObject = createJwtWithPrivateKey({
      payload: {
        client_id: clientSecretPostClient.clientId,
        response_type: "code",
        state: "aiueo",
        scope: "openid profile phone email " + clientSecretPostClient.scope,
        redirect_uri: clientSecretPostClient.redirectUri,
        aud: serverConfig.issuer,
        iss: clientSecretPostClient.clientId,
        exp: toEpocTime({ adjusted: 3000 }),
        iat: toEpocTime({}),
        nbf: toEpocTime({}),
        jti: generateJti(),
        response_mode: "query",
        nonce: "nonce",
        display: "page",
        prompt: "login",
        code_challenge: codeChallenge,
        code_challenge_method: "plain",
        login_hint: serverConfig.oauth.username,
        acr_values: serverConfig.acr,
        authorization_details: [
          {
            "type":"openid_credential",
            "format": "ldp_vc",
            "credential_definition": {
              "@context": [
                "https://www.w3.org/2018/credentials/v1",
                "https://www.w3.org/2018/credentials/examples/v1"
              ],
              "type": [
                "VerifiableCredential",
                "UniversityDegreeCredential"
              ]
            }
          },
          {
            "type":"openid_credential",
            "format": "mso_mdoc",
            "doctype":"org.iso.18013.5.1.mDL"
          }
        ]
      },
      privateKey: clientSecretPostClient.requestKey,
    });

    const { authorizationResponse } = await requestAuthorizations({
      endpoint: serverConfig.authorizationEndpoint,
      request: requestObject,
      clientId: clientSecretPostClient.clientId,
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
      codeVerifier,
    });
    console.log(JSON.stringify(tokenResponse.data));
    expect(tokenResponse.status).toBe(200);
    expect(tokenResponse.data).toHaveProperty("id_token");

    const jwksResponse = await getJwks({ endpoint: serverConfig.jwksEndpoint });
    console.log(jwksResponse.data);
    expect(jwksResponse.status).toBe(200);

    const decodedIdToken = verifyAndDecodeJwt({
      jwt: tokenResponse.data.id_token,
      jwks: jwksResponse.data,
    });
    console.log(decodedIdToken);
  });

  describe("7.2. Credential Request", () => {
    it("format REQUIRED. Format of the Credential to be issued. This Credential format identifier determines further parameters required to determine the type and (optionally) the content of the credential to be issued. Credential Format Profiles consisting of the Credential format specific set of parameters are defined in Appendix E.", async () => {
      const authorizationDetails = [
        {
          "type": "openid_credential",
          "locations": [
            "https://credential-issuer.example.com"
          ],
          "format": "jwt_vc_json",
          "credential_definition": {
            "type": [
              "VerifiableCredential",
              "UniversityDegreeCredential"
            ]
          }
        }
      ];
      const token = await getToken({authorizationDetails});
      const credentialResponse = await requestCredentials({
        endpoint: serverConfig.credentialEndpoint,
        params: {
          format: "",
        },
        authorizationHeader: {
          Authorization: `Bearer ${token}`
        }
      });
      console.log(credentialResponse.data);
      expect(credentialResponse.status).toBe(400);
      expect(credentialResponse.data.error).toEqual("invalid_request");
      expect(credentialResponse.data.error_description).toBe("credential request must contains format");
    });

    it("proof OPTIONAL. JSON object containing proof of possession of the key material the issued Credential shall be bound to. The proof object MUST contain a following claim: proof_type: REQUIRED. JSON string denoting the key proof type. The value of this claim determines other claims in the key proof object and its respective processing rules. Key proof types defined in this specification can be found in Section 7.2.1.", async () => {
      const authorizationDetails = [
        {
          "type": "openid_credential",
          "locations": [
            "https://credential-issuer.example.com"
          ],
          "format": "jwt_vc_json",
          "credential_definition": {
            "type": [
              "VerifiableCredential",
              "UniversityDegreeCredential"
            ]
          }
        }
      ];
      const token = await getToken({authorizationDetails});
      const credentialResponse = await requestCredentials({
        endpoint: serverConfig.credentialEndpoint,
        params: {
          format: "jwt_vc_json",
          proof: {
            proof_type: "undefined",
          }
        },
        authorizationHeader: {
          Authorization: `Bearer ${token}`
        }
      });
      console.log(credentialResponse.data);
      expect(credentialResponse.status).toBe(400);
      expect(credentialResponse.data.error).toEqual("invalid_request");
      expect(credentialResponse.data.error_description).toBe("When credential request contains proof, proof entity must define proof_type");
    });

  });

  describe("7.2.1. Key Proof Types", () => {
    it("When proof_type is jwt, a proof object MUST include a jwt claim containing a JWT defined in Section 7.2.1.1.", async () => {
      const authorizationDetails = [
        {
          "type": "openid_credential",
          "locations": [
            "https://credential-issuer.example.com"
          ],
          "format": "jwt_vc_json",
          "credential_definition": {
            "type": [
              "VerifiableCredential",
              "UniversityDegreeCredential"
            ]
          }
        }
      ];
      const token = await getToken({authorizationDetails});
      const credentialResponse = await requestCredentials({
        endpoint: serverConfig.credentialEndpoint,
        params: {
          format: "jwt_vc_json",
          proof: {
            proof_type: "jwt",
          }
        },
        authorizationHeader: {
          Authorization: `Bearer ${token}`
        }
      });
      console.log(credentialResponse.data);
      expect(credentialResponse.status).toBe(400);
      expect(credentialResponse.data.error).toEqual("invalid_request");
      expect(credentialResponse.data.error_description).toBe("When credential request proof_type is jwt, proof entity must contains jwt claim");
    });

    it("When proof_type is cwt, a proof object MUST include a cwt claim containing a CWT defined in Section 7.2.1.2.", async () => {
      const authorizationDetails = [
        {
          "type": "openid_credential",
          "locations": [
            "https://credential-issuer.example.com"
          ],
          "format": "jwt_vc_json",
          "credential_definition": {
            "type": [
              "VerifiableCredential",
              "UniversityDegreeCredential"
            ]
          }
        }
      ];
      const token = await getToken({authorizationDetails});
      const credentialResponse = await requestCredentials({
        endpoint: serverConfig.credentialEndpoint,
        params: {
          format: "jwt_vc_json",
          proof: {
            proof_type: "cwt",
          }
        },
        authorizationHeader: {
          Authorization: `Bearer ${token}`
        }
      });
      console.log(credentialResponse.data);
      expect(credentialResponse.status).toBe(400);
      expect(credentialResponse.data.error).toEqual("invalid_request");
      expect(credentialResponse.data.error_description).toBe("When credential request proof_type is cwt, proof entity must contains cwt claim");
    });
  });

  describe("8. Batch Credential Endpoint", () => {
    it("8.1. Batch Credential Request success", async () => {
      const authorizationDetails = [
        {
          "type": "openid_credential",
          "locations": [
            "https://credential-issuer.example.com"
          ],
          "format": "jwt_vc_json",
          "credential_definition": {
            "type": [
              "VerifiableCredential",
              "UniversityDegreeCredential"
            ]
          }
        }
      ];
      const token = await getToken({authorizationDetails});
      const credentialResponse = await requestBatchCredentials({
        endpoint: serverConfig.credentialBatchEndpoint,
        params: {
          credential_requests: [{
            format: "jwt_vc_json",
          }]
        },
        authorizationHeader: {
          Authorization: `Bearer ${token}`
        }
      });
      console.log(credentialResponse.data);
      expect(credentialResponse.status).toBe(200);
    });
  });

});

const getToken = async ({ authorizationDetails}) => {
  const codeVerifier = "aiueo12345678";
  const codeChallenge = codeVerifier;

  const { authorizationResponse } = await requestAuthorizations({
    client_id: clientSecretPostClient.clientId,
    responseType: "code",
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
    authorizationDetails: JSON.stringify(authorizationDetails),
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
    codeVerifier,
  });
  console.log(JSON.stringify(tokenResponse.data));
  expect(tokenResponse.status).toBe(200);

  return tokenResponse.data.access_token;
};