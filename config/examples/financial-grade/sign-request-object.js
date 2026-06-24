#!/usr/bin/env node
/*
 * Signs a FAPI authorization request object (JAR, RFC 9101) with the client's EC private key.
 *
 * The example client (financial-client.json) is registered on a tenant with
 * require_signed_request_object = true, so every authorization request — and FAPI Advance
 * (transfers/write) in particular — must carry a JWS-signed JWT request object. The helper
 * (helpers.sh) calls this to mint that JWT and passes it by value via the `request` parameter
 * (PAR is not required on this tenant).
 *
 * Inputs come from env vars; the signed JWT is written to stdout. Uses only Node's built-in
 * crypto (no npm install). ES256 signatures are emitted in JOSE raw form (ieee-p1363), as JWS
 * requires — not DER.
 *
 *   FAPI_CLIENT_FILE      path to the client JSON holding the private jwks (signing key)
 *   FAPI_CLIENT_ID        client_id (iss / sub / client_id claim)
 *   FAPI_AUD              audience = the OP issuer (tenant base URL)
 *   FAPI_REDIRECT_URI / FAPI_SCOPE / FAPI_STATE / FAPI_NONCE / FAPI_CODE_CHALLENGE
 *   FAPI_RESPONSE_TYPE    default "code"
 */
const crypto = require("crypto");
const fs = require("fs");

const b64url = (input) =>
  Buffer.from(input)
    .toString("base64")
    .replace(/\+/g, "-")
    .replace(/\//g, "_")
    .replace(/=+$/, "");

const env = process.env;

if (!env.FAPI_CLIENT_FILE) {
  console.error("FAPI_CLIENT_FILE is required");
  process.exit(1);
}

const client = JSON.parse(fs.readFileSync(env.FAPI_CLIENT_FILE, "utf8"));
const keys = JSON.parse(client.jwks).keys;
const jwk = keys.find((k) => k.d) || keys[0];
if (!jwk || !jwk.d) {
  console.error("no private key (jwk with 'd') found in client jwks");
  process.exit(1);
}

const privateKey = crypto.createPrivateKey({ key: jwk, format: "jwk" });

const now = Math.floor(Date.now() / 1000);
const header = { alg: "ES256", typ: "JWT", kid: jwk.kid };
const payload = {
  iss: env.FAPI_CLIENT_ID,
  sub: env.FAPI_CLIENT_ID,
  aud: env.FAPI_AUD,
  client_id: env.FAPI_CLIENT_ID,
  response_type: env.FAPI_RESPONSE_TYPE || "code",
  // FAPI Advance は response_type=code id_token か、code + response_mode=jwt(JARM) を要求する。
  // JARM を使うと最終レスポンスが response=<JWT>（code を内包）で返る。
  response_mode: "jwt",
  redirect_uri: env.FAPI_REDIRECT_URI,
  scope: env.FAPI_SCOPE,
  state: env.FAPI_STATE,
  nonce: env.FAPI_NONCE,
  code_challenge: env.FAPI_CODE_CHALLENGE,
  code_challenge_method: "S256",
  iat: now,
  nbf: now,
  exp: now + 300,
  jti: crypto.randomUUID(),
};

const signingInput = `${b64url(JSON.stringify(header))}.${b64url(JSON.stringify(payload))}`;
const signature = crypto.sign("sha256", Buffer.from(signingInput), {
  key: privateKey,
  dsaEncoding: "ieee-p1363",
});

process.stdout.write(`${signingInput}.${b64url(signature)}`);
