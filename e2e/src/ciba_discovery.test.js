import { describe, expect, it } from "@jest/globals";

import { getConfiguration } from "./api/oauthClient";
import { serverConfig } from "./testConfig";
import { isArray, isBoolean, isNumber } from "./lib/util";

describe("OpenID Connect Client-Initiated Backchannel Authentication Flow - Core 1.0 discovery", () => {
  describe("4. Registration and Discovery Metadata", () => {
    describe("OpenID Provider Metadata", () => {
      it("backchannel_token_delivery_modes_supported REQUIRED. JSON array containing one or more of the following values: poll, ping, and push.", async () => {
        const response = await getConfiguration({
          endpoint: serverConfig.discoveryEndpoint,
        });
        console.log(response.data);
        expect(response.status).toBe(200);
        expect(response.data).toHaveProperty(
          "backchannel_token_delivery_modes_supported"
        );
        expect(
          isArray(response.data.backchannel_token_delivery_modes_supported)
        ).toBe(true);
        expect(
          response.data.backchannel_token_delivery_modes_supported
        ).toEqual(["poll", "ping", "push"]);
      });

      it("backchannel_authentication_endpoint REQUIRED. URL of the OP's Backchannel Authentication Endpoint as defined in Section 7.", async () => {
        const response = await getConfiguration({
          endpoint: serverConfig.discoveryEndpoint,
        });
        console.log(response.data);
        expect(response.status).toBe(200);

        expect(response.data).toHaveProperty(
          "backchannel_token_delivery_modes_supported"
        );
        expect(
          isArray(response.data.backchannel_token_delivery_modes_supported)
        ).toBe(true);
        expect(
          response.data.backchannel_token_delivery_modes_supported
        ).toEqual(["poll", "ping", "push"]);

        expect(response.data).toHaveProperty(
          "backchannel_authentication_endpoint"
        );
      });

      it("backchannel_authentication_request_signing_alg_values_supported OPTIONAL. JSON array containing a list of the JWS signing algorithms (alg values) supported by the OP for signed authentication requests, which are described in Section 7.1.1. If omitted, signed authentication requests are not supported by the OP.", async () => {
        const response = await getConfiguration({
          endpoint: serverConfig.discoveryEndpoint,
        });
        console.log(response.data);
        expect(response.status).toBe(200);

        expect(response.data).toHaveProperty(
          "backchannel_token_delivery_modes_supported"
        );
        expect(
          isArray(response.data.backchannel_token_delivery_modes_supported)
        ).toBe(true);
        expect(
          response.data.backchannel_token_delivery_modes_supported
        ).toEqual(["poll", "ping", "push"]);

        expect(response.data).toHaveProperty(
          "backchannel_authentication_endpoint"
        );

        expect(response.data).toHaveProperty(
          "backchannel_authentication_request_signing_alg_values_supported"
        );
        expect(
          isArray(
            response.data
              .backchannel_authentication_request_signing_alg_values_supported
          )
        ).toBe(true);
      });

      it("backchannel_user_code_parameter_supported OPTIONAL. Boolean value specifying whether the OP supports the use of the user_code parameter, with true indicating support. If omitted, the default value is false.", async () => {
        const response = await getConfiguration({
          endpoint: serverConfig.discoveryEndpoint,
        });
        console.log(response.data);
        expect(response.status).toBe(200);

        expect(response.data).toHaveProperty(
          "backchannel_token_delivery_modes_supported"
        );
        expect(
          isArray(response.data.backchannel_token_delivery_modes_supported)
        ).toBe(true);
        expect(
          response.data.backchannel_token_delivery_modes_supported
        ).toEqual(["poll", "ping", "push"]);

        expect(response.data).toHaveProperty(
          "backchannel_authentication_endpoint"
        );

        expect(response.data).toHaveProperty(
          "backchannel_authentication_request_signing_alg_values_supported"
        );
        expect(
          isArray(
            response.data
              .backchannel_authentication_request_signing_alg_values_supported
          )
        ).toBe(true);

        expect(response.data).toHaveProperty(
          "backchannel_user_code_parameter_supported"
        );
        expect(
          isBoolean(response.data.backchannel_user_code_parameter_supported)
        ).toBe(true);
      });
    });
  });
});
