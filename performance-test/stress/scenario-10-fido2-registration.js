/**
 * FIDO2 Registration Full Flow Performance Test (k6)
 *
 * This scenario measures the complete FIDO2 registration flow including
 * cryptographic credential generation using k6's experimental WebCrypto API.
 *
 * Flow tested:
 * 1. Start authorization flow
 * 2. Register user (initial-registration)
 * 3. Email MFA challenge & verification
 * 4. Request FIDO2 registration challenge
 * 5. Generate credential with ECDSA key pair
 * 6. Complete FIDO2 registration
 *
 * Usage:
 *   ./performance-test/k6-sqlite run \
 *     --summary-export=./performance-test/result/stress/scenario-10-fido2-registration.json \
 *     ./performance-test/stress/scenario-10-fido2-registration.js
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
import { generateValidCredentialFromChallenge } from "../libs/fido2.js";
import { initDb, saveCredential, clearAllCredentials, closeDb } from "../libs/credential-store.js";

// Environment variables
const BASE_URL = __ENV.BASE_URL || "https://api.local.dev";
const TENANT_ID = __ENV.TENANT_ID || "a1b2c3d4-5e6f-7a8b-9c0d-1e2f3a4b5c6d";
const ORGANIZATION_ID = __ENV.ORGANIZATION_ID || "9eb8eb8c-2615-4604-809f-5cae1c00a462";
const CLIENT_ID = __ENV.CLIENT_ID || "8a9f5e2c-1b3d-4c6a-9f8e-7d5c3a2b1e4f";
const CLIENT_SECRET = __ENV.CLIENT_SECRET || "local-dev-public-secret-32char";
const REDIRECT_URI = __ENV.REDIRECT_URI || "https://sample.local.dev/api/passkey-registration/callback/";
const RP_ID = __ENV.RP_ID || "local.dev";
const ORIGIN = __ENV.ORIGIN || "https://auth.local.dev";

// Admin credentials for Management API (from .env)
const ADMIN_ORGANIZATION_ID = __ENV.ADMIN_ORGANIZATION_ID || "278bacde-56b0-4922-9cff-714eb23ae928";
const ADMIN_TENANT_ID = __ENV.ADMIN_TENANT_ID || "5d0cb576-f88f-4adc-a61c-36d480442cc6";
const ADMIN_USER_EMAIL = __ENV.ADMIN_USER_EMAIL || "administrator@mail.com";
const ADMIN_USER_PASSWORD = __ENV.ADMIN_USER_PASSWORD || "/JgGO3e0NkQi/JVk";
const ADMIN_CLIENT_ID = __ENV.ADMIN_CLIENT_ID || "2e956053-6171-42f0-a9be-6ebc36a26cb3";
const ADMIN_CLIENT_SECRET = __ENV.ADMIN_CLIENT_SECRET || "63OQ+il1YCRuBZGT2wjAJlnwZxF7+o/baYwjcVGgLUFCsh5YPnrGTvJLSKRpPSG8";

const VU_COUNT = parseInt(__ENV.VU_COUNT || "10");
const DURATION = __ENV.DURATION || "30s";

// Custom metrics
const authorizationDuration = new Trend("fido2_reg_authorization_duration", true);
const userRegistrationDuration = new Trend("fido2_reg_user_registration_duration", true);
const emailMfaDuration = new Trend("fido2_reg_email_mfa_duration", true);
const fido2ChallengeDuration = new Trend("fido2_reg_challenge_duration", true);
const credentialGenDuration = new Trend("fido2_reg_credential_gen_duration", true);
const fido2CompleteDuration = new Trend("fido2_reg_complete_duration", true);
const tokenExchangeDuration = new Trend("fido2_reg_token_exchange_duration", true);
const totalDuration = new Trend("fido2_reg_total_duration", true);

const authorizationErrors = new Counter("fido2_reg_authorization_errors");
const userRegistrationErrors = new Counter("fido2_reg_user_errors");
const emailMfaErrors = new Counter("fido2_reg_email_mfa_errors");
const fido2Errors = new Counter("fido2_reg_fido2_errors");
const tokenErrors = new Counter("fido2_reg_token_errors");

// Whether to clear existing credentials before test
const CLEAR_DB = __ENV.CLEAR_DB === "true";

export let options = {
  vus: VU_COUNT,
  duration: DURATION,
  thresholds: {
    http_req_duration: ["p(95)<2000"],
    http_req_failed: ["rate<0.05"],
    fido2_reg_authorization_duration: ["p(95)<300"],
    fido2_reg_user_registration_duration: ["p(95)<500"],
    fido2_reg_challenge_duration: ["p(95)<500"],
    fido2_reg_complete_duration: ["p(95)<500"],
    fido2_reg_token_exchange_duration: ["p(95)<500"],
    fido2_reg_total_duration: ["p(95)<3500"],
  },
};

// Initialize SQLite database
export function setup() {
  initDb();
  if (CLEAR_DB) {
    console.log("Clearing existing credentials...");
    clearAllCredentials();
  }
  console.log("SQLite database initialized for credential storage");
  return {};
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

// Helper to get verification code via Management API
function getVerificationCode(authId, accessToken) {
  const txUrl = `${BASE_URL}/v1/management/tenants/${TENANT_ID}/authentication-transactions?authorization_id=${authId}`;
  const txResponse = http.get(txUrl, {
    headers: { Authorization: `Bearer ${accessToken}` },
  });

  // console.log(accessToken)
  // console.log(txResponse);
  if (txResponse.status !== 200) return "123456";

  const transactionId = JSON.parse(txResponse.body).list[0]?.id;
  if (!transactionId) return "123456";

  const interactionUrl = `${BASE_URL}/v1/management/tenants/${TENANT_ID}/authentication-interactions/${transactionId}/email-authentication-challenge`;
  const interactionResponse = http.get(interactionUrl, {
    headers: { Authorization: `Bearer ${accessToken}` },
  });

  if (interactionResponse.status === 200) {
    const data = JSON.parse(interactionResponse.body);
    if (data.payload?.verification_code) {
      return data.payload.verification_code;
    }
  }
  return "123456";
}

// Get admin access token
function getAdminToken() {
  const tokenUrl = `${BASE_URL}/${ADMIN_TENANT_ID}/v1/tokens`;
  const tokenBody = `grant_type=password&username=${encodeURIComponent(ADMIN_USER_EMAIL)}&password=${encodeURIComponent(ADMIN_USER_PASSWORD)}&scope=account%20management&client_id=${encodeURIComponent(ADMIN_CLIENT_ID)}&client_secret=${encodeURIComponent(ADMIN_CLIENT_SECRET)}`;
  const response = http.post(tokenUrl, tokenBody, {
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
  });

  // console.log(response)
  if (response.status !== 200) return null;
  return JSON.parse(response.body).access_token;
}

export default async function () {
  const timestamp = Date.now();
  const vuId = __VU;
  const iteration = __ITER;
  const totalStart = Date.now();

  const email = `fido2-reg-vu${vuId}-iter${iteration}-${timestamp}@example.com`;
  const password = `Password${timestamp}!`;
  const name = `Test User ${vuId}-${iteration}`;

  // Step 1: Start authorization flow
  const authStart = Date.now();
  const authParams = `response_type=code&client_id=${CLIENT_ID}&redirect_uri=${encodeURIComponent(REDIRECT_URI)}&scope=openid%20profile%20email&state=state_${timestamp}_${vuId}_${iteration}`;
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

  // Step 2: Register user
  const regStart = Date.now();
  const regResponse = http.post(
    `${BASE_URL}/${TENANT_ID}/v1/authorizations/${authId}/initial-registration`,
    JSON.stringify({ email, password, name }),
    { headers: { "Content-Type": "application/json" } }
  );
  userRegistrationDuration.add(Date.now() - regStart);

  const regSuccess = check(regResponse, {
    "user registration returns 200": (r) => r.status === 200,
  });

  if (!regSuccess) {
    userRegistrationErrors.add(1);
    console.log(`User registration failed: ${regResponse.status} - ${regResponse.body}`);
    return;
  }

  // Step 3: Email MFA
  const mfaStart = Date.now();

  // Request email challenge
  http.post(
    `${BASE_URL}/${TENANT_ID}/v1/authorizations/${authId}/email-authentication-challenge`,
    JSON.stringify({ email, template: "authentication" }),
    { headers: { "Content-Type": "application/json" } }
  );

  // Get verification code via Management API
  const accessToken = getAdminToken();
  const verificationCode = accessToken ? getVerificationCode(authId, accessToken) : "123456";

  // Complete email MFA
  const emailAuthResponse = http.post(
    `${BASE_URL}/${TENANT_ID}/v1/authorizations/${authId}/email-authentication`,
    JSON.stringify({ verification_code: verificationCode }),
    { headers: { "Content-Type": "application/json" } }
  );
  emailMfaDuration.add(Date.now() - mfaStart);

  const emailAuthSuccess = check(emailAuthResponse, {
    "email auth returns 200": (r) => r.status === 200,
  });

  if (!emailAuthSuccess) {
    emailMfaErrors.add(1);
    console.log(`Email MFA failed: ${emailAuthResponse.status}`);
    return;
  }

  // Step 4: FIDO2 registration challenge
  const challengeStart = Date.now();
  const fido2ChallengeResponse = http.post(
    `${BASE_URL}/${TENANT_ID}/v1/authorizations/${authId}/fido2-registration-challenge`,
    JSON.stringify({ username: email, displayName: name }),
    { headers: { "Content-Type": "application/json" } }
  );
  fido2ChallengeDuration.add(Date.now() - challengeStart);

  const challengeSuccess = check(fido2ChallengeResponse, {
    "fido2 challenge returns 200": (r) => r.status === 200,
    "fido2 challenge has challenge field": (r) => {
      try {
        const body = JSON.parse(r.body);
        return body.challenge !== undefined;
      } catch {
        return false;
      }
    },
  });

  if (!challengeSuccess) {
    fido2Errors.add(1);
    console.log(`FIDO2 challenge failed: ${fido2ChallengeResponse.status} - ${fido2ChallengeResponse.body}`);
    return;
  }

  const challengeData = JSON.parse(fido2ChallengeResponse.body);

  // Step 5: Generate credential using fido2.js
  const credGenStart = Date.now();
  const credential = await generateValidCredentialFromChallenge(challengeData, {
    rpId: RP_ID,
    origin: ORIGIN,
  });
  credentialGenDuration.add(Date.now() - credGenStart);

  // Step 6: Complete FIDO2 registration
  const completeStart = Date.now();
  const fido2RegResponse = http.post(
    `${BASE_URL}/${TENANT_ID}/v1/authorizations/${authId}/fido2-registration`,
    JSON.stringify(credential),
    { headers: { "Content-Type": "application/json" } }
  );
  fido2CompleteDuration.add(Date.now() - completeStart);

  const fido2Success = check(fido2RegResponse, {
    "fido2 registration returns 200": (r) => r.status === 200,
  });

  if (!fido2Success) {
    fido2Errors.add(1);
    console.log(`FIDO2 registration failed: ${fido2RegResponse.status} - ${fido2RegResponse.body}`);
    totalDuration.add(Date.now() - totalStart);
    return;
  }

  // Step 7: Complete authorization to get authorization code
  const authorizeResponse = http.post(
    `${BASE_URL}/${TENANT_ID}/v1/authorizations/${authId}/authorize`,
    null,
    { headers: { "Content-Type": "application/json" } }
  );

  const authorizeSuccess = check(authorizeResponse, {
    "authorize returns 200": (r) => r.status === 200,
  });

  if (!authorizeSuccess) {
    fido2Errors.add(1);
    console.log(`Authorize failed: ${authorizeResponse.status} - ${authorizeResponse.body}`);
    totalDuration.add(Date.now() - totalStart);
    return;
  }

  const authorizeData = JSON.parse(authorizeResponse.body);
  const redirectUri = authorizeData.redirect_uri;

  if (!redirectUri) {
    fido2Errors.add(1);
    console.log(`No redirect_uri in authorize response`);
    totalDuration.add(Date.now() - totalStart);
    return;
  }

  const codeMatch = redirectUri.match(/[?&]code=([^&]+)/);
  if (!codeMatch) {
    fido2Errors.add(1);
    console.log(`No authorization code in redirect_uri: ${redirectUri}`);
    totalDuration.add(Date.now() - totalStart);
    return;
  }

  const authorizationCode = codeMatch[1];

  // Step 8: Exchange code for token
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
  } else {
    // Save credential to SQLite for later authentication tests
    if (credential.privateKeyJwk) {
      saveCredential(credential.id, credential.privateKeyJwk, email);
    }
  }

  totalDuration.add(Date.now() - totalStart);

  sleep(0.1);
}

export function handleSummary(data) {
  const summary = {
    testName: "FIDO2 Registration Full Flow Performance",
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
          avg: data.metrics.fido2_reg_authorization_duration ? data.metrics.fido2_reg_authorization_duration.values.avg : 0,
          p95: data.metrics.fido2_reg_authorization_duration ? data.metrics.fido2_reg_authorization_duration.values["p(95)"] : 0,
        },
        userRegistration: {
          avg: data.metrics.fido2_reg_user_registration_duration ? data.metrics.fido2_reg_user_registration_duration.values.avg : 0,
          p95: data.metrics.fido2_reg_user_registration_duration ? data.metrics.fido2_reg_user_registration_duration.values["p(95)"] : 0,
        },
        emailMfa: {
          avg: data.metrics.fido2_reg_email_mfa_duration ? data.metrics.fido2_reg_email_mfa_duration.values.avg : 0,
          p95: data.metrics.fido2_reg_email_mfa_duration ? data.metrics.fido2_reg_email_mfa_duration.values["p(95)"] : 0,
        },
        fido2Challenge: {
          avg: data.metrics.fido2_reg_challenge_duration ? data.metrics.fido2_reg_challenge_duration.values.avg : 0,
          p95: data.metrics.fido2_reg_challenge_duration ? data.metrics.fido2_reg_challenge_duration.values["p(95)"] : 0,
        },
        credentialGeneration: {
          avg: data.metrics.fido2_reg_credential_gen_duration ? data.metrics.fido2_reg_credential_gen_duration.values.avg : 0,
          p95: data.metrics.fido2_reg_credential_gen_duration ? data.metrics.fido2_reg_credential_gen_duration.values["p(95)"] : 0,
        },
        fido2Complete: {
          avg: data.metrics.fido2_reg_complete_duration ? data.metrics.fido2_reg_complete_duration.values.avg : 0,
          p95: data.metrics.fido2_reg_complete_duration ? data.metrics.fido2_reg_complete_duration.values["p(95)"] : 0,
        },
        tokenExchange: {
          avg: data.metrics.fido2_reg_token_exchange_duration ? data.metrics.fido2_reg_token_exchange_duration.values.avg : 0,
          p95: data.metrics.fido2_reg_token_exchange_duration ? data.metrics.fido2_reg_token_exchange_duration.values["p(95)"] : 0,
        },
        total: {
          avg: data.metrics.fido2_reg_total_duration ? data.metrics.fido2_reg_total_duration.values.avg : 0,
          p95: data.metrics.fido2_reg_total_duration ? data.metrics.fido2_reg_total_duration.values["p(95)"] : 0,
        },
      },
      errors: {
        authorization: data.metrics.fido2_reg_authorization_errors ? data.metrics.fido2_reg_authorization_errors.values.count : 0,
        userRegistration: data.metrics.fido2_reg_user_errors ? data.metrics.fido2_reg_user_errors.values.count : 0,
        emailMfa: data.metrics.fido2_reg_email_mfa_errors ? data.metrics.fido2_reg_email_mfa_errors.values.count : 0,
        fido2: data.metrics.fido2_reg_fido2_errors ? data.metrics.fido2_reg_fido2_errors.values.count : 0,
        token: data.metrics.fido2_reg_token_errors ? data.metrics.fido2_reg_token_errors.values.count : 0,
      },
    },
  };

  console.log("\n=== FIDO2 Registration Full Flow Performance Summary ===");
  console.log(`Total Iterations: ${summary.metrics.totalIterations}`);
  console.log(`Iterations/sec: ${summary.metrics.iterationsPerSecond.toFixed(2)}`);
  console.log(`\nStep Durations (avg / p95):`);
  console.log(`  Authorization:        ${summary.metrics.steps.authorization.avg.toFixed(2)}ms / ${summary.metrics.steps.authorization.p95.toFixed(2)}ms`);
  console.log(`  User Registration:    ${summary.metrics.steps.userRegistration.avg.toFixed(2)}ms / ${summary.metrics.steps.userRegistration.p95.toFixed(2)}ms`);
  console.log(`  Email MFA:            ${summary.metrics.steps.emailMfa.avg.toFixed(2)}ms / ${summary.metrics.steps.emailMfa.p95.toFixed(2)}ms`);
  console.log(`  FIDO2 Challenge:      ${summary.metrics.steps.fido2Challenge.avg.toFixed(2)}ms / ${summary.metrics.steps.fido2Challenge.p95.toFixed(2)}ms`);
  console.log(`  Credential Gen:       ${summary.metrics.steps.credentialGeneration.avg.toFixed(2)}ms / ${summary.metrics.steps.credentialGeneration.p95.toFixed(2)}ms`);
  console.log(`  FIDO2 Complete:       ${summary.metrics.steps.fido2Complete.avg.toFixed(2)}ms / ${summary.metrics.steps.fido2Complete.p95.toFixed(2)}ms`);
  console.log(`  Token Exchange:       ${summary.metrics.steps.tokenExchange.avg.toFixed(2)}ms / ${summary.metrics.steps.tokenExchange.p95.toFixed(2)}ms`);
  console.log(`  Total:                ${summary.metrics.steps.total.avg.toFixed(2)}ms / ${summary.metrics.steps.total.p95.toFixed(2)}ms`);
  console.log(`\nErrors:`);
  console.log(`  Authorization: ${summary.metrics.errors.authorization}`);
  console.log(`  User Registration: ${summary.metrics.errors.userRegistration}`);
  console.log(`  Email MFA: ${summary.metrics.errors.emailMfa}`);
  console.log(`  FIDO2: ${summary.metrics.errors.fido2}`);
  console.log(`  Token: ${summary.metrics.errors.token}`);

  return {
    stdout: JSON.stringify(summary, null, 2),
  };
}
