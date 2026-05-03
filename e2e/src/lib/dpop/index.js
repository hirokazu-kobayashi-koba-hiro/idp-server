/**
 * RFC 9449 (DPoP) helpers for E2E tests.
 *
 * Generates ES256 key pairs, computes JWK thumbprints (jkt), and signs DPoP proof JWTs.
 *
 * @see https://www.rfc-editor.org/rfc/rfc9449.html
 */
import { v4 as uuidv4 } from "uuid";
import crypto from "crypto";

// eslint-disable-next-line no-undef
const jose = require("jose");

/**
 * Compute the access token hash (ath) for DPoP proofs sent to resource endpoints.
 *
 * <p>RFC 9449 §4.2: {@code ath = base64url(SHA-256(access_token))}.
 *
 * @param {string} accessToken
 * @returns {string} base64url-encoded SHA-256 digest
 */
export const computeAth = (accessToken) => {
  return crypto.createHash("sha256").update(accessToken).digest().toString("base64url");
};

/**
 * Generate an ES256 (P-256) key pair for use as a DPoP key.
 *
 * @returns {Promise<{publicJwk: object, privateJwk: object, privateKey: object}>}
 */
export const generateDPoPKeyPair = async () => {
  const { publicKey, privateKey } = await jose.generateKeyPair("ES256", {
    extractable: true,
  });
  const publicJwk = await jose.exportJWK(publicKey);
  const privateJwk = await jose.exportJWK(privateKey);
  return { publicJwk, privateJwk, privateKey };
};

/**
 * Compute the SHA-256 JWK thumbprint (jkt) of a public JWK per RFC 7638.
 *
 * Used for the {@code dpop_jkt} authorization request parameter (RFC 9449 §10.1) and for
 * verifying {@code cnf.jkt} on issued access tokens.
 *
 * @param {object} publicJwk
 * @returns {Promise<string>} base64url-encoded SHA-256 thumbprint
 */
export const calculateDPoPJkt = async (publicJwk) => {
  return await jose.calculateJwkThumbprint(publicJwk, "sha256");
};

/**
 * Sign a DPoP proof JWT with the given key.
 *
 * Produces a JWT with the {@code dpop+jwt} type header and the public JWK embedded in the
 * {@code jwk} JOSE header per RFC 9449 §4.2.
 *
 * @param {object} options
 * @param {object} options.privateKey - signing key (KeyLike)
 * @param {object} options.publicJwk - corresponding public JWK
 * @param {string} [options.htm="POST"] - HTTP method bound to the proof
 * @param {string} options.htu - HTTP URI bound to the proof
 * @param {string} [options.ath] - access token hash (RFC 9449 §4.2)
 * @param {object} [options.overrides] - claims to override or add (e.g. negative tests)
 * @param {object} [options.headerOverrides] - JOSE header fields to override (e.g. {alg: "none"})
 * @param {string[]} [options.omitClaims] - claim names to remove before signing
 * @returns {Promise<string>} the compact DPoP proof JWT
 */
export const createDPoPProof = async ({
  privateKey,
  publicJwk,
  htm = "POST",
  htu,
  ath,
  overrides = {},
  headerOverrides = {},
  omitClaims = [],
}) => {
  const payload = {
    jti: uuidv4(),
    htm,
    htu,
    iat: Math.floor(Date.now() / 1000),
    ...(ath ? { ath } : {}),
    ...overrides,
  };
  for (const claim of omitClaims) {
    delete payload[claim];
  }
  const header = {
    typ: "dpop+jwt",
    alg: "ES256",
    jwk: publicJwk,
    ...headerOverrides,
  };
  return await new jose.SignJWT(payload).setProtectedHeader(header).sign(privateKey);
};
