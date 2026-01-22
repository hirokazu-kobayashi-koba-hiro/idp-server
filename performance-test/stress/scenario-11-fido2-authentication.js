/**
 * FIDO2 Authentication Performance Test (k6)
 *
 * This scenario measures FIDO2 authentication performance using credentials
 * stored in SQLite by scenario-10 (registration).
 *
 * Prerequisites:
 *   Run scenario-10 first to register credentials:
 *   ./performance-test/k6-sqlite run ./performance-test/stress/scenario-10-fido2-registration.js
 *
 * Flow tested:
 * 1. Load credential from SQLite (by VU/iteration index)
 * 2. Start authorization flow
 * 3. Request FIDO2 authentication challenge
 * 4. Generate assertion with stored private key
 * 5. Complete FIDO2 authentication
 * 6. Update sign count in SQLite
 * 7. Complete authorization (get authorization code)
 * 8. Exchange code for token
 *
 * Usage:
 *   ./performance-test/k6-sqlite run \
 *     --summary-export=./performance-test/result/stress/scenario-11-fido2-authentication.json \
 *     ./performance-test/stress/scenario-11-fido2-authentication.js
 *
 * Environment variables:
 *   BASE_URL - Base URL of idp-server (default: https://api.local.dev)
 *   TENANT_ID - Tenant ID
 *   RP_ID - Relying Party ID (default: local.dev)
 *   ORIGIN - Origin URL (default: https://auth.local.dev)
 *   VU_COUNT - Number of virtual users (default: 10)
 *   DURATION - Test duration (default: 30s)
 */

import http from "k6/http";
import { check, sleep } from "k6";
import { Counter, Trend } from "k6/metrics";
import { generateValidAssertionFromChallenge } from "../libs/fido2.js";
import {
  initDb,
  getCredentialByIndex,
  incrementSignCount,
  getCredentialCount,
  closeDb,
} from "../libs/credential-store.js";

// Environment variables
const BASE_URL = __ENV.BASE_URL || "https://api.local.dev";
const TENANT_ID = __ENV.TENANT_ID || "a1b2c3d4-5e6f-7a8b-9c0d-1e2f3a4b5c6d";
const CLIENT_ID = __ENV.CLIENT_ID || "8a9f5e2c-1b3d-4c6a-9f8e-7d5c3a2b1e4f";
const CLIENT_SECRET = __ENV.CLIENT_SECRET || "local-dev-public-secret-32char";
const REDIRECT_URI = __ENV.REDIRECT_URI || "https://sample.local.dev/api/passkey-registration/callback/";
const RP_ID = __ENV.RP_ID || "local.dev";
const ORIGIN = __ENV.ORIGIN || "https://auth.local.dev";
const VU_COUNT = parseInt(__ENV.VU_COUNT || "10");
const DURATION = __ENV.DURATION || "30s";

// Custom metrics
const authorizationDuration = new Trend("fido2_auth_authorization_duration", true);
const challengeDuration = new Trend("fido2_auth_challenge_duration", true);
const assertionGenDuration = new Trend("fido2_auth_assertion_gen_duration", true);
const authCompleteDuration = new Trend("fido2_auth_complete_duration", true);
const authorizeDuration = new Trend("fido2_auth_authorize_duration", true);
const tokenExchangeDuration = new Trend("fido2_auth_token_exchange_duration", true);
const totalDuration = new Trend("fido2_auth_total_duration", true);

const authorizationErrors = new Counter("fido2_auth_authorization_errors");
const challengeErrors = new Counter("fido2_auth_challenge_errors");
const authenticationErrors = new Counter("fido2_auth_authentication_errors");
const authorizeErrors = new Counter("fido2_auth_authorize_errors");
const tokenErrors = new Counter("fido2_auth_token_errors");
const credentialNotFoundErrors = new Counter("fido2_auth_credential_not_found_errors");

// Credential count (set in setup)
let credentialCount = 0;

export let options = {
  vus: VU_COUNT,
  duration: DURATION,
  thresholds: {
    http_req_duration: ["p(95)<1000"],
    http_req_failed: ["rate<0.05"],
    fido2_auth_authorization_duration: ["p(95)<300"],
    fido2_auth_challenge_duration: ["p(95)<300"],
    fido2_auth_complete_duration: ["p(95)<500"],
    fido2_auth_authorize_duration: ["p(95)<300"],
    fido2_auth_token_exchange_duration: ["p(95)<500"],
    fido2_auth_total_duration: ["p(95)<2000"],
  },
};

// Initialize SQLite database and check credentials
export function setup() {
  initDb();
  const count = getCredentialCount();
  console.log(`Found ${count} credentials in SQLite database`);

  if (count === 0) {
    console.log("ERROR: No credentials found. Run scenario-10 first to register credentials.");
    console.log("  ./performance-test/k6-sqlite run ./performance-test/stress/scenario-10-fido2-registration.js");
  }

  return { credentialCount: count };
}

// Close database connection
export function teardown() {
  closeDb();
  console.log("SQLite database connection closed");
}

// Helper to extract authId from Location header
function extractAuthId(location) {
  const idMatch = location.match(/[?&]id=([^&]+)/);
  return idMatch ? idMatch[1] : null;
}

export default async function (data) {
  const timestamp = Date.now();
  const vuId = __VU;
  const iteration = __ITER;
  const totalStart = Date.now();

  // Calculate credential index (round-robin through available credentials)
  const credIndex = ((vuId - 1) * 1000 + iteration) % data.credentialCount;

  // Get credential from SQLite
  const cred = getCredentialByIndex(credIndex);

  if (!cred) {
    credentialNotFoundErrors.add(1);
    console.log(`Credential not found at index ${credIndex}`);
    return;
  }

  // Step 1: Start authorization flow
  const authStart = Date.now();
  const authParams = `response_type=code&client_id=${CLIENT_ID}&redirect_uri=${encodeURIComponent(REDIRECT_URI)}&scope=openid%20profile%20email&state=auth_${timestamp}_${vuId}_${iteration}`;
  const authUrl = `${BASE_URL}/${TENANT_ID}/v1/authorizations?${authParams}`;

  const authResponse = http.get(authUrl, { redirects: 0 });
  authorizationDuration.add(Date.now() - authStart);

  const authSuccess = check(authResponse, {
    "authorization returns 302": (r) => r.status === 302,
  });

  if (!authSuccess) {
    authorizationErrors.add(1);
    console.log(`Authorization failed: ${authResponse.status}`);
    return;
  }

  const authId = extractAuthId(authResponse.headers["Location"]);
  if (!authId) {
    authorizationErrors.add(1);
    console.log(`Failed to extract authId`);
    return;
  }

  // Step 2: FIDO2 authentication challenge
  const challengeStart = Date.now();
  const challengeResponse = http.post(
    `${BASE_URL}/${TENANT_ID}/v1/authorizations/${authId}/fido2-authentication-challenge`,
    JSON.stringify({ username: cred.email }),
    { headers: { "Content-Type": "application/json" } }
  );
  challengeDuration.add(Date.now() - challengeStart);

  const challengeSuccess = check(challengeResponse, {
    "challenge returns 200": (r) => r.status === 200,
    "challenge has challenge field": (r) => {
      try {
        const body = JSON.parse(r.body);
        return body.challenge !== undefined;
      } catch {
        return false;
      }
    },
  });

  if (!challengeSuccess) {
    challengeErrors.add(1);
    console.log(`FIDO2 challenge failed: ${challengeResponse.status} - ${challengeResponse.body}`);
    return;
  }

  const challengeData = JSON.parse(challengeResponse.body);

  // Step 3: Generate assertion using stored private key
  const newSignCount = cred.signCount + 1;

  const assertionStart = Date.now();
  const assertion = await generateValidAssertionFromChallenge(challengeData, cred.credentialId, {
    rpId: RP_ID,
    origin: ORIGIN,
    privateKeyJwk: cred.privateKeyJwk,
    signCount: newSignCount,
  });
  assertionGenDuration.add(Date.now() - assertionStart);

  // Step 4: Complete FIDO2 authentication
  const authCompleteStart = Date.now();
  const fido2AuthResponse = http.post(
    `${BASE_URL}/${TENANT_ID}/v1/authorizations/${authId}/fido2-authentication`,
    JSON.stringify(assertion),
    { headers: { "Content-Type": "application/json" } }
  );
  authCompleteDuration.add(Date.now() - authCompleteStart);

  const fido2Success = check(fido2AuthResponse, {
    "fido2 authentication returns 200": (r) => r.status === 200,
  });

  if (!fido2Success) {
    authenticationErrors.add(1);
    console.log(`FIDO2 authentication failed: ${fido2AuthResponse.status} - ${fido2AuthResponse.body}`);
    totalDuration.add(Date.now() - totalStart);
    return;
  }

  // Update sign count in SQLite
  incrementSignCount(cred.credentialId);

  // Step 5: Complete authorization to get authorization code
  const authorizeStart = Date.now();
  const authorizeResponse = http.post(
    `${BASE_URL}/${TENANT_ID}/v1/authorizations/${authId}/authorize`,
    null,
    { headers: { "Content-Type": "application/json" } }
  );
  authorizeDuration.add(Date.now() - authorizeStart);

  const authorizeSuccess = check(authorizeResponse, {
    "authorize returns 200": (r) => r.status === 200,
  });

  if (!authorizeSuccess) {
    authorizeErrors.add(1);
    console.log(`Authorize failed: ${authorizeResponse.status} - ${authorizeResponse.body}`);
    totalDuration.add(Date.now() - totalStart);
    return;
  }

  const authorizeData = JSON.parse(authorizeResponse.body);
  const redirectUri = authorizeData.redirect_uri;

  if (!redirectUri) {
    authorizeErrors.add(1);
    console.log(`No redirect_uri in authorize response`);
    totalDuration.add(Date.now() - totalStart);
    return;
  }

  const codeMatch = redirectUri.match(/[?&]code=([^&]+)/);
  if (!codeMatch) {
    authorizeErrors.add(1);
    console.log(`No authorization code in redirect_uri: ${redirectUri}`);
    totalDuration.add(Date.now() - totalStart);
    return;
  }

  const authorizationCode = codeMatch[1];

  // Step 6: Exchange code for token
  const tokenStart = Date.now();
  const tokenBody = `grant_type=authorization_code&code=${encodeURIComponent(authorizationCode)}&redirect_uri=${encodeURIComponent(REDIRECT_URI)}&client_id=${encodeURIComponent(CLIENT_ID)}&client_secret=${encodeURIComponent(CLIENT_SECRET)}`;
  const tokenResponse = http.post(
    `${BASE_URL}/${TENANT_ID}/v1/tokens`,
    tokenBody,
    { headers: { "Content-Type": "application/x-www-form-urlencoded" } }
  );
  tokenExchangeDuration.add(Date.now() - tokenStart);

  const tokenSuccess = check(tokenResponse, {
    "token exchange returns 200": (r) => r.status === 200,
  });

  if (!tokenSuccess) {
    tokenErrors.add(1);
    console.log(`Token exchange failed: ${tokenResponse.status} - ${tokenResponse.body}`);
  }

  totalDuration.add(Date.now() - totalStart);

  sleep(0.1);
}

export function handleSummary(data) {
  const summary = {
    testName: "FIDO2 Authentication Performance (SQLite)",
    timestamp: new Date().toISOString(),
    config: {
      baseUrl: BASE_URL,
      tenantId: TENANT_ID,
      rpId: RP_ID,
      origin: ORIGIN,
      vuCount: VU_COUNT,
      duration: DURATION,
    },
    metrics: {
      totalIterations: data.metrics.iterations ? data.metrics.iterations.values.count : 0,
      iterationsPerSecond: data.metrics.iterations ? data.metrics.iterations.values.rate : 0,
      steps: {
        authorization: {
          avg: data.metrics.fido2_auth_authorization_duration ? data.metrics.fido2_auth_authorization_duration.values.avg : 0,
          p95: data.metrics.fido2_auth_authorization_duration ? data.metrics.fido2_auth_authorization_duration.values["p(95)"] : 0,
        },
        challenge: {
          avg: data.metrics.fido2_auth_challenge_duration ? data.metrics.fido2_auth_challenge_duration.values.avg : 0,
          p95: data.metrics.fido2_auth_challenge_duration ? data.metrics.fido2_auth_challenge_duration.values["p(95)"] : 0,
        },
        assertionGen: {
          avg: data.metrics.fido2_auth_assertion_gen_duration ? data.metrics.fido2_auth_assertion_gen_duration.values.avg : 0,
          p95: data.metrics.fido2_auth_assertion_gen_duration ? data.metrics.fido2_auth_assertion_gen_duration.values["p(95)"] : 0,
        },
        authComplete: {
          avg: data.metrics.fido2_auth_complete_duration ? data.metrics.fido2_auth_complete_duration.values.avg : 0,
          p95: data.metrics.fido2_auth_complete_duration ? data.metrics.fido2_auth_complete_duration.values["p(95)"] : 0,
        },
        authorize: {
          avg: data.metrics.fido2_auth_authorize_duration ? data.metrics.fido2_auth_authorize_duration.values.avg : 0,
          p95: data.metrics.fido2_auth_authorize_duration ? data.metrics.fido2_auth_authorize_duration.values["p(95)"] : 0,
        },
        tokenExchange: {
          avg: data.metrics.fido2_auth_token_exchange_duration ? data.metrics.fido2_auth_token_exchange_duration.values.avg : 0,
          p95: data.metrics.fido2_auth_token_exchange_duration ? data.metrics.fido2_auth_token_exchange_duration.values["p(95)"] : 0,
        },
        total: {
          avg: data.metrics.fido2_auth_total_duration ? data.metrics.fido2_auth_total_duration.values.avg : 0,
          p95: data.metrics.fido2_auth_total_duration ? data.metrics.fido2_auth_total_duration.values["p(95)"] : 0,
        },
      },
      errors: {
        authorization: data.metrics.fido2_auth_authorization_errors ? data.metrics.fido2_auth_authorization_errors.values.count : 0,
        challenge: data.metrics.fido2_auth_challenge_errors ? data.metrics.fido2_auth_challenge_errors.values.count : 0,
        authentication: data.metrics.fido2_auth_authentication_errors ? data.metrics.fido2_auth_authentication_errors.values.count : 0,
        authorize: data.metrics.fido2_auth_authorize_errors ? data.metrics.fido2_auth_authorize_errors.values.count : 0,
        token: data.metrics.fido2_auth_token_errors ? data.metrics.fido2_auth_token_errors.values.count : 0,
        credentialNotFound: data.metrics.fido2_auth_credential_not_found_errors ? data.metrics.fido2_auth_credential_not_found_errors.values.count : 0,
      },
    },
  };

  console.log("\n=== FIDO2 Authentication Performance Summary ===");
  console.log(`Total Iterations: ${summary.metrics.totalIterations}`);
  console.log(`Iterations/sec: ${summary.metrics.iterationsPerSecond.toFixed(2)}`);
  console.log(`\nStep Durations (avg / p95):`);
  console.log(`  Authorization:     ${summary.metrics.steps.authorization.avg.toFixed(2)}ms / ${summary.metrics.steps.authorization.p95.toFixed(2)}ms`);
  console.log(`  Challenge:         ${summary.metrics.steps.challenge.avg.toFixed(2)}ms / ${summary.metrics.steps.challenge.p95.toFixed(2)}ms`);
  console.log(`  Assertion Gen:     ${summary.metrics.steps.assertionGen.avg.toFixed(2)}ms / ${summary.metrics.steps.assertionGen.p95.toFixed(2)}ms`);
  console.log(`  Auth Complete:     ${summary.metrics.steps.authComplete.avg.toFixed(2)}ms / ${summary.metrics.steps.authComplete.p95.toFixed(2)}ms`);
  console.log(`  Authorize:         ${summary.metrics.steps.authorize.avg.toFixed(2)}ms / ${summary.metrics.steps.authorize.p95.toFixed(2)}ms`);
  console.log(`  Token Exchange:    ${summary.metrics.steps.tokenExchange.avg.toFixed(2)}ms / ${summary.metrics.steps.tokenExchange.p95.toFixed(2)}ms`);
  console.log(`  Total:             ${summary.metrics.steps.total.avg.toFixed(2)}ms / ${summary.metrics.steps.total.p95.toFixed(2)}ms`);
  console.log(`\nErrors:`);
  console.log(`  Authorization: ${summary.metrics.errors.authorization}`);
  console.log(`  Challenge: ${summary.metrics.errors.challenge}`);
  console.log(`  Authentication: ${summary.metrics.errors.authentication}`);
  console.log(`  Authorize: ${summary.metrics.errors.authorize}`);
  console.log(`  Token: ${summary.metrics.errors.token}`);
  console.log(`  Credential Not Found: ${summary.metrics.errors.credentialNotFound}`);

  return {
    stdout: JSON.stringify(summary, null, 2),
  };
}
