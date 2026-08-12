import { describe, expect, it, xit } from "@jest/globals";

import { clientSecretPostClient, serverConfig } from "../testConfig";
import { requestAuthorizations } from "../../oauth/request";
import { createJwtWithPrivateKey, generateJti } from "../../lib/jose";
import { toEpocTime } from "../../lib/util";

/**
 * RFC 9101: The OAuth 2.0 Authorization Framework: JWT-Secured Authorization Request (JAR)
 *
 * https://www.rfc-editor.org/rfc/rfc9101.html
 *
 * Section numbers and requirement wording are quoted verbatim so this file doubles as a
 * conformance ledger: `it` = covered here, `xit` = known-uncovered (with a pointer when the
 * behavior is already exercised by another spec file).
 */
describe("RFC 9101: JWT-Secured Authorization Request (JAR)", () => {

  const baseRequestObjectPayload = () => ({
    client_id: clientSecretPostClient.clientId,
    response_type: "code",
    state: "aiueo",
    scope: "openid profile phone email " + clientSecretPostClient.scope,
    redirect_uri: clientSecretPostClient.redirectUri,
    aud: serverConfig.issuer,
    iss: clientSecretPostClient.clientId,
    exp: toEpocTime({ adjusted: 3000 }),
    iat: toEpocTime({}),
    nbf: toEpocTime({}),
    jti: generateJti(),
  });

  const authorizeWithRequestObject = async (payload) => {
    const request = createJwtWithPrivateKey({
      payload,
      privateKey: clientSecretPostClient.requestKey,
    });
    return await requestAuthorizations({
      endpoint: serverConfig.authorizationEndpoint,
      request,
      clientId: clientSecretPostClient.clientId,
    });
  };

  describe("4.  Request Object", () => {

    it("request and request_uri parameters MUST NOT be included in Request Objects.", async () => {
      const withRequest = await authorizeWithRequestObject({
        ...baseRequestObjectPayload(),
        request: "nested-request-object",
      });
      expect(withRequest.authorizationResponse.error).toEqual("invalid_request_object");
      expect(withRequest.authorizationResponse.errorDescription).toEqual(
        "request object must not contain request parameter (JAR Section 4)"
      );

      const withRequestUri = await authorizeWithRequestObject({
        ...baseRequestObjectPayload(),
        request_uri: "https://client.example.org/request.jwt",
      });
      expect(withRequestUri.authorizationResponse.error).toEqual("invalid_request_object");
      expect(withRequestUri.authorizationResponse.errorDescription).toEqual(
        "request object must not contain request_uri parameter (JAR Section 4)"
      );
    });

    it("request and request_uri parameters MUST NOT be included in Request Objects. (non-string values are still 'included')", async () => {
      // 値の型は攻撃者が選べる。検知が値ベースだと {"request": 123} が素通りするため、
      // キー存在ベースで判定する。#1779 で値ベース → キーベースに変更した。
      const withNumericRequest = await authorizeWithRequestObject({
        ...baseRequestObjectPayload(),
        request: 123,
      });
      expect(withNumericRequest.authorizationResponse.error).toEqual("invalid_request_object");
      expect(withNumericRequest.authorizationResponse.errorDescription).toEqual(
        "request object must not contain request parameter (JAR Section 4)"
      );

      const withArrayRequestUri = await authorizeWithRequestObject({
        ...baseRequestObjectPayload(),
        request_uri: ["https://client.example.org/request.jwt"],
      });
      expect(withArrayRequestUri.authorizationResponse.error).toEqual("invalid_request_object");
      expect(withArrayRequestUri.authorizationResponse.errorDescription).toEqual(
        "request object must not contain request_uri parameter (JAR Section 4)"
      );
    });

    xit("It MUST contain all the parameters (including extension parameters) used to process the OAuth 2.0 authorization request except the request and request_uri parameters.", async () => {});

    xit("Parameter names and string values MUST be included as JSON strings.", async () => {});

    xit("Numerical values MUST be included as JSON numbers.", async () => {});

    xit("If signed, the Authorization Request Object SHOULD contain the Claims iss (issuer) and aud (audience).", async () => {
      // covered by oidc_core_6_request_object.test.js:
      //   "aud The Audience claim MUST contain the value of the Issuer Identifier for the OP ..."
    });

    xit("When both signature and encryption are being applied, the JWT MUST be signed, then encrypted.", async () => {
      // covered (positive path only) by oidc_core_6_request_object.test.js: "success pattern jwe"
    });
  });

  describe("5.  Authorization Request", () => {

    describe("5.1.  Passing a Request Object by Value", () => {

      xit("The client sends the authorization request as a Request Object to the authorization endpoint as the request parameter value.", async () => {
        // covered by oidc_core_6_request_object.test.js: "success pattern"
      });
    });

    describe("5.2.  Passing a Request Object by Reference", () => {

      // 実装あり: RequestUriPatternContextCreator が request_uri を解決する。
      // 登録済み request_uri チェック（require_request_uri_registration 相当）→ 取得 → JOSE 検証。

      xit("If this parameter is present in the authorization request, request_uri MUST NOT be present.", async () => {});

      xit("The entire Request URI SHOULD NOT exceed 512 ASCII characters.", async () => {});

      xit("The contents of the resource referenced by the request_uri MUST be a Request Object.", async () => {});

      xit("the request_uri MUST be an https URI", async () => {
        // 未確認: RequestUriPatternContextCreator は登録済みかどうかのみ検査しており、
        // スキームが https であることの検証は見当たらない。
      });

      describe("5.2.1.  URI Referencing the Request Object", () => {

        xit("the request_uri MUST have appropriate entropy for its lifetime.", async () => {});

        xit("It is RECOMMENDED that the request_uri be removed after a reasonable timeout.", async () => {});
      });

      describe("5.2.2.  Request Using the \"request_uri\" Request Parameter", () => {
        // No additional normative requirements beyond Section 5.2.
      });

      describe("5.2.3.  Authorization Server Fetches Request Object", () => {

        xit("Upon receipt of the Request, the authorization server MUST send an HTTP GET request to the request_uri.", async () => {});
      });
    });
  });

  describe("6.  Validating JWT-Based Requests", () => {

    xit("The Authorization Request Object MUST be one of the following: (a) JWS signed (b) JWS signed and JWE encrypted.", async () => {
      // 実装あり: RequestObjectVerifyable.throwExceptionIfSymmetricKey +
      // JoseContext.verifySignature()。covered (none alg rejection) by
      // oidc_core_6_request_object.test.js の同名 xit（あちらも未有効）
    });

    xit("the authorization server supporting this specification MUST only use the parameters included in the Request Object.", async () => {
      // プロファイル差あり:
      //   FapiAdvanceRequestObjectPatternFactory … request object のみを使用（本要件に準拠）
      //   RequestObjectPatternFactory (default)  … request object に無い項目はクエリパラメータに
      //                                            フォールバックしてマージする（OIDC Core §6.1 の挙動）
      // どちらを既定にするかは仕様選択の問題。#1781 で扱う。
    });

    describe("6.1.  JWE Encrypted Request Object", () => {

      xit("If the Request Object is encrypted, the authorization server MUST decrypt the JWT.", async () => {
        // covered (positive path only) by oidc_core_6_request_object.test.js: "success pattern jwe"
      });

      xit("If decryption fails, the authorization server MUST return an invalid_request_object error.", async () => {});
    });

    describe("6.2.  JWS-Signed Request Object", () => {

      xit("The authorization server MUST validate the signature of the JWS-signed Request Object.", async () => {
        // 実装あり: JoseContext.verifySignature()（by value / by reference の両経路で実行）
      });

      xit("Algorithm verification MUST be performed", async () => {
        // 実装あり: RequestObjectVerifyable.throwExceptionIfSymmetricKey（対称鍵署名を拒否）
      });

      xit("If the key is not associated with the client or if signature validation fails, the authorization server MUST return an invalid_request_object error.", async () => {});
    });

    describe("6.3.  Request Parameter Assembly and Validation", () => {

      xit("The authorization server MUST extract the set of authorization request parameters from the Request Object value.", async () => {});

      xit("The authorization server MUST only use the parameters in the Request Object.", async () => {
        // 上記 §6 と同じくプロファイル差あり。#1781 で扱う。
      });

      xit("The client ID values in the client_id request parameter and in the Request Object client_id claim MUST be identical.", async () => {
        // 未実装: RequestObjectVerifyable.verify() のチェック一覧に client_id 一致検証が無い
        // （あるのは iss == client_id の検証のみ）。#1781 で扱う。
      });

      xit("If the Client ID check or the request validation fails, then the authorization server MUST return an error.", async () => {});
    });
  });

  describe("7.  Authorization Server Response", () => {
    // No additional normative requirements specific to this section.
  });

  describe("8.  TLS Requirements", () => {

    xit("Client implementations supporting the Request Object URI method MUST support TLS.", async () => {});

    xit("confidentiality protection MUST be applied using TLS.", async () => {});

    xit("HTTP clients MUST also verify the TLS server certificate.", async () => {});

    xit("Clients MUST NOT use CN-ID identifiers.", async () => {});
  });
});
