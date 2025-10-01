import { describe, expect, it } from "@jest/globals";
import { post } from "../../../lib/http";
import { clientSecretPostClient, serverConfig, federationServerConfig } from "../../testConfig";
import { createFederatedUser } from "../../../user";

describe("Token Validation for Identity Verification", () => {

  describe("Invalid Authorization Header Scenarios", () => {

    it("should return 401 for missing authorization header", async () => {
      const type = "investment-account-opening";
      const applyUrl = serverConfig.identityVerificationApplyEndpoint
        .replace("{type}", type)
        .replace("{process}", "apply");

      const applyResponse = await post({
        url: applyUrl,
        headers: {
          "Content-Type": "application/json"
          // No Authorization header
        },
        body: {
          "last_name": "john",
          "first_name": "mac",
          "birthdate": "1992-02-12",
          "nationality": "JP",
          "email_address": "test@example.com"
        }
      });

      console.log("Missing auth header response:", applyResponse.status, applyResponse.data);
      expect(applyResponse.status).toBe(401);
    });

    it("should return 401 for empty authorization header", async () => {
      const type = "investment-account-opening";
      const applyUrl = serverConfig.identityVerificationApplyEndpoint
        .replace("{type}", type)
        .replace("{process}", "apply");

      const applyResponse = await post({
        url: applyUrl,
        headers: {
          "Content-Type": "application/json",
          "Authorization": "" // Empty Authorization header
        },
        body: {
          "last_name": "john",
          "first_name": "mac",
          "birthdate": "1992-02-12",
          "nationality": "JP",
          "email_address": "test@example.com"
        }
      });

      console.log("Empty auth header response:", applyResponse.status, applyResponse.data);
      expect(applyResponse.status).toBe(401);
    });

    it("should return 401 for malformed authorization header", async () => {
      const type = "investment-account-opening";
      const applyUrl = serverConfig.identityVerificationApplyEndpoint
        .replace("{type}", type)
        .replace("{process}", "apply");

      const applyResponse = await post({
        url: applyUrl,
        headers: {
          "Content-Type": "application/json",
          "Authorization": "Invalid header format" // Malformed Authorization header
        },
        body: {
          "last_name": "john",
          "first_name": "mac",
          "birthdate": "1992-02-12",
          "nationality": "JP",
          "email_address": "test@example.com"
        }
      });

      console.log("Malformed auth header response:", applyResponse.status, applyResponse.data);
      expect(applyResponse.status).toBe(401);
    });

    it("should return 401 for Bearer with empty token", async () => {
      const type = "investment-account-opening";
      const applyUrl = serverConfig.identityVerificationApplyEndpoint
        .replace("{type}", type)
        .replace("{process}", "apply");

      const applyResponse = await post({
        url: applyUrl,
        headers: {
          "Content-Type": "application/json",
          "Authorization": "Bearer " // Bearer with empty token
        },
        body: {
          "last_name": "john",
          "first_name": "mac",
          "birthdate": "1992-02-12",
          "nationality": "JP",
          "email_address": "test@example.com"
        }
      });

      console.log("Bearer empty token response:", applyResponse.status, applyResponse.data);
      expect(applyResponse.status).toBe(401);
    });

    it("should return 401 for invalid token", async () => {
      const type = "investment-account-opening";
      const applyUrl = serverConfig.identityVerificationApplyEndpoint
        .replace("{type}", type)
        .replace("{process}", "apply");

      const applyResponse = await post({
        url: applyUrl,
        headers: {
          "Content-Type": "application/json",
          "Authorization": "Bearer invalid_token_12345" // Invalid token
        },
        body: {
          "last_name": "john",
          "first_name": "mac",
          "birthdate": "1992-02-12",
          "nationality": "JP",
          "email_address": "test@example.com"
        }
      });

      console.log("Invalid token response:", applyResponse.status, applyResponse.data);
      expect(applyResponse.status).toBe(401);
    });

    it("should return 401 for expired token", async () => {
      // Create a user and get a valid token
      const { user, accessToken } = await createFederatedUser({
        serverConfig: serverConfig,
        federationServerConfig: federationServerConfig,
        client: clientSecretPostClient,
        adminClient: clientSecretPostClient
      });

      // Wait for token to expire (if short-lived) or use an expired token
      // For this test, we'll use a manually crafted expired-looking token
      const expiredToken = accessToken.substring(0, accessToken.length - 10) + "expired123";

      const type = "investment-account-opening";
      const applyUrl = serverConfig.identityVerificationApplyEndpoint
        .replace("{type}", type)
        .replace("{process}", "apply");

      const applyResponse = await post({
        url: applyUrl,
        headers: {
          "Content-Type": "application/json",
          "Authorization": `Bearer ${expiredToken}` // Modified token (simulating expired)
        },
        body: {
          "last_name": "john",
          "first_name": "mac",
          "birthdate": "1992-02-12",
          "nationality": "JP",
          "email_address": "test@example.com",
          "mobile_phone_number": user.phone_number
        }
      });

      console.log("Expired token response:", applyResponse.status, applyResponse.data);
      expect(applyResponse.status).toBe(401);
    });
  });

  describe("Valid Token Scenario (baseline)", () => {

    it("should return 200 for valid token", async () => {
      // Create a user and get a valid token
      const { user, accessToken } = await createFederatedUser({
        serverConfig: serverConfig,
        federationServerConfig: federationServerConfig,
        client: clientSecretPostClient,
        adminClient: clientSecretPostClient
      });

      const type = "investment-account-opening";
      const applyUrl = serverConfig.identityVerificationApplyEndpoint
        .replace("{type}", type)
        .replace("{process}", "apply");

      const applyResponse = await post({
        url: applyUrl,
        headers: {
          "Content-Type": "application/json",
          "Authorization": `Bearer ${accessToken}` // Valid token
        },
        body: {
          "last_name": "john",
          "first_name": "mac",
          "last_name_kana": "jon",
          "first_name_kana": "mac",
          "birthdate": "1992-02-12",
          "nationality": "JP",
          "email_address": "test@example.com",
          "mobile_phone_number": user.phone_number,
          "address": {
            "street_address": "test",
            "locality": "test",
            "region": "test",
            "postal_code": "1000001",
            "country": "JP"
          }
        }
      });

      console.log("Valid token response:", applyResponse.status, applyResponse.data);
      expect(applyResponse.status).toBe(200);
      expect(applyResponse.data).toHaveProperty("id");
    });
  });
});