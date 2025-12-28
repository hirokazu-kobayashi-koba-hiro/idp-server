import { describe, expect, it } from "@jest/globals";

import {
  clientSecretPostClient,
  nativeAppClient,
  serverConfig,
} from "../testConfig";
import { get } from "../../lib/http";

/**
 * RFC 8252: OAuth 2.0 for Native Apps
 * https://www.rfc-editor.org/rfc/rfc8252
 *
 * Note: These tests require nativeAppClient to be registered.
 * Run: ./config/scripts/e2e-test-data.sh to register the client.
 */
describe("RFC 8252: OAuth 2.0 for Native Apps", () => {
  /**
   * Helper function for native app authorization requests
   */
  const requestNativeAppAuthorization = async (redirectUri) => {
    const params = new URLSearchParams({
      response_type: "code",
      client_id: nativeAppClient.clientId,
      redirect_uri: redirectUri,
      scope: "account", // No openid = OAuth 2.0 flow
      state: `test-state-${Date.now()}`,
    });

    const response = await get({
      url: `${serverConfig.authorizationEndpoint}?${params.toString()}`,
      headers: {},
    });

    return response;
  };

  describe("7.3. Loopback Interface Redirection", () => {
    /**
     * RFC 8252 Section 7.3:
     * "The authorization server MUST allow any port to be specified at the
     * time of the request for loopback IP redirect URIs, to accommodate
     * clients that obtain an available ephemeral port from the operating
     * system at the time of the request."
     *
     * Note: Requires implementation of matchWithLoopbackPortAllowance() in UriMatcher
     */
    it("should accept different port for native app with loopback URI (127.0.0.1)", async () => {
      // Registered: http://127.0.0.1:8080/callback
      // Requested:  http://127.0.0.1:9999/callback
      const differentPortUri = nativeAppClient.redirectUri.replace(":8080", ":9999");
      const response = await requestNativeAppAuthorization(differentPortUri);

      expect(response.status).toBe(302);
      const location = response.headers.location || "";
      expect(location).not.toContain("error=");
    });

    it("should accept different port for native app with localhost", async () => {
      // Registered: http://127.0.0.1:8080/callback (or http://localhost:8080/callback)
      // Requested:  http://localhost:12345/callback
      const localhostUri = nativeAppClient.redirectUri.replace("127.0.0.1:8080", "localhost:12345");
      const response = await requestNativeAppAuthorization(localhostUri);

      expect(response.status).toBe(302);
      const location = response.headers.location || "";
      expect(location).not.toContain("error=");
    });

    it("should reject different host even for native app", async () => {
      // Port flexibility only applies to loopback addresses
      const differentHostUri = nativeAppClient.redirectUri.replace("127.0.0.1", "192.168.1.1");
      const response = await requestNativeAppAuthorization(differentHostUri);

      expect(response.status).toBe(302);
      const location = response.headers.location || "";
      const url = new URL(location);
      expect(url.searchParams.get("error")).toBe("invalid_request");
      expect(url.searchParams.get("error_description")).toContain("redirect_uri does not match");
    });

    it("should reject different path even with loopback", async () => {
      // Port flexibility does not extend to path
      const differentPathUri = nativeAppClient.redirectUri.replace("/callback", "/malicious");
      const response = await requestNativeAppAuthorization(differentPathUri);

      expect(response.status).toBe(302);
      const location = response.headers.location || "";
      const url = new URL(location);
      expect(url.searchParams.get("error")).toBe("invalid_request");
      expect(url.searchParams.get("error_description")).toContain("redirect_uri does not match");
    });
  });

  describe("Port flexibility does NOT apply to web applications", () => {
    it("should reject different port for web application", async () => {
      const webDifferentPortUri = clientSecretPostClient.redirectUri.replace(
        "www.certification.openid.net",
        "www.certification.openid.net:8443"
      );

      const params = new URLSearchParams({
        response_type: "code",
        client_id: clientSecretPostClient.clientId,
        redirect_uri: webDifferentPortUri,
        scope: "account",
        state: `test-state-${Date.now()}`,
      });

      const response = await get({
        url: `${serverConfig.authorizationEndpoint}?${params.toString()}`,
        headers: {},
      });

      expect(response.status).toBe(302);
      const location = response.headers.location || "";
      const url = new URL(location);
      expect(url.searchParams.get("error")).toBe("invalid_request");
      expect(url.searchParams.get("error_description")).toContain("redirect_uri does not match");
    });
  });
});
