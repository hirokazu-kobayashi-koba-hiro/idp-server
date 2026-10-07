#!/usr/bin/env node
/**
 * Generate an attestation client matrix (trust source × signing algorithm)
 *
 * scenario-17-token-attest-matrix.js の入力を用意する。
 * 「クライアント認証で増えるコストが、署名アルゴリズムと trust source でどう変わるか」を
 * 測るためのハーネス。
 *
 * 2 軸:
 *   trust source
 *     - registered_instance_key: Client Instance が自己署名。サーバは kid で client_instance を引く
 *     - attester_jwks:           Attester が署名。公開鍵はクライアント設定にあるので DB を引かない
 *     → 2 つの差が client_instance の鍵解決コストになる
 *   signing algorithm
 *     - Client Attestation JWT と PoP JWT の両方を同じ alg で署名する
 *     → ES / RSA / EdDSA で検証コストがどう変わるかを見る
 *
 * PoP JWT はリクエストごとに 1 つ署名する（jti のリプレイ検出、#1893。理由は
 * generate-client-instances.js と同じ）。組み合わせ × --requests 件を署名するので、
 * テスト直前は --only で流す組み合わせだけを署名し直すこと。
 * ⚠️ 署名から窓（--window、既定 600 秒）を過ぎると 401 になる。
 *
 * Usage:
 *   NODE_EXTRA_CA_CERTS="$(mkcert -CAROOT)/rootCA.pem" \
 *     node performance-test/scripts/generate-attestation-matrix.js
 *
 *   # テスト直前: 流す組み合わせだけ署名し直す
 *   node performance-test/scripts/generate-attestation-matrix.js --resign --only attester_jwks/RS256
 *
 * Options:
 *   --instances <n>   組み合わせごとのインスタンス数（デフォルト: 20）
 *   --algs <list>     カンマ区切り（デフォルト: ES256,ES384,ES512,RS256,PS256,EdDSA）
 *   --rsa-bits <n>    RSA 系の modulus 長（デフォルト: 2048）
 *   --requests <n>    組み合わせごとに署名する PoP JWT の数 = 流せるリクエストの上限（デフォルト: 50000）
 *   --window <s>      テナントの PoP JWT の iat 許容窓（秒）。Client Attestation JWT の exp にも使う（デフォルト: 600）
 *   --only <list>     署名する組み合わせ（trust_source/alg のカンマ区切り）。省略時は全部
 *   --resign          登録をスキップし、保存済みの鍵から JWT を再生成する
 *
 * Output:
 *   - performance-test/data/performance-test-client-instances-matrix.json       組み合わせ・インスタンス・Client Attestation JWT
 *   - performance-test/data/performance-test-client-instances-matrix-pops.json  署名した組み合わせの PoP JWT（k6 の SharedArray で読む）
 */

const path = require("path");
const fs = require("fs");
const crypto = require("crypto");

const projectRoot = path.resolve(__dirname, "../..");
module.paths.unshift(path.join(projectRoot, "e2e/node_modules"));

const jose = require("jose");
const { signPopPool, DEFAULT_WINDOW_SECONDS } = require("./attestation-pop-pool");

const performanceTestDataDir = path.join(projectRoot, "performance-test/data");
const tenantDataPath = path.join(performanceTestDataDir, "performance-test-multi-tenant-users.json");
const outputPath = path.join(
  performanceTestDataDir,
  "performance-test-client-instances-matrix.json"
);
const popsPath = path.join(
  performanceTestDataDir,
  "performance-test-client-instances-matrix-pops.json"
);

const ATTESTATION_TYP = "oauth-client-attestation+jwt";
const TRUST_SOURCES = ["registered_instance_key", "attester_jwks"];

// =============================================================================
// Arguments
// =============================================================================
const args = process.argv.slice(2);
const argValue = (name, fallback) => {
  const index = args.indexOf(name);
  return index >= 0 && args[index + 1] ? args[index + 1] : fallback;
};
const instanceCount = parseInt(argValue("--instances", "20"), 10);
const rsaBits = parseInt(argValue("--rsa-bits", "2048"), 10);
const algs = argValue("--algs", "ES256,ES384,ES512,RS256,PS256,EdDSA").split(",");
const resignOnly = args.includes("--resign");
const requestCount = parseInt(argValue("--requests", "50000"), 10);
const windowSeconds = parseInt(argValue("--window", String(DEFAULT_WINDOW_SECONDS)), 10);
const only = argValue("--only", "")
  .split(",")
  .filter((key) => key.length > 0);
const isSelected = (key) => only.length === 0 || only.includes(key);

// =============================================================================
// Helpers
// =============================================================================
function loadEnv() {
  const envPath = path.join(projectRoot, ".env");
  if (!fs.existsSync(envPath)) {
    console.error(`.env not found: ${envPath}`);
    process.exit(1);
  }
  const env = {};
  for (const line of fs.readFileSync(envPath, "utf-8").split("\n")) {
    const match = line.match(/^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)\s*$/);
    if (!match) continue;
    env[match[1]] = match[2].replace(/^["']|["']$/g, "");
  }
  return env;
}

const publicJwkOf = (privateJwk) => {
  const { d, p, q, dp, dq, qi, ...publicJwk } = privateJwk;
  return publicJwk;
};

async function generateJwk(alg, kid) {
  const options = { extractable: true };
  if (alg === "RS256" || alg === "PS256") {
    options.modulusLength = rsaBits;
  }
  if (alg === "EdDSA") {
    options.crv = "Ed25519";
  }
  const { privateKey } = await jose.generateKeyPair(alg, options);
  return { ...(await jose.exportJWK(privateKey)), use: "sig", kid, alg };
}

async function postJson(url, body, headers = {}) {
  const response = await fetch(url, {
    method: "POST",
    headers: { "Content-Type": "application/json", ...headers },
    body: JSON.stringify(body),
  });
  return { status: response.status, data: await response.json().catch(() => ({})) };
}

async function fetchAdminToken(env, baseUrl) {
  const response = await fetch(`${baseUrl}/${env.ADMIN_TENANT_ID}/v1/tokens`, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "password",
      username: env.ADMIN_USER_EMAIL,
      password: env.ADMIN_USER_PASSWORD,
      scope: "management",
      client_id: env.ADMIN_CLIENT_ID,
      client_secret: env.ADMIN_CLIENT_SECRET,
    }),
  });
  const data = await response.json();
  if (response.status !== 200) {
    console.error(`Failed to get admin token: ${response.status} ${JSON.stringify(data)}`);
    process.exit(1);
  }
  return data.access_token;
}

// =============================================================================
// Signing
// =============================================================================
/**
 * registered_instance_key では Client Instance が自身の鍵で自己署名し、サーバは kid
 * （= client_instance.id）で登録済みの鍵を引く。attester_jwks では Attester の鍵で署名し、
 * サーバはクライアント設定の JWKS から kid で引くので DB アクセスが発生しない。
 *
 * Client Attestation JWT はインスタンスごとに 1 つ、PoP JWT はリクエストごとに 1 つ。
 * k 番目の PoP JWT は instances[k % instances.length] の鍵で署名する。
 *
 * @returns {Promise<string[]>} PoP JWT
 */
async function signAll(entry) {
  const now = Math.floor(Date.now() / 1000);
  const selfSigned = entry.trustSource === "registered_instance_key";
  const attesterKey = selfSigned ? null : await jose.importJWK(entry.attesterJwk, entry.alg);

  const signers = [];
  for (const instance of entry.instances) {
    const instanceKey = await jose.importJWK(instance.jwk, entry.alg);

    entry.attestationKid = selfSigned ? instance.instanceId : entry.attesterJwk.kid;
    instance.attestationJwt = await new jose.SignJWT({
      sub: entry.clientId,
      iat: now,
      exp: now + windowSeconds,
      cnf: { jwk: publicJwkOf(instance.jwk) },
    })
      .setProtectedHeader({
        alg: entry.alg,
        typ: ATTESTATION_TYP,
        kid: selfSigned ? instance.instanceId : entry.attesterJwk.kid,
      })
      .sign(selfSigned ? instanceKey : attesterKey);
    delete instance.popJwt;
    signers.push({ key: instanceKey });
  }

  const popJwts = await signPopPool({
    jose,
    signers,
    alg: entry.alg,
    issuer: entry.issuer,
    now,
    count: requestCount,
  });
  entry.generatedAt = now;
  entry.popWindowSeconds = windowSeconds;
  entry.popCount = popJwts.length;
  return popJwts;
}

/** Signs the selected combinations and writes both files. */
async function signSelected(entries) {
  const pops = [];
  for (const entry of entries) {
    if (!isSelected(entry.key)) continue;
    const popJwts = await signAll(entry);
    pops.push({ key: entry.key, generatedAt: entry.generatedAt, popJwts });
    console.log(`  signed ${entry.key}: ${popJwts.length} PoP JWTs`);
  }
  if (pops.length === 0) {
    console.error(`No combination matched --only ${only.join(",")}`);
    process.exit(1);
  }
  fs.writeFileSync(outputPath, JSON.stringify(entries, null, 2));
  fs.writeFileSync(popsPath, JSON.stringify(pops));
  console.log(`\nWritten: ${outputPath}`);
  console.log(`Written: ${popsPath} (${pops.map((entry) => entry.key).join(", ")})`);
  console.log(
    `\n⚠️  JWT は ${windowSeconds} 秒で受け付けられなくなる。テストはこの直後に実行し、その内側で終わらせること。`
  );
  console.log(`    流せるリクエストは組み合わせごとに最大 ${requestCount} 件（--requests で変更）。`);
}

// =============================================================================
// Main
// =============================================================================
async function main() {
  if (resignOnly) {
    if (!fs.existsSync(outputPath)) {
      console.error(`Nothing to re-sign: ${outputPath} not found. Run without --resign first.`);
      process.exit(1);
    }
    const existing = JSON.parse(fs.readFileSync(outputPath, "utf-8"));
    await signSelected(existing);
    return;
  }

  if (!fs.existsSync(tenantDataPath)) {
    console.error(`Tenant data not found: ${tenantDataPath}`);
    console.error("Please run: ./performance-test/scripts/register-tenants.sh -n 1");
    process.exit(1);
  }

  const env = loadEnv();
  const baseUrl = env.AUTHORIZATION_SERVER_URL || "http://localhost:8080";
  const tenant = JSON.parse(fs.readFileSync(tenantDataPath, "utf-8"))[0];
  const accessToken = await fetchAdminToken(env, baseUrl);
  const managementHeaders = { Authorization: `Bearer ${accessToken}` };

  const entries = [];
  for (const trustSource of TRUST_SOURCES) {
    for (const alg of algs) {
      const clientId = crypto.randomUUID();
      const selfSigned = trustSource === "registered_instance_key";
      const attesterJwk = selfSigned ? null : await generateJwk(alg, `attester-${alg}`);

      const extension = { client_attestation_trust_source: trustSource };
      if (!selfSigned) {
        extension.client_attestation_attester_jwks = JSON.stringify({
          keys: [publicJwkOf(attesterJwk)],
        });
      }

      const clientResponse = await postJson(
        `${baseUrl}/v1/management/tenants/${tenant.tenantId}/clients`,
        {
          client_id: clientId,
          client_name: `Perf Attestation ${trustSource} ${alg}`,
          token_endpoint_auth_method: "attest_jwt_client_auth",
          extension,
          grant_types: ["client_credentials"],
          redirect_uris: ["http://localhost:3000/callback"],
          response_types: ["code"],
          scope: "openid profile email phone offline_access",
          enabled: true,
        },
        managementHeaders
      );
      if (clientResponse.status !== 201) {
        console.error(
          `Failed to register client for ${trustSource}/${alg}: ${clientResponse.status} ` +
            JSON.stringify(clientResponse.data)
        );
        process.exit(1);
      }

      const instances = [];
      for (let i = 0; i < instanceCount; i++) {
        const instanceId = crypto.randomUUID();
        const jwk = await generateJwk(alg, instanceId);
        // attester_jwks は cnf.jwk の鍵で検証するので client_instance への登録は不要
        if (selfSigned) {
          const response = await postJson(
            `${baseUrl}/v1/management/tenants/${tenant.tenantId}/client-instances`,
            { id: instanceId, client_id: clientId, instance_key: publicJwkOf(jwk) },
            managementHeaders
          );
          if (response.status !== 201) {
            console.error(
              `Failed to register instance for ${trustSource}/${alg}: ${response.status} ` +
                JSON.stringify(response.data)
            );
            process.exit(1);
          }
        }
        instances.push({ instanceId, jwk });
      }

      entries.push({
        key: `${trustSource}/${alg}`,
        trustSource,
        alg,
        rsaBits: alg === "RS256" || alg === "PS256" ? rsaBits : null,
        tenantId: tenant.tenantId,
        issuer: `${baseUrl}/${tenant.tenantId}`,
        clientId,
        attesterJwk,
        instances,
      });
      console.log(`  ${trustSource}/${alg}: client ${clientId}, ${instances.length} instances`);
    }
  }

  await signSelected(entries);
  console.log(
    "    時間が空いたら: node performance-test/scripts/generate-attestation-matrix.js --resign --only <trust_source/alg>"
  );
}

main().catch((error) => {
  if (error?.cause?.code === "UNABLE_TO_VERIFY_LEAF_SIGNATURE") {
    console.error("TLS handshake failed: the local CA is not trusted by node.");
    console.error('Retry with: NODE_EXTRA_CA_CERTS="$(mkcert -CAROOT)/rootCA.pem" node ' + process.argv[1]);
    process.exit(1);
  }
  console.error(error);
  process.exit(1);
});
