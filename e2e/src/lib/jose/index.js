// eslint-disable-next-line no-undef
const jwt = require("jsonwebtoken");
// eslint-disable-next-line no-undef
const jose = require("jose");
import jwkToPem from "jwk-to-pem";
import jwt_decode from "jwt-decode";
import { v4 as uuidv4 } from "uuid";
export const generateJti = () => {
  return uuidv4();
};
export const createJwt = ({ payload, secret, options }) => {
  return jwt.sign(payload, secret, options);
};

export const createJwe = async ({ text, key, enc }) => {
  const keyObject = await jose.importJWK(key);
  console.log(keyObject);
  const jwe = await new jose.CompactEncrypt(new TextEncoder().encode(text))
    .setProtectedHeader({
      kid: key.kid,
      alg: key.alg,
      enc,
      cty: "JWT",
    })
    .encrypt(keyObject);
  console.log(jwe);
  return jwe;
};

export const createJwtWithNoneSignature = ({
 payload,
 additionalOptions,
}) => {
  const none = jwt.sign(payload, "", {
    ...additionalOptions,
    algorithm: "none",
  });
  console.log(none);
  return none;
};
export const createJwtWithPrivateKey = ({
  payload,
  privateKey,
  additionalOptions,
}) => {
  const secret = jwkToPem(privateKey, { private: true });
  const options = {
    ...additionalOptions,
    keyid: privateKey.kid,
    algorithm: privateKey.alg,
  };
  return createJwt({
    payload,
    secret,
    options,
  });
};

export const decryptAndVerifyAndDecodeIdToken = async ({ idToken, privateKey, jwks }) => {
  const keyObject = await jose.importJWK(privateKey);
  console.log(keyObject);
  const { plaintext, protectedHeader } = await jose.compactDecrypt(idToken, keyObject);
  console.log(protectedHeader);
  return verifyAndDecodeJwt({
    jwt: new TextDecoder().decode(plaintext),
    jwks,
  });
};

export const verifyAndDecodeJwt = ({ jwt, jwks }) => {
  const header = jwt_decode(jwt, { header: true });
  const payload = jwt_decode(jwt);
  const jwk = jwks.keys.filter((jwk) => jwk.kid === header.kid)[0];
  const publicKey = jwkToPem(jwk);
  const verifyResult = verifySignature({ jws: jwt, publicKey });
  return {
    header,
    payload,
    verifyResult,
  };
};

export const verifySignature = ({ jws, publicKey }) => {
  try {
    jwt.verify(jws, publicKey);
    return true;
  } catch (e) {
    console.log(e);
    return false;
  }
};

/**
 * Generate RS256 key pair for JWKS
 * @returns {Promise<{publicKey: Object, privateKey: Object, jwks: string}>}
 */
export const generateRS256KeyPair = async () => {
  // Generate RS256 key pair
  const { publicKey, privateKey } = await jose.generateKeyPair("RS256", {
    modulusLength: 2048,
  });

  // Export as JWK
  const publicJwk = await jose.exportJWK(publicKey);
  const privateJwk = await jose.exportJWK(privateKey);

  // Add key ID and algorithm
  const kid = uuidv4();
  publicJwk.kid = kid;
  publicJwk.alg = "RS256";
  publicJwk.use = "sig";

  privateJwk.kid = kid;
  privateJwk.alg = "RS256";
  privateJwk.use = "sig";

  // Create JWKS with private key (server needs private key for signing)
  const jwks = {
    keys: [privateJwk]  // Include private key for token signing
  };

  return {
    publicKey: publicJwk,
    privateKey: privateJwk,
    jwks: JSON.stringify(jwks),
  };
};

/**
 * Generate EC P-256 keypair and convert to JWK format
 *
 * @param {Object} options - Configuration options
 * @param {string} options.kid - Key ID (default: "signing_key_1")
 * @param {string} options.use - Key usage (default: "sig")
 * @param {string} options.alg - Algorithm (default: "ES256")
 * @returns {Promise<string>} JSON string of JWKS (for API request body)
 *
 * @example
 * import { generateECP256JWKS } from "../lib/jose";
 * const jwks = await generateECP256JWKS();
 * // Returns: '{"keys":[{"kty":"EC","crv":"P-256",...}]}'
 *
 * const jwks = await generateECP256JWKS({ kid: "custom_key_id" });
 */
export const generateECP256JWKS = async (options = {}) => {
  const crypto = await import("crypto");

  const { kid = "signing_key_1", use = "sig", alg = "ES256" } = options;

  // Generate EC P-256 keypair using jose library for proper JWK export
  const { publicKey, privateKey } = await jose.generateKeyPair("ES256", {
    extractable: true,
  });

  // Export as JWK
  const privateJwk = await jose.exportJWK(privateKey);

  // Add metadata
  const jwk = {
    ...privateJwk,
    use,
    kid,
    alg,
  };

  const jwks = { keys: [jwk] };
  return JSON.stringify(jwks);
};

/**
 * Generate EC P-256 keypair and return as JWK object (not stringified)
 *
 * @param {Object} options - Configuration options
 * @param {string} options.kid - Key ID (default: "signing_key_1")
 * @param {string} options.use - Key usage (default: "sig")
 * @param {string} options.alg - Algorithm (default: "ES256")
 * @returns {Promise<Object>} JWKS object
 *
 * @example
 * import { generateECP256JWKSObject } from "../lib/jose";
 * const jwks = await generateECP256JWKSObject();
 * // Returns: { keys: [{ kty: "EC", crv: "P-256", ... }] }
 */
export const generateECP256JWKSObject = async (options = {}) => {
  const jwksString = await generateECP256JWKS(options);
  return JSON.parse(jwksString);
};
