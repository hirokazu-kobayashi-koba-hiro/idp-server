import { describe, expect, it } from "@jest/globals";
import { options } from "../../lib/http";
import { serverConfig } from "../testConfig";

/**
 * FAPI 2.0 Security Profile §5.2.3.3 / OAuth 2.1: Authorization Endpoint CORS.
 *
 * The authorization endpoint is reached via top-level browser navigation (redirect / form
 * submission), not via XHR/fetch, so it MUST NOT support CORS. Endpoints that ARE legitimately
 * accessed by JavaScript clients (PAR /push, /{id}/authorize, ...) continue to support CORS.
 *
 * `DynamicCorsFilter` always emits `Access-Control-Allow-Origin` when it runs (matching an
 * allow-listed origin, or falling back to the tenant domain). Therefore:
 * - authorization endpoint root: the filter is skipped -> header absent.
 * - CORS-eligible subpaths: the filter runs -> header present.
 */
describe("FAPI 2.0 SP §5.2.3.3 / OAuth 2.1  Authorization Endpoint CORS", () => {
  const origin = "https://spa.example.com";

  it("The authorization endpoint MUST NOT support CORS (preflight returns no Access-Control-Allow-Origin)", async () => {
    const response = await options({
      url: serverConfig.authorizationEndpoint,
      headers: {
        Origin: origin,
        "Access-Control-Request-Method": "GET",
        "Access-Control-Request-Headers": "Content-Type",
      },
    });
    expect(response.headers["access-control-allow-origin"]).toBeUndefined();
  });

  it("The PAR endpoint (/push) MUST continue to support CORS for JavaScript clients", async () => {
    const response = await options({
      url: serverConfig.pushedAuthorizationEndpoint,
      headers: {
        Origin: origin,
        "Access-Control-Request-Method": "POST",
        "Access-Control-Request-Headers": "Content-Type, Authorization",
      },
    });
    expect(response.headers["access-control-allow-origin"]).toBeDefined();
  });

  it("The /{id}/authorize subpath MUST continue to support CORS for JavaScript clients", async () => {
    const response = await options({
      url: serverConfig.authorizeEndpoint.replace("{id}", "preflight-probe"),
      headers: {
        Origin: origin,
        "Access-Control-Request-Method": "POST",
        "Access-Control-Request-Headers": "Content-Type",
      },
    });
    expect(response.headers["access-control-allow-origin"]).toBeDefined();
  });
});
