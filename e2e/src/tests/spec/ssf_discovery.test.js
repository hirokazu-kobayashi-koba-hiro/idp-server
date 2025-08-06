import { describe, expect, it } from "@jest/globals";

import { serverConfig } from "../testConfig";
import { get } from "../../lib/http";

describe("OpenID Shared Signals Framework Specification 1.0 - draft 04", () => {
  it("success pattern", async () => {
    let response = await get({
      url: serverConfig.ssfDiscoveryEndpoint,
    });
    console.log(response.data);
    expect(response.status).toBe(200);

    response = await get({
      url: serverConfig.ssfJwksEndpoint,
    });
    console.log(response.data);
    expect(response.status).toBe(200);
  });
});
