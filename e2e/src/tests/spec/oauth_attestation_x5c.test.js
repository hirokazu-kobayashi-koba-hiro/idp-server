/**
 * draft-ietf-oauth-attestation-based-client-auth-11 §10.8 (Trust Management and Key Resolution):
 * certificate chain model.
 *
 * The specification leaves establishing trust in the Client Attester out of scope, but §10.8 names
 * the shapes it can take, and the `x5c` header parameter is one of them: "conveys an X.509
 * certificate chain in the JOSE header of each Client Attestation. Trust is established by
 * validating the chain against a configured trust anchor."
 *
 * What this buys over attester_jwks is whose problem key rotation is. With a configured JWKS an
 * attester replacing its signing key means editing every client that trusts it; with a chain the
 * root outlives the key. Deployments built on a certificate hierarchy are shaped this way — the
 * EUDI Wallet's Wallet Instance Attestation is an oauth-client-attestation+jwt carrying the Wallet
 * Provider's certificate in x5c.
 *
 * idp-server expresses the choice with extension.client_attestation_trust_source = x5c and
 * extension.client_attestation_trusted_root_certificates.
 *
 * @see https://www.ietf.org/archive/id/draft-ietf-oauth-attestation-based-client-auth-11.html
 */
import { beforeAll, describe, expect, it } from "@jest/globals";
import { v4 as uuidv4 } from "uuid";
import { postWithJson } from "../../lib/http";
import { requestToken } from "../../api/oauthClient";
import { adminServerConfig, backendUrl, serverConfig } from "../testConfig";
import { createJwtWithPrivateKey, generateJti } from "../../lib/jose";
import { toEpocTime } from "../../lib/util";
import {
  generateAttesterRoot,
  issueAttesterCertificate,
} from "../../lib/attester/certificateChain";

const ATTESTATION_TYP = "oauth-client-attestation+jwt";
const POP_TYP = "oauth-client-attestation-pop+jwt";
const ATTESTATION_HEADER = "OAuth-Client-Attestation";
const POP_HEADER = "OAuth-Client-Attestation-PoP";

let managementHeaders;
let root;
let otherRoot;
let clientId;
let instanceJwk;

const publicJwkOf = (privateJwk) => {
  const { d, p, q, dp, dq, qi, ...publicJwk } = privateJwk;
  return publicJwk;
};

/** The Client Attester signs, and carries its chain in x5c rather than a kid. */
const createAttestationJwt = ({ attester, x5c, signingKey }) =>
  createJwtWithPrivateKey({
    payload: {
      sub: clientId,
      iat: toEpocTime({ adjusted: 0 }),
      exp: toEpocTime({ adjusted: 300 }),
      cnf: { jwk: publicJwkOf(instanceJwk) },
    },
    privateKey: signingKey || attester.privateJwk,
    algorithm: "RS256",
    additionalOptions: {
      header: x5c ? { typ: ATTESTATION_TYP, x5c } : { typ: ATTESTATION_TYP },
    },
  });

/** The Client Instance proves possession of the key named in cnf. */
const createPopJwt = () =>
  createJwtWithPrivateKey({
    payload: {
      aud: serverConfig.issuer,
      jti: generateJti(),
      iat: toEpocTime({ adjusted: 0 }),
    },
    privateKey: instanceJwk,
    algorithm: "ES256",
    additionalOptions: { header: { typ: POP_TYP } },
  });

/**
 * Every trust failure in this file lands on the same error code, so the reason is asserted too:
 * without it a test named after one way of breaking the chain passes when another one refuses
 * the request.
 */
const expectNoTrustedKey = (response) => {
  expect(response.status).toBe(401);
  expect(response.data.error).toBe("invalid_client_attestation");
  expect(response.data.error_description).toContain(
    "reason=no trusted client attestation key is available for the client"
  );
};

const requestTokenWith = async (attestationJwt) =>
  await requestToken({
    endpoint: serverConfig.tokenEndpoint,
    grantType: "client_credentials",
    scope: "account",
    clientId,
    additionalHeaders: {
      [ATTESTATION_HEADER]: attestationJwt,
      [POP_HEADER]: createPopJwt(),
    },
  });

const registerClient = async (trustedRoots) => {
  const id = uuidv4();
  const response = await postWithJson({
    url: `${backendUrl}/v1/management/tenants/${serverConfig.tenantId}/clients`,
    headers: managementHeaders,
    body: {
      client_id: id,
      client_name: "x5c Attestation Test Client",
      token_endpoint_auth_method: "attest_jwt_client_auth",
      extension: {
        client_attestation_trust_source: "x5c",
        client_attestation_trusted_root_certificates: trustedRoots,
      },
      grant_types: ["client_credentials"],
      redirect_uris: ["http://localhost:3000/callback"],
      response_types: ["code"],
      scope: "account management",
      enabled: true,
    },
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

  root = generateAttesterRoot();
  otherRoot = generateAttesterRoot();
  clientId = await registerClient([root.base64Der]);

  const jose = await import("jose");
  const { privateKey } = await jose.generateKeyPair("ES256", {
    extractable: true,
  });
  instanceJwk = await jose.exportJWK(privateKey);
}, 120000);

describe("draft-ietf-oauth-attestation-based-client-auth-11 §10.8: trust established by validating the x5c chain against a configured trust anchor", () => {
  describe("a chain that leads to the configured trust anchor", () => {
    it("authenticates the client", async () => {
      const attester = await issueAttesterCertificate({ root, name: "attester-1" });

      const response = await requestTokenWith(
        createAttestationJwt({ attester, x5c: attester.x5c })
      );
      console.log("x5c chain including the root:", response.status);

      expect(response.status).toBe(200);
      expect(response.data).toHaveProperty("access_token");
    }, 120000);

    it("authenticates when the chain omits the trust anchor, as HAIP requires", async () => {
      // HAIP: "The X.509 certificate of the trust anchor MUST NOT be included". The server holds
      // the root from configuration, so a chain that stops below it verifies just the same.
      const attester = await issueAttesterCertificate({ root, name: "attester-1" });

      const response = await requestTokenWith(
        createAttestationJwt({ attester, x5c: attester.x5cWithoutRoot })
      );
      console.log("x5c chain without the root:", response.status);

      expect(response.status).toBe(200);
    }, 120000);

    it("accepts a signing key the deployment has never seen, under the same root", async () => {
      // This is what the mode is for. Nothing about the client's configuration changes when the
      // attester rotates — which with attester_jwks would mean editing every client that trusts it.
      const rotated = await issueAttesterCertificate({ root, name: "attester-2" });

      const response = await requestTokenWith(
        createAttestationJwt({ attester: rotated, x5c: rotated.x5c })
      );
      console.log("rotated attester certificate:", response.status);

      expect(response.status).toBe(200);
    }, 120000);
  });

  describe("a chain that does not", () => {
    it("rejects a chain built under another root", async () => {
      // The attacker signs their own chain. Everything inside it is theirs to choose, so only the
      // root check decides anything.
      const forged = await issueAttesterCertificate({
        root: otherRoot,
        name: "attacker",
      });

      const response = await requestTokenWith(
        createAttestationJwt({ attester: forged, x5c: forged.x5c })
      );
      console.log("chain under another root:", response.status, JSON.stringify(response.data));

      expectNoTrustedKey(response);
    }, 120000);

    it("rejects an attestation carrying no chain", async () => {
      const attester = await issueAttesterCertificate({ root, name: "attester-1" });

      const response = await requestTokenWith(
        createAttestationJwt({ attester, x5c: null })
      );

      expectNoTrustedKey(response);
    }, 120000);

    it("rejects a chain whose leaf did not sign the attestation", async () => {
      const attester = await issueAttesterCertificate({ root, name: "attester-1" });
      const other = await issueAttesterCertificate({ root, name: "attester-2" });

      const response = await requestTokenWith(
        createAttestationJwt({
          attester,
          x5c: attester.x5c,
          signingKey: other.privateJwk,
        })
      );

      // The chain verifies to the root, so the resolver hands back the leaf key; the JOSE layer
      // is what refuses, and its reason is different from the trust failures above.
      expect(response.status).toBe(401);
      expect(response.data.error_description).toContain(
        "reason=client attestation jwt validation failed: invalid signature"
      );
    }, 120000);
  });

  describe("a client with no trust anchor configured", () => {
    it("rejects every chain rather than trusting the one presented", async () => {
      const unconfigured = await registerClient([]);
      const attester = await issueAttesterCertificate({ root, name: "attester-1" });

      const attestationJwt = createJwtWithPrivateKey({
        payload: {
          sub: unconfigured,
          iat: toEpocTime({ adjusted: 0 }),
          exp: toEpocTime({ adjusted: 300 }),
          cnf: { jwk: publicJwkOf(instanceJwk) },
        },
        privateKey: attester.privateJwk,
        algorithm: "RS256",
        additionalOptions: {
          header: { typ: ATTESTATION_TYP, x5c: attester.x5c },
        },
      });

      const response = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        grantType: "client_credentials",
        scope: "account",
        clientId: unconfigured,
        additionalHeaders: {
          [ATTESTATION_HEADER]: attestationJwt,
          [POP_HEADER]: createPopJwt(),
        },
      });
      console.log("no trust anchor configured:", response.status);

      expectNoTrustedKey(response);
    }, 120000);
  });
});
