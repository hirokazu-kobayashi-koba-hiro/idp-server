// suite のテスト設定（../oidc-test/haip.json）を、pki/ の鍵と証明書から組み立てる。
// ウォレットのインスタンス鍵（client / client2）は初回だけ作って wallet-*.jwk.json に残す。
// usage: node build-test-config.mjs
import fs from "node:fs";
import crypto from "node:crypto";

const here = (p) => new URL(p, import.meta.url).pathname;
const read = (p) => fs.readFileSync(here(p), "utf8");

const TENANT_ID = "b01c0787-b7be-4699-9a5a-042f70d41697";
const ISSUER = `https://api.local.test/${TENANT_ID}`;

function walletKey(name) {
  const file = here(`${name}.jwk.json`);
  if (!fs.existsSync(file)) {
    const { privateKey } = crypto.generateKeyPairSync("ec", { namedCurve: "P-256" });
    const jwk = { ...privateKey.export({ format: "jwk" }), kid: name, use: "sig", alg: "ES256" };
    fs.writeFileSync(file, JSON.stringify(jwk, null, 2) + "\n");
  }
  return JSON.parse(fs.readFileSync(file, "utf8"));
}

const issuerCa = read("issuer-ca.pem");

const config = {
  alias: "idp-server-oid4vci-haip",
  description: "OID4VCI 1.0 Final / HAIP issuer test against local idp-server (attest_jwt_client_auth + DPoP)",
  vci: {
    credential_issuer_url: ISSUER,
    credential_configuration_id: "identity_credential",
  },
  client: {
    client_id: "876cfb14-9fef-4436-a3b0-7282879164f4",
    jwks: { keys: [walletKey("wallet-instance-1")] },
  },
  client2: {
    client_id: "f4840d7f-b58f-4f1c-9f65-ba3ea2ce1f54",
    jwks: { keys: [walletKey("wallet-instance-2")] },
  },
  client_attestation: {
    issuer: "https://wallet-provider.idp-server.example/",
    attester_jwks: { keys: [JSON.parse(read("attester.jwk.json"))] },
  },
  server: {
    // OIDC4IDA のメタデータ。suite のスキーマが知らないだけで、仕様にある拡張
    allow_unexpected_metadata_fields: ["verified_claims_supported"],
  },
  credential: {
    trust_anchor_pem: issuerCa,
    // Status List は使っていないが、HAIP のプランは未設定だと失敗にする
    status_list_trust_anchor_pem: issuerCa,
  },
};

fs.writeFileSync(here("../oidc-test/haip.json"), JSON.stringify(config, null, 2) + "\n");
console.log("wrote oidc-test/haip.json");
