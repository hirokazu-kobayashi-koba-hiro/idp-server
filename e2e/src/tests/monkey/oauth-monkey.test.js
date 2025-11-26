import { describe, expect, it, test } from "@jest/globals";
import { get, post, postWithJson } from "../../lib/http";
import { backendUrl, serverConfig } from "../testConfig";


describe("Monkey test OAuth Flow", () => {

  it("authorization request id null is bad request", async() => {
    const id = null;
    const viewDataResponse = await get({
      url: `${backendUrl}/${serverConfig.tenantId}/v1/authorizations/${id}/view-data`
    });

    console.log(JSON.stringify(viewDataResponse.data, null, 2));
    expect(viewDataResponse.status).toBe(400);
    expect(viewDataResponse.data.error).toEqual("invalid_request");

    const passwordResponse = await postWithJson({
      url: `${backendUrl}/${serverConfig.tenantId}/v1/authorizations/${id}/password-authentication`,
      body: {
        username: "test",
        password: "test",
      }
    });

    console.log(JSON.stringify(passwordResponse.data, null, 2));
    expect(passwordResponse.status).toBe(400);
    expect(passwordResponse.data.error).toEqual("invalid_request");
  });

});