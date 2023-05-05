import { describe, expect, it } from "@jest/globals";

import { getConfiguration } from "./api/oauthClient";
import { serverConfig } from "./testConfig";

describe("OpenID Connect Core 1.0 incorporating errata set 1 code", () => {
  it("success pattern", async () => {
    const response = await getConfiguration({
      endpoint: serverConfig.discoveryEndpoint,
    });
    console.log(response.data);
    expect(response.status).toBe(200);
  });
});
