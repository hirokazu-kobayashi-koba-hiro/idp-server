#!/usr/bin/env node
/**
 * Mints the two JWTs of Attestation-Based Client Authentication (draft-10).
 *
 * Uses only Node built-ins, so the template needs no npm install.
 *
 *   # attester_jwks: the Client Attester signs the Client Attestation JWT
 *   node mint-attestation.mjs --mode attester --client-id <id> --issuer <issuer>
 *
 *   # registered_instance_key: the instance signs its own Client Attestation JWT.
 *   # kid MUST be the instance id, it is how the server finds the registered key.
 *   node mint-attestation.mjs --mode self-signed --client-id <id> --issuer <issuer> \
 *        --instance-id <instance id>
 *
 *   # decode any JWT (this tool's output, or the one your own client produced)
 *   node mint-attestation.mjs --decode <jwt>
 *
 *   # print the public JWK to register, then exit
 *   node mint-attestation.mjs --print-jwk
 *
 *   # request_hash for the unauthenticated registration endpoint
 *   node mint-attestation.mjs --request-hash --challenge <challenge>
 *
 *   # an already expired Client Attestation JWT (to see use_fresh_attestation)
 *   node mint-attestation.mjs --mode attester --client-id <id> --issuer <issuer> --exp-offset -60
 *
 * The Client Instance Key is generated once and kept in instance-key.json so that
 * repeated runs keep presenting the same key.
 */
import crypto from "node:crypto";
import fs from "node:fs";
import path from "node:path";

const args = process.argv.slice(2);
const opt = (name, fallback) => {
  const i = args.indexOf(`--${name}`);
  return i >= 0 ? args[i + 1] : fallback;
};
const flag = (name) => args.includes(`--${name}`);

const outDir = opt("out-dir", process.env.ABCA_OUT_DIR || ".");
const instanceKeyPath = path.join(outDir, "instance-key.json");

const base64url = (buf) => Buffer.from(buf).toString("base64url");

const loadOrCreateInstanceKey = () => {
  if (fs.existsSync(instanceKeyPath)) {
    return JSON.parse(fs.readFileSync(instanceKeyPath, "utf8"));
  }
  const { publicKey, privateKey } = crypto.generateKeyPairSync("ec", { namedCurve: "P-256" });
  const jwk = {
    private: { ...privateKey.export({ format: "jwk" }), alg: "ES256", use: "sig" },
    public: { ...publicKey.export({ format: "jwk" }), alg: "ES256", use: "sig" },
  };
  fs.mkdirSync(outDir, { recursive: true });
  fs.writeFileSync(instanceKeyPath, JSON.stringify(jwk, null, 2) + "\n");
  process.stderr.write(`generated Client Instance Key: ${instanceKeyPath}\n`);
  return jwk;
};

/** ES256 JWS. dsaEncoding ieee-p1363 gives the raw r||s that JWS requires. */
const signJwt = ({ header, payload, jwk }) => {
  const key = crypto.createPrivateKey({ key: jwk, format: "jwk" });
  const signingInput = `${base64url(JSON.stringify(header))}.${base64url(JSON.stringify(payload))}`;
  const signature = crypto.sign("sha256", Buffer.from(signingInput), {
    key,
    dsaEncoding: "ieee-p1363",
  });
  return `${signingInput}.${base64url(signature)}`;
};

/**
 * Decodes without verifying. The point is to see what was actually sent, so a client
 * implementation can be compared field by field against the reference.
 */
if (flag("decode")) {
  const token = opt("decode");
  if (!token) {
    console.error("--decode requires a JWT");
    process.exit(1);
  }
  const [rawHeader, rawPayload] = token.split(".");
  if (!rawHeader || !rawPayload) {
    console.error("not a JWT");
    process.exit(1);
  }
  const decode = (part) => JSON.parse(Buffer.from(part, "base64url").toString("utf8"));
  const payload = decode(rawPayload);
  console.log("header:");
  console.log(JSON.stringify(decode(rawHeader), null, 2));
  console.log("payload:");
  console.log(JSON.stringify(payload, null, 2));
  const now = Math.floor(Date.now() / 1000);
  if (payload.iat !== undefined) {
    console.log(`iat is ${now - payload.iat}s ago`);
  }
  if (payload.exp !== undefined) {
    const left = payload.exp - now;
    console.log(left >= 0 ? `exp in ${left}s` : `exp expired ${-left}s ago`);
  }
  process.exit(0);
}

const instanceKey = loadOrCreateInstanceKey();

if (flag("print-jwk")) {
  // Only the required members, which is also what the registration request carries.
  const { kty, crv, x, y } = instanceKey.public;
  console.log(JSON.stringify({ kty, crv, x, y }));
  process.exit(0);
}

/**
 * request_hash = base64url_nopad( SHA-256( challenge_bytes || canonical_jwk_utf8 ) )
 *
 * canonical_jwk is the RFC 7638 thumbprint input: required members only, lexicographic, no
 * whitespace. The challenge is hashed as the decoded bytes, not as the base64url text.
 */
if (flag("request-hash")) {
  const challengeArg = opt("challenge");
  if (!challengeArg) {
    console.error("--challenge is required with --request-hash");
    process.exit(1);
  }
  const { kty, crv, x, y } = instanceKey.public;
  const canonicalJwk = JSON.stringify({ crv, kty, x, y });
  const digest = crypto.createHash("sha256");
  digest.update(Buffer.from(challengeArg, "base64url"));
  digest.update(Buffer.from(canonicalJwk, "utf8"));
  console.log(base64url(digest.digest()));
  process.exit(0);
}

const mode = opt("mode", "attester");
const clientId = opt("client-id");
const issuer = opt("issuer");
const challenge = opt("challenge");
const instanceId = opt("instance-id");
const lifetime = Number(opt("lifetime", "300"));
// Negative values produce an already expired Client Attestation JWT.
const expOffset = opt("exp-offset") === undefined ? null : Number(opt("exp-offset"));

if (!clientId || !issuer) {
  console.error("--client-id and --issuer are required");
  process.exit(1);
}

const now = Math.floor(Date.now() / 1000);
const attestationExp = expOffset === null ? now + lifetime : now + expOffset;
const { kty, crv, x, y } = instanceKey.public;
const cnfJwk = { kty, crv, x, y };

let attestationJwt;
if (mode === "attester") {
  const attesterPath = opt("attester-keys", path.join(outDir, "attester-keys.json"));
  if (!fs.existsSync(attesterPath)) {
    console.error(`attester keys not found: ${attesterPath}`);
    process.exit(1);
  }
  const attester = JSON.parse(fs.readFileSync(attesterPath, "utf8"));
  attestationJwt = signJwt({
    header: { typ: "oauth-client-attestation+jwt", alg: "ES256", kid: attester.private_key.kid },
    payload: {
      iss: "attester.example.com",
      sub: clientId,
      iat: now,
      exp: attestationExp,
      cnf: { jwk: cnfJwk },
    },
    jwk: attester.private_key,
  });
} else if (mode === "self-signed") {
  if (!instanceId) {
    console.error("--instance-id is required for --mode self-signed");
    process.exit(1);
  }
  attestationJwt = signJwt({
    // kid selects which registered Client Instance the server verifies with.
    header: { typ: "oauth-client-attestation+jwt", alg: "ES256", kid: instanceId },
    payload: { sub: clientId, iat: now, exp: attestationExp, cnf: { jwk: cnfJwk } },
    jwk: instanceKey.private,
  });
} else {
  console.error(`unknown --mode: ${mode}`);
  process.exit(1);
}

// Section 5.1: aud / jti / iat are the required claims. iss and exp are not defined for the PoP.
const popPayload = { aud: issuer, jti: crypto.randomUUID(), iat: now };
if (challenge) {
  popPayload.challenge = challenge;
}
const popJwt = signJwt({
  header: { typ: "oauth-client-attestation-pop+jwt", alg: "ES256" },
  payload: popPayload,
  jwk: instanceKey.private,
});

if (opt("format", "env") === "json") {
  console.log(JSON.stringify({ attestation: attestationJwt, pop: popJwt }, null, 2));
} else {
  console.log(`export OAUTH_CLIENT_ATTESTATION='${attestationJwt}'`);
  console.log(`export OAUTH_CLIENT_ATTESTATION_POP='${popJwt}'`);
}
