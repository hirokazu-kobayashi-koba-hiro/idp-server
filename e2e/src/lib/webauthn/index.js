import crypto from "crypto";
import base64url from "base64url";
import cbor from "cbor";

// Create a SHA-256 hash of the RP ID
function createAuthenticatorData(rpId = "localhost") {
  const rpIdHash = crypto.createHash("sha256").update(rpId).digest();
  const flags = Buffer.from([0x41]); // user present + attested credential data
  const signCount = Buffer.alloc(4); // signCount = 0
  return Buffer.concat([rpIdHash, flags, signCount]);
}

// Create clientDataJSON buffer
function createClientDataJSON(challenge) {
  const json = JSON.stringify({
    type: "webauthn.create",
    challenge: base64url.encode(Buffer.from(challenge, "base64")),
    origin: "http://localhost:3000",
  });
  return Buffer.from(json);
}

// Create attestationObject with empty attStmt
function createAttestationObject(authenticatorData) {
  return cbor.encode({
    fmt: "none",
    attStmt: {},
    authData: authenticatorData,
  });
}

// Main function to generate a credential object
export function generateFakeWebAuthnCredential(challenge) {
  const authenticatorData = createAuthenticatorData("localhost");
  const clientDataJSON = createClientDataJSON(challenge);
  const attestationObject = createAttestationObject(authenticatorData);

  return {
    id: base64url.encode(crypto.randomBytes(32)),
    rawId: base64url.encode(crypto.randomBytes(32)),
    type: "public-key",
    clientExtensionResults: {},
    response: {
      clientDataJSON: base64url.encode(clientDataJSON),
      attestationObject: base64url.encode(attestationObject),
    },
  };
}
