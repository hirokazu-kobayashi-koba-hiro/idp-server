import { describe, expect, it, test } from "@jest/globals";
import { faker } from "@faker-js/faker";
import {
  requestBackchannelAuthentications, requestToken
} from "../../api/oauthClient";
import {
  clientSecretPostClient,
  serverConfig
} from "../testConfig";
import { post } from "../../lib/http";
import { createJwt } from "../../lib/jose";

export const generateRandomJsonLikeObject = () => {
  return {
    [faker.lorem.word()]: faker.person.firstName(),
    [faker.lorem.word()]: faker.phone.number(),
    [faker.lorem.word()]: {
      [faker.lorem.word()]: faker.internet.url(),
      [faker.lorem.word()]: faker.datatype.boolean()
    }
  };
};

describe("Monkey test CIBA Flow", () => {
  const ciba = serverConfig.ciba;

  describe("CIBA random invalid request", () => {
    it("monkey test: random missing params", async () => {

      const body = {
        client_id: shouldOmit(0.5) ? undefined : clientSecretPostClient.clientId,
        client_secret: shouldOmit(0.5) ? undefined : clientSecretPostClient.clientSecret,
        login_hint: shouldOmit(0.5) ? undefined : ciba.loginHint,
        scope: shouldOmit(0.5) ? undefined : "openid",
        binding_message: shouldOmit(0.5) ? undefined : ciba.bindingMessage,
        user_code: shouldOmit(0.5) ? undefined : ciba.userCode
      };

      const params = new URLSearchParams();
      Object.entries(body).forEach(([k, v]) => {
        if (v !== undefined) params.append(k, v);
      });
      console.log(params);

      const res = await post({
        url: serverConfig.backchannelAuthenticationEndpoint,
        body: params
      });
      console.log(res.status, res.data);
      expect([200, 400, 401, 403]).toContain(res.status);
    });

    it("monkey test: invalid format in scope", async () => {
      const res = await requestBackchannelAuthentications({
        endpoint: serverConfig.backchannelAuthenticationEndpoint,
        clientId: clientSecretPostClient.clientId,
        scope: "!!@@###", // invalid
        bindingMessage: ciba.bindingMessage,
        userCode: ciba.userCode,
        loginHint: ciba.loginHint,
        clientSecret: clientSecretPostClient.clientSecret
      });
      console.log(res.status, res.data);
      expect([400]).toContain(res.status);
    });

    it("monkey test: flood of requests", async () => {
      const requests = Array.from({ length: 10 }).map(() =>
        requestBackchannelAuthentications({
          endpoint: serverConfig.backchannelAuthenticationEndpoint,
          clientId: clientSecretPostClient.clientId,
          scope: "openid",
          bindingMessage: ciba.bindingMessage,
          userCode: ciba.userCode,
          loginHint: ciba.loginHint,
          clientSecret: clientSecretPostClient.clientSecret
        })
      );
      const responses = await Promise.all(requests);
      responses.forEach((res) => {
        console.log(res.status, res.data?.auth_req_id || res.data?.error);
        expect([200, 429, 400]).toContain(res.status);
      });
    });

    it("monkey test: long string input", async () => {
      const longStr = "a".repeat(10000);
      const res = await requestBackchannelAuthentications({
        endpoint: serverConfig.backchannelAuthenticationEndpoint,
        clientId: clientSecretPostClient.clientId,
        scope: "openid " + longStr,
        bindingMessage: longStr,
        userCode: ciba.userCode,
        loginHint: ciba.loginHint,
        clientSecret: clientSecretPostClient.clientSecret
      });
      console.log(res.status, res.data);
      expect([200]).toContain(res.status);
    });
  });

  describe("CIBA Monkey Test - type mismatch", () => {
    const endpoint = serverConfig.backchannelAuthenticationEndpoint;
    const clientId = clientSecretPostClient.clientId;
    const clientSecret = clientSecretPostClient.clientSecret;

    const cases = [
      { field: "scope", value: 12345 },
      { field: "scope", value: { openid: true } },
      { field: "loginHint", value: ["not", "a", "string"] },
      { field: "userCode", value: false },
      { field: "bindingMessage", value: null },
      { field: "clientId", value: () => "function-as-id" },
      { field: "clientSecret", value: NaN }
    ];

    cases.forEach(({ field, value }, index) => {
      it(`type mismatch #${index + 1} - ${field} = ${JSON.stringify(value)}`, async () => {
        const baseParams = {
          endpoint,
          clientId,
          clientSecret,
          scope: "openid",
          loginHint: faker.internet.email(),
          bindingMessage: faker.phone.number(),
          userCode: faker.internet.password()
        };

        baseParams[field] = value;

        let res;
        try {
          res = await requestBackchannelAuthentications(baseParams);
        } catch (e) {
          console.error("💥 JS Runtime Error:", e);
          expect(true).toBe(false);
          return;
        }

        console.log(res.data);

        console.log({
          field,
          value,
          status: res.status,
          error: res.data?.error,
          description: res.data?.error_description
        });

        expect([400, 401]).toContain(res.status);
      });
    });
  });

  describe("CIBA - params type mismatch", () => {
    const endpoint = serverConfig.backchannelAuthenticationEndpoint;
    const typeMismatchCases = [
      ["scope", 123, 400, "invalid_scope"],
      ["scope", null, 400, "invalid_scope"],
      ["loginHint", ["array"], 400, "unknown_user_id"],
      ["bindingMessage", {}, 200, ""],
      ["clientId", null, 400, "invalid_request"],
      ["clientSecret", ["a", "b"], 401, "invalid_client"],
      ["acrValues", undefined, 200, undefined],
      ["acrValues", "", 200, undefined],
      ["acrValues", 123, 200, undefined],
      ["acrValues", ["urn:mace:bronze"], 200, undefined],
      ["acrValues", "urn:nonexistent:acr", 200, undefined],
      ["acrValues", "urn:x:basic urn:x:strong", 200, undefined],
      ["acrValues", "A".repeat(10000), 200, undefined],
      ["userCode", undefined, 200, undefined], // optional, should pass
      ["userCode", "", 200, undefined], // empty string is optional
      ["userCode", 12345, 400, "invalid_user_code"], // not a string
      ["userCode", {}, 400, "invalid_user_code"], // object instead of string
      ["userCode", "A".repeat(10000), 400, "invalid_user_code"], // too long
      ["userCode", "invalid-code", 400, "invalid_user_code"], // string but wrong value
      ["userCode", ciba.userCode, 200, undefined], // correct value
      ["requestedExpiry", "five minutes", 400, "invalid_request"],
      ["requestedExpiry", 0, 400, "invalid_request"],
      ["requestedExpiry", -1, 400, "invalid_request"],
      ["requestedExpiry", {}, 400, "invalid_request"],
      ["authorizationDetails", 42, 400, "invalid_authorization_details"],
      ["authorizationDetails", null, 400, "invalid_authorization_details"]
    ];
    test.each(typeMismatchCases)(
      "Field=%s, Value=%s → Expect Status=%s, Error=%s",
      async (field, value, expectedStatus, expectedError) => {
        const params = {
          clientId: clientSecretPostClient.clientId,
          clientSecret: clientSecretPostClient.clientSecret,
          scope: "openid",
          loginHint: serverConfig.ciba.loginHintSub,
          userCode: serverConfig.ciba.userCode,
          bindingMessage: serverConfig.ciba.bindingMessage
        };

        if (field === "authorizationDetails") {
          try {
            params.authorizationDetails = JSON.stringify(value);
          } catch (e) {
            console.warn(`⛔ stringify失敗: ${field} =`, value);
            expect(true).toBe(true);
            return;
          }
        } else {
          params[field] = value;
        }

        const res = await requestBackchannelAuthentications({
          endpoint,
          ...params
        });

        console.log(`\n🧪 Test Field: ${field}`);
        console.log("🔸 Sent Value:", value);
        console.log("🔸 Expected Status:", expectedStatus, " / Actual:", res.status);
        console.log("🔸 Expected Error:", expectedError, " / Actual:", res.data?.error);
        console.log("🔸 Description:", res.data?.error_description);

        expect(res.status).toBe(expectedStatus);
        if (expectedError) {
          expect(res.data?.error).toBe(expectedError);
        }
      }
    );

    const idTokenCases = [
      ["idTokenHint", "optional: should work without it", undefined, 400, undefined], // optional: should work without it
      ["idTokenHint", "empty string", "", 400, "invalid_request"], // empty string
      ["idTokenHint", "not a JWT","not.a.jwt", 400, "unknown_user_id"], // not a JWT
      ["idTokenHint", "not a string", 12345, 400, "unknown_user_id"], // not a string
      ["idTokenHint", "object", {}, 400, "unknown_user_id"], // object
      ["idTokenHint", "unsigned JWT", "eyJhbGciOiJub25lIn0.eyJzdWIiOiIxMjM0In0.", 400, "unknown_user_id"], // unsigned JWT
      ["idTokenHint", "expired token", createExpiredIdToken(), 200, undefined], // expired token
      ["idTokenHint", "audience mismatch", createIdTokenWithWrongAud(), 400, "unknown_user_id"], // audience mismatch
      ["idTokenHint", " issuer mismatch", createIdTokenWithWrongIss(), 400, "unknown_user_id"], // issuer mismatch
      ["idTokenHint", "broken structure", "malformed.jwt.part", 400, "unknown_user_id"], // broken structure
      ["idTokenHint", "valid case", createValidIdToken(), 200, undefined], // valid case
    ];
    test.each(idTokenCases)(
      "Field=%s, %s, Value=%s → Expect Status=%s, Error=%s",
      async (field, description, value, expectedStatus, expectedError) => {
        const params = {
          clientId: clientSecretPostClient.clientId,
          clientSecret: clientSecretPostClient.clientSecret,
          scope: "openid",
          userCode: serverConfig.ciba.userCode,
          bindingMessage: serverConfig.ciba.bindingMessage
        };

        params[field] = value;

        const res = await requestBackchannelAuthentications({
          endpoint,
          ...params
        });

        console.log(`\n🧪 Test Field: ${field}`);
        console.log("🔸 Sent Value:", value);
        console.log("🔸 Expected Status:", expectedStatus, " / Actual:", res.status);
        console.log("🔸 Expected Error:", expectedError, " / Actual:", res.data?.error);
        console.log("🔸 Description:", res.data?.error_description);

        expect(res.status).toBe(expectedStatus);
        if (expectedError) {
          expect(res.data?.error).toBe(expectedError);
        }
      }
    );
  });


  describe("CIBA RAR", () => {
    const endpoint = serverConfig.backchannelAuthenticationEndpoint;
    const clientId = clientSecretPostClient.clientId;
    const clientSecret = clientSecretPostClient.clientSecret;
    const loginHint = serverConfig.ciba.loginHintSub;
    const userCode = serverConfig.ciba.userCode;
    const bindingMessage = serverConfig.ciba.bindingMessage;

    const invalidAuthorizationDetailsCases = [
      {
        title: "authorization_details is string instead of array",
        value: "I am not an array"
      },
      {
        title: "authorization_details is empty array",
        value: JSON.stringify([])
      },
      {
        title: "authorization_details is array of numbers",
        value: JSON.stringify([1, 2, 3])
      },
      {
        title: "authorization_details with unknown type",
        value: JSON.stringify([{ type: "unknown_type", actions: ["steal_money"] }])
      },
      {
        title: "authorization_details with circular reference",
        value: JSON.stringify([{
          type: "payment_initiation",
          creditorAccount: {
            toString: () => {
              throw new Error("💥 BOOM");
            }
          }
        }])
      },
      {
        title: "authorization_details with deeply nested structure",
        value: JSON.stringify([
          {
            type: "payment_initiation",
            nested: { level1: { level2: { level3: { level4: { value: "boom" } } } } }
          }
        ])
      },
      {
        title: "authorization_details with random junk",
        value: JSON.stringify([generateRandomJsonLikeObject()])
      }
    ];

    invalidAuthorizationDetailsCases.forEach(({ title, value }) => {
      it(`should handle: ${title}`, async () => {
        let res;
        try {
          res = await requestBackchannelAuthentications({
            endpoint,
            clientId,
            clientSecret,
            scope: "openid",
            loginHint,
            userCode,
            bindingMessage,
            authorizationDetails: value
          });
        } catch (e) {
          console.error("💥 Runtime Error:", e);
          expect(true).toBe(false);
          return;
        }

        console.log(`\n[${title}]`);
        console.log("status:", res.status);
        console.log("error:", res.data?.error);
        console.log("desc :", res.data?.error_description);

        expect([200, 400]).toContain(res.status);
      });
    });
  });

  describe("CIBA login_hint format validation", () => {
    const endpoint = serverConfig.backchannelAuthenticationEndpoint;
    const clientId = clientSecretPostClient.clientId;
    const clientSecret = clientSecretPostClient.clientSecret;

    //TODO to be more correct error. based on implementation at 2025-10-27.
    const invalidLoginHintCases = [
      ["sub: with invalid UUID", "sub:not-a-valid-uuid", 400, "invalid_request"],
      ["sub: with empty string after prefix", "sub:", 400, "invalid_request"],
      ["device: with invalid UUID", "device:invalid-device-id", 400, "invalid_request"],
      ["device: with empty string", "device:", 400, "invalid_request"],
      ["phone: without proper format", "phone:", 400, "unknown_user_id"],
      ["email: without proper format", "email:", 400, "unknown_user_id"],
      ["ex-sub: with invalid format", "ex-sub:", 400, "unknown_user_id"],
      ["unknown prefix", "unknown:value", 400, "unknown_user_id"],
      ["just random string without prefix", "just-random-string-without-any-prefix", 400, "unknown_user_id"],
      ["empty string", "", 400, "invalid_request"],
      ["only whitespace", "   ", 400, "unknown_user_id"],
      ["malformed UUID in sub", "sub:12345-not-uuid", 400, "invalid_request"],
      ["malformed UUID in device", "device:abc-def-ghi", 400, "invalid_request"],
    ];

    test.each(invalidLoginHintCases)(
      "login_hint validation: %s → %s should return %s with %s",
      async (description, loginHintValue, expectedStatus, expectedError) => {
        const res = await requestBackchannelAuthentications({
          endpoint,
          clientId,
          clientSecret,
          scope: "openid",
          loginHint: loginHintValue,
          userCode: serverConfig.ciba.userCode,
          bindingMessage: serverConfig.ciba.bindingMessage
        });

        console.log(`\n🧪 Test: ${description}`);
        console.log("🔸 login_hint:", loginHintValue);
        console.log("📥 Status:", res.status);
        console.log("📄 Error:", res.data?.error);
        console.log("📄 Description:", res.data?.error_description);

        expect(res.status).toBe(expectedStatus);
        if (expectedError) {
          expect(res.data?.error).toBe(expectedError);
        }
      }
    );
  });

  describe("CIBA Token monkey test", () => {

    const tokenParamValidationCases = [
      ["grantType", null, 400, "invalid_request"],
      ["grantType", 123, 400, "unsupported_grant_type"],
      ["grantType", "code", 400, "unsupported_grant_type"],

      ["authReqId", null, 400, "invalid_request"],
      ["authReqId", 12345, 400, "invalid_grant"],
      ["authReqId", {}, 400, "invalid_grant"],

      ["clientId", null, 401, "invalid_client"],
      // ["clientId", 123, 401, "invalid_client"],
      //
      // ["clientSecret", null, 401, "invalid_client"],
      // ["clientSecret", [], 401, "invalid_client"]
    ];

    test.each(tokenParamValidationCases)(
      "Token param validation: %s = %s → %s / %s",
      async (field, value, expectedStatus, expectedError) => {
        const backchannelAuthenticationResponse =
          await requestBackchannelAuthentications({
            endpoint: serverConfig.backchannelAuthenticationEndpoint,
            clientId: clientSecretPostClient.clientId,
            bindingMessage: ciba.bindingMessage,
            userCode: ciba.userCode,
            loginHint: ciba.loginHintSub,
            scope: "openid profile phone email" + clientSecretPostClient.scope,
            clientSecret: clientSecretPostClient.clientSecret
          });
        console.log(backchannelAuthenticationResponse.data);
        expect(backchannelAuthenticationResponse.status).toBe(200);
        const authReqId = backchannelAuthenticationResponse.data.auth_req_id;

        const params = {
          endpoint: serverConfig.tokenEndpoint,
          grantType: "urn:openid:params:grant-type:ciba",
          authReqId: authReqId,
          clientId: clientSecretPostClient.clientId,
          clientSecret: clientSecretPostClient.clientSecret
        };

        params[field] = value;

        const res = await requestToken(params);

        console.log(`\n🧪 Field: ${field}`);
        console.log("🔸 Value:", value);
        console.log("📥 Status:", res.status);
        console.log("📄 Error:", res.data?.error);
        console.log("📄 ErrorDescription:", res.data?.error_description);

        expect(res.status).toBe(expectedStatus);
        expect(res.data?.error).toBe(expectedError);

      });

  });

});

export const shouldOmit = (threshold) => {
  const random = Math.random();
  return random < threshold;
};


function createValidIdToken() {
  return createJwt({
    payload:  {
      sub: serverConfig.ciba.sub,
      aud: clientSecretPostClient.clientId,
      iss: serverConfig.issuer,
      iat: Math.floor(Date.now() / 1000),
      exp: Math.floor(Date.now() / 1000) + 60,
    },
    secret: clientSecretPostClient.clientSecret
  });
}

function createExpiredIdToken() {
  return createJwt({
   payload: {
     sub: serverConfig.ciba.sub,
     aud: clientSecretPostClient.clientId,
     iss: serverConfig.issuer,
     iat: Math.floor(Date.now() / 1000) - 3600,
     exp: Math.floor(Date.now() / 1000) - 1800,
   },
   secret: clientSecretPostClient.clientSecret
  });
}

function createIdTokenWithWrongAud() {
  return createJwt({
    payload: {
      sub: serverConfig.ciba.userCode,
      aud: "other-client",
      iss: serverConfig.issuer,
      iat: Math.floor(Date.now() / 1000),
      exp: Math.floor(Date.now() / 1000) + 60,
    },
    secret: clientSecretPostClient.clientSecret
  });
}

function createIdTokenWithWrongIss() {
  return createJwt({
    payload: {
      sub: serverConfig.ciba.userCode,
      aud: clientSecretPostClient.clientId,
      iss: serverConfig.issuer + "/wrong",
      iat: Math.floor(Date.now() / 1000),
      exp: Math.floor(Date.now() / 1000) + 60,
    },
    secret: clientSecretPostClient.clientSecret
  });
}

