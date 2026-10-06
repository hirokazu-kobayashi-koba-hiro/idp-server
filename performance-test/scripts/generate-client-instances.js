#!/usr/bin/env node
/**
 * Generate Client Instances and pre-signed attestation JWTs for Performance Testing
 *
 * scenario-15-token-attest-jwt-client-auth.js の入力を用意する。
 * k6 は ES256 署名ができない（k6/crypto は HMAC のみ）ため、
 * Client Attestation JWT / PoP JWT を事前に署名しておく。
 *
 * PoP JWT はリクエストごとに 1 つ（jti のリプレイ検出、#1893）:
 *   サーバは受け付けた PoP JWT の jti を記録し、2 回目を拒否する。そのため PoP JWT は
 *   流すリクエストの数（--requests）だけ jti を変えて署名し、シナリオは 1 リクエストに 1 つずつ使う。
 *   Client Attestation JWT はインスタンスごとに 1 つで、使い回してよい。
 *
 *   PoP JWT の iat は、テナントの client_attestation_pop_acceptable_window_seconds（性能テスト用の
 *   テナントは 600 秒）の内側でしか受け付けられない。Client Attestation JWT の exp も同じ長さにしてある。
 *   ⚠️ 署名から窓を過ぎると 401 になるので、テスト直前に --resign し、テストは窓の内側で終わらせること。
 *
 * Usage:
 *   # 初回: クライアント登録 + インスタンス登録 + 署名
 *   node performance-test/scripts/generate-client-instances.js
 *
 *   # テスト直前: 保存済みの鍵から JWT だけ作り直す（API 呼び出しなし・数秒）
 *   node performance-test/scripts/generate-client-instances.js --resign
 *
 *   ローカルの https://api.local.test は mkcert のローカル CA を使うため、
 *   e2e/package.json と同じくルート CA を渡す必要がある:
 *     NODE_EXTRA_CA_CERTS="$(mkcert -CAROOT)/rootCA.pem" node ...
 *
 * Options:
 *   --instances <n>     登録するインスタンス数（デフォルト: 100）
 *   --tenant-index <i>  対象テナントのインデックス（デフォルト: 0）
 *   --requests <n>      署名する PoP JWT の数 = 流せるリクエストの上限（デフォルト: 50000）
 *   --window <s>        テナントの PoP JWT の iat 許容窓（秒）。Client Attestation JWT の exp にも使う（デフォルト: 600）
 *   --resign            登録をスキップし、保存済みの鍵から JWT を再生成する
 *   --challenge         Challenge エンドポイントから 1 つ取得し、全 PoP JWT の challenge クレームに入れる。
 *                       PoP に入った Challenge はサーバが必ず照合するので、照合のコストを測るときに使う
 *
 * Output:
 *   - performance-test/data/performance-test-client-instances.json       インスタンスと Client Attestation JWT
 *   - performance-test/data/performance-test-client-instances-pops.json  PoP JWT（k6 の SharedArray で読む）
 */

const path = require("path");
const fs = require("fs");
const crypto = require("crypto");

// Resolve paths
const projectRoot = path.resolve(__dirname, "../..");
const e2eNodeModules = path.join(projectRoot, "e2e/node_modules");

// Add e2e/node_modules to module search path
module.paths.unshift(e2eNodeModules);

const jose = require("jose");
const { signPopPool, DEFAULT_WINDOW_SECONDS } = require("./attestation-pop-pool");

const performanceTestDataDir = path.join(projectRoot, "performance-test/data");
const tenantDataPath = path.join(performanceTestDataDir, "performance-test-multi-tenant-users.json");
const outputPath = path.join(performanceTestDataDir, "performance-test-client-instances.json");
const popsPath = path.join(performanceTestDataDir, "performance-test-client-instances-pops.json");

const ATTESTATION_TYP = "oauth-client-attestation+jwt";

// =============================================================================
// Arguments
// =============================================================================
const args = process.argv.slice(2);
const argValue = (name, fallback) => {
  const index = args.indexOf(name);
  return index >= 0 && args[index + 1] ? args[index + 1] : fallback;
};
const instanceCount = parseInt(argValue("--instances", "100"), 10);
const tenantIndex = parseInt(argValue("--tenant-index", "0"), 10);
const requestCount = parseInt(argValue("--requests", "50000"), 10);
const windowSeconds = parseInt(argValue("--window", String(DEFAULT_WINDOW_SECONDS)), 10);
const resignOnly = args.includes("--resign");
const withChallenge = args.includes("--challenge");

// =============================================================================
// .env (register-tenants.sh と同じキーを読む)
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
// JWT
// =============================================================================
const publicJwkOf = (privateJwk) => {
  const { d, ...publicJwk } = privateJwk;
  return publicJwk;
};

async function generateInstanceJwk(kid) {
  const { privateKey } = await jose.generateKeyPair("ES256", { extractable: true });
  const jwk = await jose.exportJWK(privateKey);
  return { ...jwk, use: "sig", kid, alg: "ES256" };
}

/**
 * registered_instance_key では Client Instance が自身の CIK で Client Attestation JWT を自己署名し、
 * サーバは JOSE ヘッダの kid（= client_instance.id）で登録済みの鍵を引く。
 */
async function signAttestationJwt(instance, key, clientId, now) {
  return await new jose.SignJWT({
    sub: clientId,
    iat: now,
    exp: now + windowSeconds,
    cnf: { jwk: publicJwkOf(instance.jwk) },
  })
    .setProtectedHeader({ alg: "ES256", typ: ATTESTATION_TYP, kid: instance.instanceId })
    .sign(key);
}

async function fetchChallenge(issuer) {
  const response = await postJson(`${issuer}/v1/client-attestation/challenges`, {});
  if (response.status !== 200 || !response.data.attestation_challenge) {
    throw new Error(`challenge endpoint returned ${response.status}: ${JSON.stringify(response.data)}`);
  }
  return response.data.attestation_challenge;
}

/**
 * Client Attestation JWT はインスタンスごとに 1 つ、PoP JWT はリクエストごとに 1 つ署名する。
 * k 番目の PoP JWT は instances[k % instances.length] の鍵で署名するので、シナリオは同じ番号で
 * 対になる Client Attestation JWT を引ける。
 *
 * @returns {Promise<string[]>} PoP JWT
 */
async function signAll(entry) {
  const now = Math.floor(Date.now() / 1000);
  const challenge = withChallenge ? await fetchChallenge(entry.issuer) : undefined;
  const signers = [];
  for (const instance of entry.instances) {
    const key = await jose.importJWK(instance.jwk, "ES256");
    instance.attestationJwt = await signAttestationJwt(instance, key, entry.clientId, now);
    delete instance.popJwt;
    signers.push({ key });
  }
  const popJwts = await signPopPool({
    jose,
    signers,
    alg: "ES256",
    issuer: entry.issuer,
    now,
    count: requestCount,
    challenge,
    onProgress: (signed) => {
      if (signed % 10000 === 0 || signed === requestCount) {
        console.log(`  signed ${signed}/${requestCount} PoP JWTs`);
      }
    },
  });
  entry.generatedAt = now;
  entry.popWindowSeconds = windowSeconds;
  entry.popCount = popJwts.length;
  entry.challenge = challenge ?? null;
  return popJwts;
}

function writeOutputs(entries, popsByTenant) {
  fs.writeFileSync(outputPath, JSON.stringify(entries, null, 2));
  fs.writeFileSync(popsPath, JSON.stringify(popsByTenant));
  console.log(`\nWritten: ${outputPath}`);
  console.log(`Written: ${popsPath}`);
  console.log(
    `\n⚠️  JWT は ${windowSeconds} 秒で受け付けられなくなる。テストはこの直後に実行し、その内側で終わらせること。`
  );
  console.log(`    流せるリクエストは最大 ${requestCount} 件（--requests で変更）。`);
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
    const popsByTenant = [];
    for (const entry of existing) {
      const popJwts = await signAll(entry);
      popsByTenant.push({ tenantId: entry.tenantId, generatedAt: entry.generatedAt, popJwts });
      console.log(`re-signed ${entry.instances.length} instances for tenant ${entry.tenantId}`);
    }
    writeOutputs(existing, popsByTenant);
    return;
  }

  if (!fs.existsSync(tenantDataPath)) {
    console.error(`Tenant data not found: ${tenantDataPath}`);
    console.error("Please run: ./performance-test/scripts/register-tenants.sh -n 10");
    process.exit(1);
  }

  const env = loadEnv();
  const baseUrl = env.AUTHORIZATION_SERVER_URL || "http://localhost:8080";
  const tenantData = JSON.parse(fs.readFileSync(tenantDataPath, "utf-8"));
  const tenant = tenantData[tenantIndex];
  if (!tenant) {
    console.error(`Tenant index ${tenantIndex} not found in ${tenantDataPath}`);
    process.exit(1);
  }

  const accessToken = await fetchAdminToken(env, baseUrl);
  const managementHeaders = { Authorization: `Bearer ${accessToken}` };

  // 1. attest_jwt_client_auth のクライアントを登録
  const clientId = crypto.randomUUID();
  const clientResponse = await postJson(
    `${baseUrl}/v1/management/tenants/${tenant.tenantId}/clients`,
    {
      client_id: clientId,
      client_name: "Performance Test Attested Client",
      token_endpoint_auth_method: "attest_jwt_client_auth",
      extension: { client_attestation_trust_source: "registered_instance_key" },
      // scenario-2-bc との比較のため CIBA も perf クライアントと同じ設定で持たせる
      grant_types: ["client_credentials", "urn:openid:params:grant-type:ciba"],
      backchannel_token_delivery_mode: "poll",
      backchannel_user_code_parameter: true,
      redirect_uris: ["http://localhost:3000/callback"],
      response_types: ["code"],
      // scenario-5 の比較対象になるよう、onboarding-template.json の perf クライアントと同じ scope にする
      scope: "openid profile email phone offline_access",
      enabled: true,
    },
    managementHeaders
  );
  if (clientResponse.status !== 201) {
    console.error(
      `Failed to register client: ${clientResponse.status} ${JSON.stringify(clientResponse.data)}`
    );
    process.exit(1);
  }
  console.log(`registered client ${clientId} on tenant ${tenant.tenantId}`);

  // 2. Client Instance Key を登録
  // インスタンスはテナント直下の管理 API で登録する（client_id は本文で指定）
  const instancesUrl = `${baseUrl}/v1/management/tenants/${tenant.tenantId}/client-instances`;
  const instances = [];
  for (let i = 0; i < instanceCount; i++) {
    const instanceId = crypto.randomUUID();
    const jwk = await generateInstanceJwk(instanceId);
    const response = await postJson(
      instancesUrl,
      { id: instanceId, client_id: clientId, instance_key: publicJwkOf(jwk) },
      managementHeaders
    );
    if (response.status !== 201) {
      console.error(
        `Failed to register instance ${i}: ${response.status} ${JSON.stringify(response.data)}`
      );
      process.exit(1);
    }
    instances.push({ instanceId, jwk });
    if ((i + 1) % 20 === 0) {
      console.log(`  registered ${i + 1}/${instanceCount} instances`);
    }
  }

  // 3. JWT を事前署名
  const entry = {
    tenantId: tenant.tenantId,
    issuer: `${baseUrl}/${tenant.tenantId}`,
    clientId,
    instances,
  };
  const popJwts = await signAll(entry);

  console.log(`\n  tenant:    ${entry.tenantId}`);
  console.log(`  client:    ${entry.clientId}`);
  console.log(`  instances: ${entry.instances.length}`);
  writeOutputs([entry], [{ tenantId: entry.tenantId, generatedAt: entry.generatedAt, popJwts }]);
  console.log("    時間が空いたら: node performance-test/scripts/generate-client-instances.js --resign");
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
