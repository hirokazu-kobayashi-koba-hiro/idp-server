/**
 * draft-ietf-oauth-attestation-based-client-auth-10 §9.8 (Trust Management and Key Resolution):
 * self-signed model.
 *
 * The specification leaves trust establishment out of scope, and §1 explicitly allows a client
 * without a backend Client Attester to perform the attester functions itself. In this mode the
 * Client Instance signs its own Client Attestation JWT with the Client Instance Key (CIK), and the
 * Authorization Server trusts the CIK it registered beforehand.
 *
 * idp-server expresses the choice with the client configuration
 * extension.client_attestation_trust_source = registered_instance_key, and the JOSE kid of the
 * Client Attestation JWT selects which registered instance to verify with.
 *
 * @see https://www.ietf.org/archive/id/draft-ietf-oauth-attestation-based-client-auth-10.html
 */
import { beforeAll, describe, expect, it } from "@jest/globals";
import { v4 as uuidv4 } from "uuid";
import * as jose from "jose";
import { deletion, get, postWithJson } from "../../lib/http";
import { requestToken } from "../../api/oauthClient";
import { adminServerConfig, backendUrl, serverConfig } from "../testConfig";
import { createJwtWithPrivateKey, generateJti } from "../../lib/jose";
import { toEpocTime } from "../../lib/util";

const ATTESTATION_TYP = "oauth-client-attestation+jwt";
const POP_TYP = "oauth-client-attestation-pop+jwt";
const ATTESTATION_HEADER = "OAuth-Client-Attestation";
const POP_HEADER = "OAuth-Client-Attestation-PoP";

let managementHeaders;
let clientId;
let instanceId;
let instanceJwk;

const generateSigningJwk = async (kid) => {
  const { privateKey } = await jose.generateKeyPair("ES256", { extractable: true });
  const jwk = await jose.exportJWK(privateKey);
  return { ...jwk, use: "sig", kid, alg: "ES256" };
};

const publicJwkOf = (privateJwk) => {
  const { d, ...publicJwk } = privateJwk;
  return publicJwk;
};

const instancesUrl = () =>
  `${backendUrl}/v1/management/tenants/${serverConfig.tenantId}/clients/${clientId}/instances`;

/** Client Instance role: self-signs the Client Attestation JWT with its own CIK. */
const createSelfSignedAttestationJwt = ({
  kid = () => instanceId,
  sub = () => clientId,
  iat = toEpocTime({ adjusted: 0 }),
  exp = toEpocTime({ adjusted: 300 }),
  cnf = () => ({ jwk: publicJwkOf(instanceJwk) }),
  signingKey = () => instanceJwk,
} = {}) => {
  const key = typeof signingKey === "function" ? signingKey() : signingKey;
  const payload = { sub: typeof sub === "function" ? sub() : sub, iat, exp };
  const cnfValue = typeof cnf === "function" ? cnf() : cnf;
  if (cnfValue) {
    payload.cnf = cnfValue;
  }
  return createJwtWithPrivateKey({
    payload,
    privateKey: { ...key, kid: typeof kid === "function" ? kid() : kid },
    algorithm: "ES256",
    additionalOptions: { header: { typ: ATTESTATION_TYP } },
  });
};

const createPopJwt = ({ signingKey = () => instanceJwk } = {}) => {
  const key = typeof signingKey === "function" ? signingKey() : signingKey;
  return createJwtWithPrivateKey({
    payload: { aud: serverConfig.issuer, jti: generateJti(), iat: toEpocTime({ adjusted: 0 }) },
    privateKey: key,
    algorithm: "ES256",
    additionalOptions: { header: { typ: POP_TYP } },
  });
};

const requestTokenWith = async ({ attestationJwt, popJwt }) =>
  await requestToken({
    endpoint: serverConfig.tokenEndpoint,
    grantType: "client_credentials",
    scope: "account",
    clientId,
    additionalHeaders: {
      [ATTESTATION_HEADER]: attestationJwt,
      [POP_HEADER]: popJwt,
    },
  });

const registerInstance = async (jwk, id = uuidv4()) => {
  const response = await postWithJson({
    url: instancesUrl(),
    headers: managementHeaders,
    body: { id, instance_key: publicJwkOf(jwk) },
  });
  expect(response.status).toBe(201);
  return id;
};

beforeAll(async () => {
  const tokenResponse = await requestToken({
    endpoint: adminServerConfig.tokenEndpoint,
    grantType: "password",
    username: adminServerConfig.oauth.username,
    password: adminServerConfig.oauth.password,
    scope: adminServerConfig.adminClient.scope,
    clientId: adminServerConfig.adminClient.clientId,
    clientSecret: adminServerConfig.adminClient.clientSecret,
  });
  expect(tokenResponse.status).toBe(200);
  managementHeaders = { Authorization: `Bearer ${tokenResponse.data.access_token}` };

  clientId = uuidv4();
  const registrationResponse = await postWithJson({
    url: `${backendUrl}/v1/management/tenants/${serverConfig.tenantId}/clients`,
    headers: managementHeaders,
    body: {
      client_id: clientId,
      client_name: "Self-signed Attestation Test Client",
      token_endpoint_auth_method: "attest_jwt_client_auth",
      extension: { client_attestation_trust_source: "registered_instance_key" },
      grant_types: ["client_credentials"],
      redirect_uris: ["http://localhost:3000/callback"],
      response_types: ["code"],
      scope: "account management",
      enabled: true,
    },
  });
  expect(registrationResponse.status).toBe(201);

  instanceJwk = await generateSigningJwk("instance-1");
  instanceId = await registerInstance(instanceJwk);
});

describe("draft-ietf-oauth-attestation-based-client-auth-10 §9.8: self-signed Client Attestation verified by a registered Client Instance Key", () => {

  describe("Client Instance management API", () => {

    it("registers a Client Instance Key and returns it in the instance list", async () => {
      const response = await get({ url: instancesUrl(), headers: managementHeaders });
      expect(response.status).toBe(200);
      const ids = response.data.list.map((instance) => instance.id);
      expect(ids).toContain(instanceId);
    });

    it("rejects an instance key containing private key material", async () => {
      const response = await postWithJson({
        url: instancesUrl(),
        headers: managementHeaders,
        // instanceJwk still holds the private component d
        body: { id: uuidv4(), instance_key: instanceJwk },
      });
      expect(response.status).toBe(400);
    });
  });

  describe("Client authentication", () => {

    it("authenticates the client when the Client Attestation JWT is self-signed by the registered Client Instance Key", async () => {
      const response = await requestTokenWith({
        attestationJwt: createSelfSignedAttestationJwt(),
        popJwt: createPopJwt(),
      });
      console.log(response.data);
      expect(response.status).toBe(200);
      expect(response.data).toHaveProperty("access_token");
    });

    it("rejects a Client Attestation JWT whose kid does not match any registered instance", async () => {
      const response = await requestTokenWith({
        attestationJwt: createSelfSignedAttestationJwt({ kid: "unknown-instance" }),
        popJwt: createPopJwt(),
      });
      expect(response.status).toBe(401);
      expect(response.data).toHaveProperty("error", "invalid_client");
    });

    it("rejects a Client Attestation JWT signed by a key that is not the registered Client Instance Key", async () => {
      const attackerJwk = await generateSigningJwk("instance-1");
      const response = await requestTokenWith({
        attestationJwt: createSelfSignedAttestationJwt({
          cnf: () => ({ jwk: publicJwkOf(attackerJwk) }),
          signingKey: () => attackerJwk,
        }),
        popJwt: createPopJwt({ signingKey: () => attackerJwk }),
      });
      expect(response.status).toBe(401);
      expect(response.data).toHaveProperty("error", "invalid_client");
    });

    it("rejects when cnf.jwk is not the registered key that signed the Client Attestation JWT", async () => {
      // signed by the registered instance key, but cnf points at another key:
      // the PoP would otherwise prove possession of a key the server never registered
      const otherJwk = await generateSigningJwk("other");
      const response = await requestTokenWith({
        attestationJwt: createSelfSignedAttestationJwt({
          cnf: () => ({ jwk: publicJwkOf(otherJwk) }),
        }),
        popJwt: createPopJwt({ signingKey: () => otherJwk }),
      });
      expect(response.status).toBe(401);
      expect(response.data).toHaveProperty("error", "invalid_client");
    });

    it("rejects a Client Attestation JWT whose lifetime (exp - iat) exceeds the server policy", async () => {
      const response = await requestTokenWith({
        attestationJwt: createSelfSignedAttestationJwt({
          exp: toEpocTime({ adjusted: 48 * 60 * 60 }),
        }),
        popJwt: createPopJwt(),
      });
      expect(response.status).toBe(401);
      expect(response.data).toHaveProperty("error", "invalid_client");
    });

    it("stops authenticating the instance as soon as it is deleted", async () => {
      const disposableJwk = await generateSigningJwk("instance-disposable");
      const disposableId = await registerInstance(disposableJwk);

      const before = await requestTokenWith({
        attestationJwt: createSelfSignedAttestationJwt({
          kid: disposableId,
          cnf: () => ({ jwk: publicJwkOf(disposableJwk) }),
          signingKey: () => disposableJwk,
        }),
        popJwt: createPopJwt({ signingKey: () => disposableJwk }),
      });
      expect(before.status).toBe(200);

      const deleteResponse = await deletion({
        url: `${instancesUrl()}/${disposableId}`,
        headers: managementHeaders,
      });
      expect(deleteResponse.status).toBe(204);

      const after = await requestTokenWith({
        attestationJwt: createSelfSignedAttestationJwt({
          kid: disposableId,
          cnf: () => ({ jwk: publicJwkOf(disposableJwk) }),
          signingKey: () => disposableJwk,
        }),
        popJwt: createPopJwt({ signingKey: () => disposableJwk }),
      });
      expect(after.status).toBe(401);
      expect(after.data).toHaveProperty("error", "invalid_client");
    });
  });
});
