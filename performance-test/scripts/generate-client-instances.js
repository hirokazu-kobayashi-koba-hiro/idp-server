#!/usr/bin/env node
/**
 * Generate Client Instances and pre-signed attestation JWTs for Performance Testing
 *
 * scenario-15-token-attest-jwt-client-auth.js の入力を用意する。
 * k6 は ES256 署名ができない（k6/crypto は HMAC のみ）ため、
 * Client Attestation JWT / PoP JWT を事前に署名しておく。
 *
 * 事前署名が成立する理由:
 *   ClientAttestationPopJwtVerifier の iat 許容窓は ±5 分（DEFAULT_ACCEPTABLE_TIME_WINDOW）で、
 *   jti のリプレイ検出は未実装。よって同じ JWT ペアを窓の内側で使い回せる。
 *   ⚠️ 署名から 5 分を過ぎたペアは 401 になるので、テスト直前に --resign すること。
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
 *   --resign            登録をスキップし、保存済みの鍵から JWT を再生成する
 *   --challenge         Challenge エンドポイントから 1 つ取得し、全 PoP JWT の challenge クレームに入れる。
 *                       PoP に入った Challenge はサーバが必ず照合するので、照合のコストを測るときに使う
 *
 * Output:
 *   - performance-test/data/performance-test-client-instances.json
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

const performanceTestDataDir = path.join(projectRoot, "performance-test/data");
const tenantDataPath = path.join(performanceTestDataDir, "performance-test-multi-tenant-users.json");
const outputPath = path.join(performanceTestDataDir, "performance-test-client-instances.json");

const ATTESTATION_TYP = "oauth-client-attestation+jwt";
const POP_TYP = "oauth-client-attestation-pop+jwt";

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
async function signAttestationJwt(instance, clientId, now) {
  const key = await jose.importJWK(instance.jwk, "ES256");
  return await new jose.SignJWT({
    sub: clientId,
    iat: now,
    exp: now + 300,
    cnf: { jwk: publicJwkOf(instance.jwk) },
  })
    .setProtectedHeader({ alg: "ES256", typ: ATTESTATION_TYP, kid: instance.instanceId })
    .sign(key);
}

async function signPopJwt(instance, issuer, now, challenge) {
  const key = await jose.importJWK(instance.jwk, "ES256");
  return await new jose.SignJWT({
    aud: issuer,
    jti: crypto.randomUUID(),
    iat: now,
    ...(challenge ? { challenge } : {}),
  })
    .setProtectedHeader({ alg: "ES256", typ: POP_TYP })
    .sign(key);
}

async function fetchChallenge(issuer) {
  const response = await postJson(`${issuer}/v1/client-attestation/challenges`, {});
  if (response.status !== 200 || !response.data.attestation_challenge) {
    throw new Error(`challenge endpoint returned ${response.status}: ${JSON.stringify(response.data)}`);
  }
  return response.data.attestation_challenge;
}

async function signAll(entry) {
  const now = Math.floor(Date.now() / 1000);
  const challenge = withChallenge ? await fetchChallenge(entry.issuer) : undefined;
  for (const instance of entry.instances) {
    instance.attestationJwt = await signAttestationJwt(instance, entry.clientId, now);
    instance.popJwt = await signPopJwt(instance, entry.issuer, now, challenge);
  }
  entry.generatedAt = now;
  entry.challenge = challenge ?? null;
  return entry;
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
    for (const entry of existing) {
      await signAll(entry);
      console.log(`re-signed ${entry.instances.length} instances for tenant ${entry.tenantId}`);
    }
    fs.writeFileSync(outputPath, JSON.stringify(existing, null, 2));
    console.log(`\nWritten: ${outputPath}`);
    console.log("⚠️  JWT は 5 分で期限切れになる。テストはこの直後に実行すること。");
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
  const entry = await signAll({
    tenantId: tenant.tenantId,
    issuer: `${baseUrl}/${tenant.tenantId}`,
    clientId,
    instances,
  });

  fs.writeFileSync(outputPath, JSON.stringify([entry], null, 2));
  console.log(`\nWritten: ${outputPath}`);
  console.log(`  tenant:    ${entry.tenantId}`);
  console.log(`  client:    ${entry.clientId}`);
  console.log(`  instances: ${entry.instances.length}`);
  console.log("\n⚠️  JWT は 5 分で期限切れになる。テストはこの直後に実行すること。");
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
