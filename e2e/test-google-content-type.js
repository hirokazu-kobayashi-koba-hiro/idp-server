/**
 * Google OAuth 2.0 endpoints Content-Type validation test
 *
 * This script tests how Google handles invalid Content-Type headers
 * on OAuth 2.0/OIDC endpoints to compare with our implementation.
 */

const axios = require("axios");

const GOOGLE_TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token";
const GOOGLE_REVOKE_ENDPOINT = "https://oauth2.googleapis.com/revoke";

async function testGoogleEndpoint(name, url, body) {
  console.log(`\n${"=".repeat(80)}`);
  console.log(`Testing: ${name}`);
  console.log(`URL: ${url}`);
  console.log(`${"=".repeat(80)}`);

  // Test 1: application/json (Invalid Content-Type)
  console.log("\n📝 Test 1: Sending application/json (Invalid)");
  try {
    const response = await axios.post(url, body, {
      headers: {
        "Content-Type": "application/json",
      },
      validateStatus: () => true,
    });

    console.log(`Status: ${response.status}`);
    console.log(`Headers:`, JSON.stringify(response.headers, null, 2));
    console.log(`Body:`, JSON.stringify(response.data, null, 2));
  } catch (error) {
    console.log(`Error: ${error.message}`);
    if (error.response) {
      console.log(`Status: ${error.response.status}`);
      console.log(`Body:`, JSON.stringify(error.response.data, null, 2));
    }
  }

  // Test 2: application/x-www-form-urlencoded (Valid Content-Type)
  console.log("\n📝 Test 2: Sending application/x-www-form-urlencoded (Valid)");
  try {
    const params = new URLSearchParams(body);
    const response = await axios.post(url, params.toString(), {
      headers: {
        "Content-Type": "application/x-www-form-urlencoded",
      },
      validateStatus: () => true,
    });

    console.log(`Status: ${response.status}`);
    console.log(`Headers:`, JSON.stringify(response.headers, null, 2));
    console.log(`Body:`, JSON.stringify(response.data, null, 2));
  } catch (error) {
    console.log(`Error: ${error.message}`);
    if (error.response) {
      console.log(`Status: ${error.response.status}`);
      console.log(`Body:`, JSON.stringify(error.response.data, null, 2));
    }
  }
}

async function main() {
  console.log("🔍 Google OAuth 2.0 Content-Type Validation Test");
  console.log("Testing how Google handles invalid Content-Type headers\n");

  // Test Token Endpoint
  await testGoogleEndpoint(
    "Token Endpoint",
    GOOGLE_TOKEN_ENDPOINT,
    {
      grant_type: "authorization_code",
      code: "dummy_code",
      client_id: "dummy_client_id",
      client_secret: "dummy_client_secret",
      redirect_uri: "https://example.com/callback",
    }
  );

  // Test Revocation Endpoint
  await testGoogleEndpoint(
    "Revocation Endpoint",
    GOOGLE_REVOKE_ENDPOINT,
    {
      token: "dummy_token",
    }
  );

  console.log(`\n${"=".repeat(80)}`);
  console.log("✅ Test completed");
  console.log(`${"=".repeat(80)}\n`);
}

main().catch(console.error);
