import { describe, expect, it, beforeAll, afterAll } from "@jest/globals";
import { postWithJson, deletion } from "../../../lib/http";
import { requestToken } from "../../../api/oauthClient";
import {
  backendUrl,
  clientSecretPostClient,
  serverConfig,
  federationServerConfig,
  mockApiBaseUrl
} from "../../testConfig";
import { createFederatedUser } from "../../../user";
import { v4 as uuidv4 } from "uuid";

/**
 * Issue #1935: kana and date mapping functions, and the chains that bring Japanese and English
 * names to one form.
 *
 * Verifies that a reading written in hiragana or katakana, an English name written with different
 * widths, dashes, spaces and case, and a date written in any of the common notations, reach the
 * external API in one form. English needs no new function: normalize, regex_replace and case
 * already cover it, and the case here shows how they compose. The mock endpoint echoes back what it received, so
 * the assertion covers the value after JSON, HTTP and UTF-8, not the mapper in isolation.
 *
 * The echo endpoint reflects `name` and `user_id`. The name travels as `name` and the date as
 * `user_id`; the field names only matter to the mock.
 */
describe("Identity Verification - kana and date functions in body_mapping_rules", () => {
  const orgId = serverConfig.organizationId;
  const tenantId = serverConfig.tenantId;

  let orgAccessToken;
  let userAccessToken;

  const configIds = [];

  beforeAll(async () => {
    const orgAuthResponse = await requestToken({
      endpoint: `${backendUrl}/${tenantId}/v1/tokens`,
      grantType: "password",
      username: "ito.ichiro@gmail.com",
      password: "successUserCode001",
      clientId: clientSecretPostClient.clientId,
      clientSecret: clientSecretPostClient.clientSecret,
      scope: "org-management account management"
    });

    expect(orgAuthResponse.status).toBe(200);
    orgAccessToken = orgAuthResponse.data.access_token;

    const { accessToken } = await createFederatedUser({
      serverConfig: serverConfig,
      federationServerConfig: federationServerConfig,
      client: clientSecretPostClient,
      adminClient: clientSecretPostClient,
      scope:
        "openid profile email identity_verification_application " +
        clientSecretPostClient.identityVerificationScope
    });

    userAccessToken = accessToken;
  });

  afterAll(async () => {
    for (const configId of configIds) {
      try {
        await deletion({
          url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/identity-verification-configurations/${configId}`,
          headers: { Authorization: `Bearer ${orgAccessToken}` }
        });
      } catch (e) {
        console.log(`Failed to clean up configuration: ${configId}`, e.message);
      }
    }
  });

  /**
   * Creates a single-process IDA config whose apply step sends `name_kana` through
   * `nameFunctions` and `birthdate` through `dateFunctions` to the echo endpoint.
   */
  async function createEchoConfig(nameFunctions, dateFunctions) {
    const configId = uuidv4();
    const configurationType = uuidv4();
    configIds.push(configId);

    const response = await postWithJson({
      url: `${backendUrl}/v1/management/organizations/${orgId}/tenants/${tenantId}/identity-verification-configurations`,
      headers: {
        "Authorization": `Bearer ${orgAccessToken}`,
        "Content-Type": "application/json"
      },
      body: {
        "id": configId,
        "type": configurationType,
        "attributes": { "enabled": true },
        "common": { "auth_type": "none" },
        "processes": {
          "apply": {
            "request": {
              "schema": {
                "type": "object",
                "properties": {
                  "name_kana": { "type": "string" },
                  "birthdate": { "type": "string" }
                },
                "required": ["name_kana", "birthdate"]
              }
            },
            "execution": {
              "type": "http_request",
              "http_request": {
                "url": `${mockApiBaseUrl}/e2e/echo-user-context`,
                "method": "POST",
                "auth_type": "none",
                "header_mapping_rules": [
                  { "static_value": "application/json", "to": "Content-Type" }
                ],
                "body_mapping_rules": [
                  { "from": "$.request_body.name_kana", "to": "name", "functions": nameFunctions },
                  { "from": "$.request_body.birthdate", "to": "user_id", "functions": dateFunctions }
                ]
              }
            },
            "response": {
              "body_mapping_rules": [
                { "from": "$.response_body", "to": "*" }
              ]
            }
          }
        }
      }
    });
    expect(response.status).toBe(201);
    return configurationType;
  }

  /** Applies and returns what the external API received: { name, date }. */
  async function apply(configurationType, nameKana, birthdate) {
    const applyUrl = serverConfig.identityVerificationApplyEndpoint
      .replace("{type}", configurationType)
      .replace("{process}", "apply");

    const applyResponse = await postWithJson({
      url: applyUrl,
      body: { "name_kana": nameKana, "birthdate": birthdate },
      headers: {
        "Content-Type": "application/json",
        "Authorization": `Bearer ${userAccessToken}`
      }
    });
    console.log("Apply response:", JSON.stringify(applyResponse.data, null, 2));
    expect(applyResponse.status).toBe(200);
    return { name: applyResponse.data.received_name, date: applyResponse.data.received_sub };
  }

  /** The chain a reading needs: fold notation, drop spaces, then one script. */
  const READING_CHAIN = [
    { "name": "normalize", "args": { "form": "NFKC" } },
    { "name": "regex_replace", "args": { "pattern": "[\\s\\u3000]+", "replacement": "" } },
    { "name": "kana", "args": { "to": "katakana" } }
  ];

  it("should turn a hiragana reading into katakana and a written date into yyyy-MM-dd", async () => {
    const type = await createEchoConfig([{ "name": "kana" }], [{ "name": "date" }]);

    const received = await apply(type, "やまだ たろう", "1990年4月1日");

    expect(received.name).toBe("ヤマダ タロウ");
    expect(received.date).toBe("1990-04-01");
  });

  it("should bring every notation of the same reading and date to one value", async () => {
    // The point of the issue: the same person typed in different ways reaches the external API as
    // one value. Hiragana, halfwidth katakana with an ideographic space, fullwidth katakana.
    const notations = [
      ["やまだ たろう", "1990/4/1"],
      ["ﾔﾏﾀﾞ　ﾀﾛｳ", "１９９０／０４／０１"],
      ["ヤマダタロウ", "19900401"],
      ["ヤマダ  タロウ", "1990.04.01"]
    ];

    for (const [reading, birthdate] of notations) {
      const type = await createEchoConfig(READING_CHAIN, [{ "name": "date" }]);
      const received = await apply(type, reading, birthdate);

      expect(received.name).toBe("ヤマダタロウ");
      expect(received.date).toBe("1990-04-01");
    }
  });

  /**
   * The chain an English name needs. NFKC folds fullwidth letters and the fullwidth hyphen, but not
   * U+2010..U+2015 or U+2212, which have no compatibility decomposition, so dashes are unified
   * explicitly. Then spaces go and case is folded.
   */
  const ENGLISH_NAME_CHAIN = [
    { "name": "normalize", "args": { "form": "NFKC" } },
    { "name": "regex_replace", "args": { "pattern": "[\\u2010-\\u2015\\u2212]", "replacement": "-" } },
    { "name": "regex_replace", "args": { "pattern": "[\\s\\u3000]+", "replacement": "" } },
    { "name": "case", "args": { "mode": "lower" } }
  ];

  it("should bring every notation of the same English name to one value", async () => {
    const notations = [
      "Smith-Jones",
      "SMITH\u2010JONES", // HYPHEN U+2010
      "ｓｍｉｔｈ－ｊｏｎｅｓ", // fullwidth letters and FULLWIDTH HYPHEN-MINUS
      " Smith \u2212 Jones ", // MINUS SIGN U+2212, with spaces
      "Smith\u3000-\u3000Jones" // ideographic spaces
    ];

    for (const name of notations) {
      const type = await createEchoConfig(ENGLISH_NAME_CHAIN, [{ "name": "date" }]);
      const received = await apply(type, name, "1990-04-01");

      expect(received.name).toBe("smith-jones");
    }
  });

  /**
   * The chain a number written by a person needs: NFKC folds fullwidth digits and the fullwidth
   * hyphen and parentheses, then everything that is not a digit goes — hyphens of any kind, the
   * prolonged sound mark typed in place of a hyphen, spaces, 〒, +.
   */
  const DIGITS_CHAIN = [
    { "name": "normalize", "args": { "form": "NFKC" } },
    { "name": "regex_replace", "args": { "pattern": "[^0-9]", "replacement": "" } }
  ];

  it("should bring every notation of the same postal code and phone number to digits only", async () => {
    const cases = [
      ["123-4567", "1234567"],
      ["〒１２３－４５６７", "1234567"],
      ["123ー4567", "1234567"], // prolonged sound mark typed as a hyphen
      ["123 4567", "1234567"],
      ["090-1234-5678", "09012345678"],
      ["090\u20101234\u20105678", "09012345678"], // HYPHEN U+2010
      ["０９０（１２３４）５６７８", "09012345678"],
      ["090 1234 5678", "09012345678"]
    ];

    for (const [written, digits] of cases) {
      const type = await createEchoConfig(DIGITS_CHAIN, [{ "name": "date" }]);
      const received = await apply(type, written, "1990-04-01");

      expect(received.name).toBe(digits);
    }
  });

  it("should take the last four digits of a phone number in any notation", async () => {
    // The international and the domestic form differ in full but agree in their last digits.
    const lastFour = [...DIGITS_CHAIN, { "name": "substring", "args": { "start": -4 } }];

    for (const written of ["+81 90-1234-5678", "090-1234-5678", "０９０‐１２３４‐５６７８"]) {
      const type = await createEchoConfig(lastFour, [{ "name": "date" }]);
      const received = await apply(type, written, "1990-04-01");

      expect(received.name).toBe("5678");
    }
  });

  it("should keep the prolonged sound mark when converting to hiragana", async () => {
    const type = await createEchoConfig(
      [{ "name": "kana", "args": { "to": "hiragana" } }],
      [{ "name": "date", "args": { "format": "uuuu/MM/dd" } }]
    );

    const received = await apply(type, "ジョーンズ", "1990-4-1");

    // ー is shared by both scripts and is not a dash, so it survives.
    expect(received.name).toBe("じょーんず");
    // The format argument reaches the function through the stored configuration.
    expect(received.date).toBe("1990/04/01");
  });

  it("should send no date rather than the raw input when it cannot be read", async () => {
    const type = await createEchoConfig([{ "name": "kana" }], [{ "name": "date" }]);

    // A Japanese-era date and an impossible date are not read; the raw text is not passed on.
    for (const unreadable of ["H2.4.1", "1990-02-30"]) {
      const received = await apply(type, "やまだ", unreadable);

      expect(["", "null", null, undefined]).toContain(received.date);
      expect(received.date).not.toBe(unreadable);
    }
  });
});
