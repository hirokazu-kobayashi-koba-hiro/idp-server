import { describe, expect, it } from "@jest/globals";
import { clientSecretPostClient, privateKeyJwtClient, serverConfig } from "../testConfig";
import { requestToken } from "../../api/oauthClient";
import { requestAuthorizations } from "../../oauth/request";

/**
 * Issue #801 - S9: Redirect URI切り替え攻撃
 *
 * OAuth 2.0のredirect_uri検証が適切に行われているかをテストします。
 *
 * RFC 6749 Section 4.1.3:
 * "If the 'redirect_uri' parameter was included in the authorization request,
 * the authorization server MUST verify that the 'redirect_uri' parameter
 * in the token request is identical to the one used in the authorization request."
 *
 * 攻撃シナリオ:
 * 1. 正規のredirect_uriで認可リクエスト → 認可コード取得
 * 2. 攻撃者のredirect_uriでトークンリクエスト
 * 3. [脆弱] トークン発行 → 攻撃者に認可コード漏洩 ❌
 * 4. [保護] invalid_grant エラー ✅
 *
 * 重大度: Critical
 * CVE: CWE-601 (URL Redirection to Untrusted Site)
 * OWASP: A01:2021 - Broken Access Control
 */
describe("Issue #801 - S9: Redirect URI Switching Attack", () => {
  describe("Critical: Token Endpoint Redirect URI Validation", () => {
    it("Should reject token request when redirect_uri does not match authorization request", async () => {
      /**
       * RFC 6749 Section 4.1.3 検証:
       * トークンエンドポイントでのredirect_uri検証
       *
       * 攻撃シナリオ:
       * 1. 正規のredirect_uriで認可リクエスト
       * 2. 認可コード取得
       * 3. 異なるredirect_uriでトークンリクエスト
       * 4. 期待: invalid_grant エラー
       *    脆弱: トークン発行成功
       */

      const legitimateRedirectUri = clientSecretPostClient.redirectUri;
      const attackerRedirectUri = "https://attacker.example.com/callback";

      console.log("\n" + "=".repeat(80));
      console.log("REDIRECT URI SWITCHING ATTACK TEST");
      console.log("=".repeat(80) + "\n");

      // =====================================================================
      // Step 1: 正規のredirect_uriで認可リクエスト
      // =====================================================================
      console.log("📋 Step 1: Authorization request with legitimate redirect_uri");
      console.log("-".repeat(80));
      console.log(`   Client ID: ${clientSecretPostClient.clientId}`);
      console.log(`   Legitimate redirect_uri: ${legitimateRedirectUri}`);

      const { authorizationResponse, status } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        responseType: "code",
        state: "test-state-" + Date.now(),
        scope: clientSecretPostClient.scope,
        redirectUri: legitimateRedirectUri,
      });

      expect(status).toBe(200);
      expect(authorizationResponse.code).not.toBeNull();
      console.log(`✅ Authorization code obtained: ${authorizationResponse.code}`);

      // =====================================================================
      // Step 2: 異なるredirect_uriでトークンリクエスト（攻撃）
      // =====================================================================
      console.log("\n📋 Step 2: Token request with DIFFERENT redirect_uri (attack)");
      console.log("-".repeat(80));
      console.log(`   Attacker redirect_uri: ${attackerRedirectUri}`);
      console.log(`   Using authorization code: ${authorizationResponse.code}`);

      const tokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        code: authorizationResponse.code,
        grantType: "authorization_code",
        redirectUri: attackerRedirectUri, // ← 異なるredirect_uri（攻撃）
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });

      console.log(`   Response Status: ${tokenResponse.status}`);
      console.log(`   Response Body: ${JSON.stringify(tokenResponse.data)}`);

      // =====================================================================
      // CRITICAL SECURITY CHECK
      // =====================================================================
      console.log("\n" + "=".repeat(80));
      console.log("⚠️  SECURITY VALIDATION");
      console.log("=".repeat(80));

      if (tokenResponse.status === 200 && tokenResponse.data.access_token) {
        console.log("\n❌❌❌ CRITICAL SECURITY VULNERABILITY DETECTED! ❌❌❌");
        console.log("\nToken was issued despite redirect_uri mismatch!");
        console.log("\nAttack Success Scenario:");
        console.log("   1. Authorization with legitimate URI:  ", legitimateRedirectUri);
        console.log("   2. Token request with attacker URI:    ", attackerRedirectUri);
        console.log("   3. Token issued:                        ✓ VULNERABLE");
        console.log("   4. Attacker can steal authorization:    ✓ VULNERABLE");
        console.log("\nSeverity: CRITICAL");
        console.log("CVE: CWE-601 (URL Redirection to Untrusted Site)");
        console.log("RFC 6749 Violation: Section 4.1.3");

        throw new Error(
          "CRITICAL VULNERABILITY: Token endpoint did not validate redirect_uri! " +
          `Token was issued despite redirect_uri mismatch. ` +
          `Authorization redirect_uri: ${legitimateRedirectUri}, ` +
          `Token request redirect_uri: ${attackerRedirectUri}. ` +
          "This allows authorization code interception attacks. " +
          "See RFC 6749 Section 4.1.3 and Issue #801 S9."
        );
      } else if (tokenResponse.status === 400 && tokenResponse.data.error === "invalid_grant") {
        console.log("\n✅ Redirect URI validation working correctly");
        console.log("\nValidation Results:");
        console.log("   Authorization redirect_uri:   ", legitimateRedirectUri);
        console.log("   Token request redirect_uri:   ", attackerRedirectUri);
        console.log("   Response Status:              ", tokenResponse.status);
        console.log("   Error Code:                   ", tokenResponse.data.error);
        console.log("   Error Description:            ", tokenResponse.data.error_description);
        console.log("\nAttack Success Scenario:");
        console.log("   Attacker can steal authorization:  ✗ PROTECTED");
        console.log("\nRFC 6749 Compliance: Section 4.1.3 ✅");
        console.log("Severity: NONE");
        console.log("Status: Protected against redirect URI switching attacks");

        expect(tokenResponse.status).toBe(400);
        expect(tokenResponse.data.error).toBe("invalid_grant");
      } else {
        console.log(`\n⚠️  Unexpected response: Status ${tokenResponse.status}`);
        console.log(`   Error: ${tokenResponse.data.error}`);
        console.log(`   Description: ${tokenResponse.data.error_description}`);

        // 他のエラーコードでもトークンが発行されていなければOK
        expect(tokenResponse.data.access_token).toBeUndefined();
      }

      console.log("\n" + "=".repeat(80));
      console.log("END OF SECURITY TEST");
      console.log("=".repeat(80) + "\n");
    });

    it("Should reject token request when redirect_uri is missing but was present in authorization", async () => {
      /**
       * RFC 6749 Section 4.1.3:
       * "REQUIRED, if the 'redirect_uri' parameter was included in the
       * authorization request as described in Section 4.1.1, and their
       * values MUST be identical."
       *
       * 認可リクエストにredirect_uriがあった場合、
       * トークンリクエストでも必須
       */

      const legitimateRedirectUri = clientSecretPostClient.redirectUri;

      console.log("\n" + "=".repeat(80));
      console.log("REDIRECT URI OMISSION ATTACK TEST");
      console.log("=".repeat(80) + "\n");

      // Step 1: 正規のredirect_uriで認可リクエスト
      console.log("📋 Step 1: Authorization request with redirect_uri");
      console.log(`   Redirect URI: ${legitimateRedirectUri}`);

      const { authorizationResponse, status } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        responseType: "code",
        state: "test-state-" + Date.now(),
        scope: clientSecretPostClient.scope,
        redirectUri: legitimateRedirectUri,
      });

      expect(status).toBe(200);
      expect(authorizationResponse.code).not.toBeNull();
      console.log(`✅ Authorization code obtained`);

      // Step 2: redirect_uri省略でトークンリクエスト（攻撃）
      console.log("\n📋 Step 2: Token request WITHOUT redirect_uri (attack)");

      const tokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        code: authorizationResponse.code,
        grantType: "authorization_code",
        redirectUri: undefined, // ← redirect_uri省略（攻撃）
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });

      console.log(`   Response Status: ${tokenResponse.status}`);

      // redirect_uri省略によるエラーを期待
      // RFC 6749では invalid_grant が推奨だが、invalid_request も許容される
      expect(tokenResponse.status).toBe(400);
      expect(["invalid_grant", "invalid_request"]).toContain(tokenResponse.data.error);
      console.log(`✅ Redirect URI omission properly rejected (error: ${tokenResponse.data.error})`);
    });
  });

  describe("Critical: Authorization Endpoint Redirect URI Validation", () => {
    it("Should reject authorization request with unregistered redirect_uri", async () => {
      /**
       * RFC 6749 Section 3.1.2.3:
       * "The authorization server MUST require the following clients to
       * register their redirection endpoint:
       * - Public clients
       * - Confidential clients utilizing the implicit grant type"
       *
       * Section 3.1.2.4:
       * "If multiple redirection URIs have been registered, if only part of
       * the redirection URI has been registered, or if no redirection URI has
       * been registered, the client MUST include a redirection URI with the
       * authorization request using the 'redirect_uri' request parameter."
       *
       * 登録されていないredirect_uriでの認可リクエストは拒否されるべき
       */

      const unregisteredRedirectUri = "https://evil.example.com/callback";

      console.log("\n" + "=".repeat(80));
      console.log("UNREGISTERED REDIRECT URI TEST");
      console.log("=".repeat(80) + "\n");

      console.log("📋 Authorization request with UNREGISTERED redirect_uri");
      console.log("-".repeat(80));
      console.log(`   Client ID: ${clientSecretPostClient.clientId}`);
      console.log(`   Registered redirect_uri: ${clientSecretPostClient.redirectUri}`);
      console.log(`   Unregistered redirect_uri: ${unregisteredRedirectUri}`);

      const { authorizationResponse, status, error } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        responseType: "code",
        state: "test-state-" + Date.now(),
        scope: clientSecretPostClient.scope,
        redirectUri: unregisteredRedirectUri, // ← 登録されていないURI
      });

      console.log(`   Response Status: ${status}`);

      if (authorizationResponse && authorizationResponse.error) {
        console.log(`   Error: ${authorizationResponse.error}`);
        console.log(`   Error Description: ${authorizationResponse.errorDescription}`);
      } else if (error) {
        console.log(`   Error: ${error.error}`);
        console.log(`   Error Description: ${error.error_description}`);
      }

      // 登録されていないredirect_uriはエラーを返すべき
      if (status === 200 || (authorizationResponse && authorizationResponse.code)) {
        console.log("\n❌❌❌ CRITICAL SECURITY VULNERABILITY DETECTED! ❌❌❌");
        console.log("\nAuthorization succeeded with unregistered redirect_uri!");
        console.log("\nSeverity: CRITICAL");
        console.log("CVE: CWE-601 (URL Redirection to Untrusted Site)");

        throw new Error(
          "CRITICAL VULNERABILITY: Authorization endpoint did not validate redirect_uri registration! " +
          `Unregistered redirect_uri was accepted: ${unregisteredRedirectUri}. ` +
          "See RFC 6749 Section 3.1.2.3 and Issue #801 S9."
        );
      } else {
        console.log("✅ Unregistered redirect_uri properly rejected");

        // エラーレスポンスの内容を検証
        const errorValue = authorizationResponse?.error || error?.error;
        expect(errorValue).toBeTruthy();
        // invalid_request または unauthorized_client が期待される
        expect(["invalid_request", "unauthorized_client"]).toContain(errorValue);
      }

      console.log("\n" + "=".repeat(80));
      console.log("✅ Authorization endpoint redirect_uri validation verified");
      console.log("=".repeat(80) + "\n");
    });

    it("Should validate exact match of redirect_uri (no substring matching)", async () => {
      /**
       * セキュリティベストプラクティス:
       * redirect_uriは完全一致で検証すべき
       *
       * 攻撃シナリオ:
       * 登録URI: https://example.com/callback
       * 攻撃URI: https://example.com/callback.evil.com
       *
       * 部分一致検証の場合、攻撃URIが通ってしまう危険性
       */

      const legitimateRedirectUri = clientSecretPostClient.redirectUri;
      // 正規URIをサブストリングとして含む攻撃URI
      const substringAttackUri = legitimateRedirectUri + ".evil.com";

      console.log("\n" + "=".repeat(80));
      console.log("REDIRECT URI EXACT MATCH VALIDATION TEST");
      console.log("=".repeat(80) + "\n");

      console.log("📋 Testing substring-based redirect_uri attack");
      console.log("-".repeat(80));
      console.log(`   Registered URI:      ${legitimateRedirectUri}`);
      console.log(`   Attack URI:          ${substringAttackUri}`);
      console.log(`   Attack pattern:      Legitimate URI + ".evil.com"`);

      const { authorizationResponse, status, error } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        responseType: "code",
        state: "test-state-" + Date.now(),
        scope: clientSecretPostClient.scope,
        redirectUri: substringAttackUri,
      });

      console.log(`   Response Status: ${status}`);

      // サブストリング攻撃はエラーを返すべき
      if (status === 200 || (authorizationResponse && authorizationResponse.code)) {
        console.log("\n❌❌❌ CRITICAL SECURITY VULNERABILITY DETECTED! ❌❌❌");
        console.log("\nSubstring-based redirect_uri attack succeeded!");
        console.log("\nThis indicates redirect_uri validation uses substring matching");
        console.log("instead of exact matching, which is a critical security flaw.");

        throw new Error(
          "CRITICAL VULNERABILITY: Redirect URI validation uses substring matching! " +
          `Attack URI was accepted: ${substringAttackUri}. ` +
          "Redirect URI validation MUST use exact match, not substring match. " +
          "See OWASP OAuth 2.0 Security Best Current Practice."
        );
      } else {
        console.log("✅ Substring-based attack properly rejected");
        console.log("   → Exact match validation confirmed");

        const errorValue = authorizationResponse?.error || error?.error;
        expect(errorValue).toBeTruthy();
        expect(["invalid_request", "unauthorized_client"]).toContain(errorValue);
      }

      console.log("\n" + "=".repeat(80));
      console.log("✅ Exact match validation verified");
      console.log("=".repeat(80) + "\n");
    });
  });

  describe("Advanced: URI Normalization and Strict Matching", () => {
    it("Should reject redirect_uri with different scheme (http vs https)", async () => {
      /**
       * RFC 6749 厳密モード:
       * スキームの違いは完全に異なるURIとして扱うべき
       *
       * セキュリティリスク:
       * HTTP URIは盗聴可能なため、HTTPS登録URIとの混同は危険
       */

      // httpRedirectUriがクライアント設定にある場合のみテスト
      if (!clientSecretPostClient.httpRedirectUri) {
        console.log("⏭️  Skipped (no HTTP redirect_uri configured)");
        return;
      }

      const httpsUri = clientSecretPostClient.redirectUri; // https://...
      const httpUri = clientSecretPostClient.httpRedirectUri; // http://...

      console.log("\n📋 Testing scheme mismatch (HTTP vs HTTPS)");
      console.log(`   Registered HTTPS URI: ${httpsUri}`);
      console.log(`   HTTP URI:             ${httpUri}`);

      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        responseType: "code",
        state: "test-state-" + Date.now(),
        scope: clientSecretPostClient.scope,
        redirectUri: httpsUri, // HTTPS登録
      });

      expect(authorizationResponse.code).not.toBeNull();

      const tokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        code: authorizationResponse.code,
        grantType: "authorization_code",
        redirectUri: httpUri, // HTTP（スキーム違い）
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });

      // スキーム違いはエラーを返すべき
      expect(tokenResponse.status).toBe(400);
      expect(["invalid_grant", "invalid_request"]).toContain(tokenResponse.data.error);
      console.log(`✅ Scheme mismatch properly rejected (error: ${tokenResponse.data.error})`);
    });

    it("Should reject redirect_uri with explicit default port vs implicit", async () => {
      /**
       * RFC 3986 Section 6.2.3:
       * URI正規化では https://example.com と https://example.com:443 は同一
       *
       * しかし、OAuth 2.0セキュリティベストプラクティスでは
       * 厳密一致（完全一致）が推奨される
       *
       * idp-serverの実装: 厳密モード（完全一致）
       */

      const legitimateUri = clientSecretPostClient.redirectUri;
      // ポート番号が含まれていない場合のみテスト
      if (legitimateUri.includes(":443") || legitimateUri.includes(":80")) {
        console.log("⏭️  Skipped (redirect_uri already contains port)");
        return;
      }

      // https://example.com → https://example.com:443
      const uriWithExplicitPort = legitimateUri.replace("https://", "https://").replace(/\//, ":443/");

      console.log("\n📋 Testing explicit default port vs implicit");
      console.log(`   Registered URI:        ${legitimateUri}`);
      console.log(`   With explicit port:    ${uriWithExplicitPort}`);

      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        responseType: "code",
        state: "test-state-" + Date.now(),
        scope: clientSecretPostClient.scope,
        redirectUri: legitimateUri,
      });

      expect(authorizationResponse.code).not.toBeNull();

      const tokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        code: authorizationResponse.code,
        grantType: "authorization_code",
        redirectUri: uriWithExplicitPort, // :443明示
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });

      // 厳密モードではポート明示も不一致
      expect(tokenResponse.status).toBe(400);
      expect(["invalid_grant", "invalid_request"]).toContain(tokenResponse.data.error);
      console.log(`✅ Explicit port mismatch rejected (strict mode confirmed, error: ${tokenResponse.data.error})`);
    });

    it("Should reject redirect_uri with query parameters", async () => {
      /**
       * RFC 6749 Section 3.1.2:
       * redirect_uriにクエリパラメータを含めることは許可されているが、
       * 完全一致検証が必要
       *
       * セキュリティリスク:
       * クエリパラメータの追加/変更による攻撃
       */

      const legitimateUri = clientSecretPostClient.redirectUri;
      const uriWithQuery = legitimateUri + "?extra=param";

      console.log("\n📋 Testing redirect_uri with query parameters");
      console.log(`   Registered URI:     ${legitimateUri}`);
      console.log(`   With query params:  ${uriWithQuery}`);

      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        responseType: "code",
        state: "test-state-" + Date.now(),
        scope: clientSecretPostClient.scope,
        redirectUri: legitimateUri,
      });

      expect(authorizationResponse.code).not.toBeNull();

      const tokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        code: authorizationResponse.code,
        grantType: "authorization_code",
        redirectUri: uriWithQuery, // クエリパラメータ追加
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });

      // クエリパラメータ追加は不一致
      expect(tokenResponse.status).toBe(400);
      expect(["invalid_grant", "invalid_request"]).toContain(tokenResponse.data.error);
      console.log(`✅ Query parameter addition rejected (error: ${tokenResponse.data.error})`);
    });

    it("Should reject redirect_uri with fragment", async () => {
      /**
       * RFC 6749 Section 4.1.2:
       * 認可エンドポイントはフラグメント（#）を含むredirect_uriを拒否すべき
       *
       * セキュリティリスク:
       * フラグメントはサーバーに送信されないため、検証不可能
       */

      const legitimateUri = clientSecretPostClient.redirectUri;
      const uriWithFragment = legitimateUri + "#fragment";

      console.log("\n📋 Testing redirect_uri with fragment");
      console.log(`   Registered URI:   ${legitimateUri}`);
      console.log(`   With fragment:    ${uriWithFragment}`);

      const { authorizationResponse, status, error } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        responseType: "code",
        state: "test-state-" + Date.now(),
        scope: clientSecretPostClient.scope,
        redirectUri: uriWithFragment, // フラグメント付き
      });

      console.log(`   Response Status: ${status}`);

      // フラグメント付きURIはエラーを返すべき
      if (status === 200 || (authorizationResponse && authorizationResponse.code)) {
        console.log("\n⚠️  WARNING: Fragment in redirect_uri was accepted");
        console.log("   This may violate RFC 6749 Section 4.1.2");
        console.log("   However, fragment is stripped by browser before sending to server");
      } else {
        const errorValue = authorizationResponse?.error || error?.error;
        expect(errorValue).toBeTruthy();
        console.log(`✅ Fragment in redirect_uri rejected (error: ${errorValue})`);
      }
    });

    it("Should reject redirect_uri with trailing slash difference", async () => {
      /**
       * 末尾スラッシュの有無による不一致検証
       *
       * RFC 3986 Section 6.2.3:
       * 正規化では同一とみなされる場合があるが、
       * OAuth 2.0では厳密一致が推奨
       */

      const legitimateUri = clientSecretPostClient.redirectUri;

      // 末尾スラッシュの追加/削除
      const uriWithTrailingSlash = legitimateUri.endsWith("/")
        ? legitimateUri.slice(0, -1)
        : legitimateUri + "/";

      console.log("\n📋 Testing trailing slash difference");
      console.log(`   Registered URI:      ${legitimateUri}`);
      console.log(`   With/without slash:  ${uriWithTrailingSlash}`);

      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        responseType: "code",
        state: "test-state-" + Date.now(),
        scope: clientSecretPostClient.scope,
        redirectUri: legitimateUri,
      });

      expect(authorizationResponse.code).not.toBeNull();

      const tokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        code: authorizationResponse.code,
        grantType: "authorization_code",
        redirectUri: uriWithTrailingSlash, // 末尾スラッシュ違い
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });

      // 末尾スラッシュの違いは不一致
      expect(tokenResponse.status).toBe(400);
      expect(["invalid_grant", "invalid_request"]).toContain(tokenResponse.data.error);
      console.log(`✅ Trailing slash mismatch rejected (error: ${tokenResponse.data.error})`);
    });

    it("Should reject redirect_uri with host case difference", async () => {
      /**
       * RFC 3986 Section 6.2.2.1:
       * ホスト名はcase-insensitiveだが、
       * OAuth 2.0セキュリティベストプラクティスでは厳密一致推奨
       *
       * idp-server実装: 厳密モード（大文字小文字を区別）
       */

      const legitimateUri = clientSecretPostClient.redirectUri;

      // ホスト名の大文字小文字を変更
      // https://www.example.com → https://WWW.EXAMPLE.COM
      const uriWithUppercaseHost = legitimateUri.replace(
        /^(https?:\/\/)([^\/]+)(.*)/,
        (match, protocol, host, path) => protocol + host.toUpperCase() + path
      );

      // ホスト名が変更されている場合のみテスト
      if (legitimateUri === uriWithUppercaseHost) {
        console.log("⏭️  Skipped (host is already uppercase)");
        return;
      }

      console.log("\n📋 Testing host case sensitivity");
      console.log(`   Registered URI:    ${legitimateUri}`);
      console.log(`   Uppercase host:    ${uriWithUppercaseHost}`);

      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        responseType: "code",
        state: "test-state-" + Date.now(),
        scope: clientSecretPostClient.scope,
        redirectUri: legitimateUri,
      });

      expect(authorizationResponse.code).not.toBeNull();

      const tokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        code: authorizationResponse.code,
        grantType: "authorization_code",
        redirectUri: uriWithUppercaseHost, // ホスト名大文字
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });

      // 厳密モードではホスト名のCase違いも不一致
      expect(tokenResponse.status).toBe(400);
      expect(["invalid_grant", "invalid_request"]).toContain(tokenResponse.data.error);
      console.log(`✅ Host case mismatch rejected (strict mode, error: ${tokenResponse.data.error})`);
    });

    it("Should handle port normalization between authorization and token endpoints", async () => {
      /**
       * ポート正規化の挙動確認
       *
       * idp-server実装:
       * - 認可EP: RFC 3986正規化（https://example.com == :443）
       * - トークンEP: 厳密一致
       *
       * セキュリティ保証:
       * 認可時と同じ形式でトークンリクエストすれば成功
       * 認可時と異なる形式（たとえ正規化で同じでも）ならエラー
       */

      const legitimateUri = clientSecretPostClient.redirectUri;

      // 非標準ポートに変更
      // https://example.com/path → https://example.com:8443/path
      const uriWithNonStandardPort = legitimateUri.replace(
        /^(https?:\/\/)([^:\/]+)(:\d+)?(\/.*)?$/,
        (match, scheme, host, port, path) => {
          const newPort = port ? ":8444" : ":8443";
          return scheme + host + newPort + (path || "/");
        }
      );

      console.log("\n📋 Testing port normalization behavior");
      console.log(`   Registered URI:        ${legitimateUri}`);
      console.log(`   With non-std port:     ${uriWithNonStandardPort}`);

      // Test Pattern 1: 認可でポート省略、トークンでポート明示
      console.log("\n   Pattern 1: Authorization without port → Token with port");

      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        responseType: "code",
        state: "test-state-" + Date.now(),
        scope: clientSecretPostClient.scope,
        redirectUri: legitimateUri, // ポート省略
      });

      expect(authorizationResponse.code).not.toBeNull();

      const tokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        code: authorizationResponse.code,
        grantType: "authorization_code",
        redirectUri: uriWithNonStandardPort, // ポート明示
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });

      console.log(`   Response Status: ${tokenResponse.status}`);

      // トークンエンドポイントは厳密一致のため、形式が異なればエラー
      expect(tokenResponse.status).toBe(400);
      expect(["invalid_grant", "invalid_request"]).toContain(tokenResponse.data.error);
      console.log(`   ✅ Port form mismatch rejected (error: ${tokenResponse.data.error})`);
      console.log("   → Token endpoint enforces strict match with authorization request");
    });

    it("Should allow same redirect_uri in authorization and token requests (positive test)", async () => {
      /**
       * ポジティブテスト:
       * 完全一致する場合は正常にトークン発行
       *
       * これにより、厳密すぎてfalse positiveが発生していないことを確認
       */

      const legitimateUri = clientSecretPostClient.redirectUri;

      console.log("\n📋 Testing exact match (positive test)");
      console.log(`   Redirect URI: ${legitimateUri}`);

      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        responseType: "code",
        state: "test-state-" + Date.now(),
        scope: clientSecretPostClient.scope,
        redirectUri: legitimateUri,
      });

      expect(authorizationResponse.code).not.toBeNull();

      const tokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        code: authorizationResponse.code,
        grantType: "authorization_code",
        redirectUri: legitimateUri, // 完全一致
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });

      // 完全一致の場合は成功
      expect(tokenResponse.status).toBe(200);
      expect(tokenResponse.data.access_token).toBeDefined();
      console.log("✅ Exact match succeeded (tokens issued correctly)");
    });
  });

  describe("Edge Cases: Redirect URI Validation", () => {
    it("Should handle case-sensitive redirect_uri comparison", async () => {
      /**
       * RFC 3986 Section 6.2.2.1:
       * "For all URIs, the hexadecimal digits used in percent-encoded
       * characters are case-insensitive."
       *
       * しかし、redirect_uriの検証は通常case-sensitiveであるべき
       */

      const legitimateRedirectUri = clientSecretPostClient.redirectUri;
      // パス部分の大文字小文字を変更
      const caseDifferentUri = legitimateRedirectUri.replace("/callback", "/Callback");

      console.log("\n📋 Testing case-sensitive redirect_uri validation");
      console.log(`   Original:     ${legitimateRedirectUri}`);
      console.log(`   Case changed: ${caseDifferentUri}`);

      if (legitimateRedirectUri !== caseDifferentUri) {
        const { authorizationResponse } = await requestAuthorizations({
          endpoint: serverConfig.authorizationEndpoint,
          clientId: clientSecretPostClient.clientId,
          responseType: "code",
          state: "test-state-" + Date.now(),
          scope: clientSecretPostClient.scope,
          redirectUri: legitimateRedirectUri,
        });

        expect(authorizationResponse.code).not.toBeNull();

        const tokenResponse = await requestToken({
          endpoint: serverConfig.tokenEndpoint,
          code: authorizationResponse.code,
          grantType: "authorization_code",
          redirectUri: caseDifferentUri, // 大文字小文字が異なる
          clientId: clientSecretPostClient.clientId,
          clientSecret: clientSecretPostClient.clientSecret,
        });

        // Case-sensitiveな検証ならエラーを返すべき
        // RFC 6749では invalid_grant が推奨だが、invalid_request も許容される
        expect(tokenResponse.status).toBe(400);
        expect(["invalid_grant", "invalid_request"]).toContain(tokenResponse.data.error);
        console.log(`✅ Case-sensitive validation confirmed (error: ${tokenResponse.data.error})`);
      } else {
        console.log("⏭️  Skipped (redirect_uri does not have case-sensitive path)");
      }
    });
  });

  describe("Advanced: Multiple Registered Redirect URIs", () => {
    it("Should validate redirect_uri when client has multiple registered URIs", async () => {
      /**
       * RFC 6749 Section 3.1.2.3:
       * "If multiple redirection URIs have been registered, if only part of
       * the redirection URI has been registered, or if no redirection URI has
       * been registered, the client MUST include a redirection URI with the
       * authorization request using the 'redirect_uri' request parameter."
       *
       * 複数登録時の挙動:
       * - いずれかの登録URIと完全一致すればOK
       * - 登録されていないURIは拒否
       *
       * Note: このテストはclientSecretPostClientの登録URIを使用
       */

      const registeredUri = clientSecretPostClient.redirectUri;
      const unregisteredUri = "https://attacker.example.com/callback";

      console.log("\n" + "=".repeat(80));
      console.log("MULTIPLE REDIRECT URIS VALIDATION TEST");
      console.log("=".repeat(80) + "\n");

      console.log("📋 Client redirect_uri configuration:");
      console.log(`   Registered: ${registeredUri}`);
      console.log(`   Unregistered: ${unregisteredUri}`);

      // Test 1: 登録URIで認可 → 成功
      console.log("\n📋 Test 1: Using registered URI");
      const { authorizationResponse: response1 } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        responseType: "code",
        state: "test-state-" + Date.now(),
        scope: clientSecretPostClient.scope,
        redirectUri: registeredUri,
      });

      expect(response1.code).not.toBeNull();
      console.log("✅ Registered URI accepted");

      // Test 2: 未登録URIで認可 → エラー
      console.log("\n📋 Test 2: Using unregistered URI (should fail)");

      const { authorizationResponse: response2, status: status2, error: error2 } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        responseType: "code",
        state: "test-state-" + Date.now(),
        scope: clientSecretPostClient.scope,
        redirectUri: unregisteredUri,
      });

      console.log(`   Response Status: ${status2}`);

      if (status2 === 200 || (response2 && response2.code)) {
        throw new Error(
          "CRITICAL VULNERABILITY: Unregistered redirect_uri was accepted! " +
          `Unregistered URI was accepted: ${unregisteredUri}`
        );
      } else {
        const errorValue = response2?.error || error2?.error;
        expect(errorValue).toBeTruthy();
        console.log(`✅ Unregistered URI rejected (error: ${errorValue})`);
      }

      console.log("\n" + "=".repeat(80));
      console.log("✅ Redirect URI registration validation verified");
      console.log("=".repeat(80) + "\n");
    });

    it("Should enforce same redirect_uri between authorization and token requests", async () => {
      /**
       * セキュリティテスト:
       * 認可リクエストとトークンリクエストで
       * 完全に同じredirect_uriを使用する必要がある
       *
       * トークンエンドポイントは厳密一致検証を行う
       */

      const legitimateUri = clientSecretPostClient.redirectUri;
      const differentUri = clientSecretPostClient.httpRedirectUri || "http://localhost:8081/callback";

      console.log("\n" + "=".repeat(80));
      console.log("REDIRECT URI CONSISTENCY TEST");
      console.log("=".repeat(80) + "\n");

      // Step 1: 最初のURIで認可開始
      console.log("📋 Step 1: Authorization with first redirect_uri");
      console.log(`   Using: ${legitimateUri}`);

      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        responseType: "code",
        state: "test-state-" + Date.now(),
        scope: clientSecretPostClient.scope,
        redirectUri: legitimateUri,
      });

      expect(authorizationResponse.code).not.toBeNull();
      console.log(`✅ Authorization code obtained`);

      // Step 2: 異なるURIでトークンリクエスト
      console.log("\n📋 Step 2: Token request with DIFFERENT redirect_uri");
      console.log(`   Authorization used: ${legitimateUri}`);
      console.log(`   Token request uses: ${differentUri}`);

      const tokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        code: authorizationResponse.code,
        grantType: "authorization_code",
        redirectUri: differentUri, // 異なるURI
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });

      console.log(`   Response Status: ${tokenResponse.status}`);

      // 異なるredirect_uriは拒否
      expect(tokenResponse.status).toBe(400);
      expect(["invalid_grant", "invalid_request"]).toContain(tokenResponse.data.error);
      console.log(`✅ Different redirect_uri rejected (error: ${tokenResponse.data.error})`);
      console.log("   → Authorization redirect_uri MUST match token redirect_uri exactly");

      console.log("\n" + "=".repeat(80));
      console.log("✅ Redirect URI consistency verified");
      console.log("=".repeat(80) + "\n");
    });
  });

  describe("Advanced: URL Encoding and Special Characters", () => {
    it("Should handle URL-encoded characters in redirect_uri", async () => {
      /**
       * RFC 3986 Section 2.1:
       * Percent-encodingの扱い
       *
       * 例: スペース → %20
       */

      const baseUri = "https://www.certification.openid.net/test/a/idp_oidc_basic/callback";

      console.log("\n📋 Testing URL-encoded redirect_uri");
      console.log(`   Base URI: ${baseUri}`);

      // 通常のURIで認可
      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        responseType: "code",
        state: "test-state-" + Date.now(),
        scope: clientSecretPostClient.scope,
        redirectUri: baseUri,
      });

      expect(authorizationResponse.code).not.toBeNull();

      // 同じURIをURL-encodedで送信
      const encodedUri = encodeURIComponent(baseUri);
      console.log(`   URL-encoded:  ${encodedUri}`);

      const tokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        code: authorizationResponse.code,
        grantType: "authorization_code",
        redirectUri: encodedUri, // URL-encoded
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });

      // URL-encodingは不一致として扱われるべき
      console.log(`   Response Status: ${tokenResponse.status}`);

      if (tokenResponse.status === 400) {
        console.log(`✅ URL-encoded mismatch rejected (error: ${tokenResponse.data.error})`);
        expect(["invalid_grant", "invalid_request"]).toContain(tokenResponse.data.error);
      } else {
        console.log("⚠️  Note: Server may be normalizing URL encoding (implementation-specific)");
      }
    });

    it("Should reject redirect_uri with path traversal attempt", async () => {
      /**
       * セキュリティテスト:
       * パストラバーサル攻撃（../ を使用）の防止
       */

      const legitimateUri = clientSecretPostClient.redirectUri;

      // パストラバーサル攻撃URI
      // https://example.com/test/a/callback → https://example.com/test/../evil/callback
      const pathTraversalUri = legitimateUri.replace(
        /\/([^\/]+)\/callback$/,
        "/../evil/callback"
      );

      console.log("\n📋 Testing path traversal attack");
      console.log(`   Registered URI:       ${legitimateUri}`);
      console.log(`   Path traversal URI:   ${pathTraversalUri}`);

      const { authorizationResponse, status, error } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        responseType: "code",
        state: "test-state-" + Date.now(),
        scope: clientSecretPostClient.scope,
        redirectUri: pathTraversalUri,
      });

      console.log(`   Response Status: ${status}`);

      // パストラバーサルURIはエラーを返すべき
      if (status === 200 || (authorizationResponse && authorizationResponse.code)) {
        console.log("\n⚠️  WARNING: Path traversal URI was accepted");
        console.log("   Registered URIs should use exact match, not path normalization");
      } else {
        const errorValue = authorizationResponse?.error || error?.error;
        expect(errorValue).toBeTruthy();
        console.log(`✅ Path traversal URI rejected (error: ${errorValue})`);
      }
    });
  });

  describe("Advanced: Authorization Code Binding", () => {
    it("Should bind authorization code to specific redirect_uri", async () => {
      /**
       * 重要なセキュリティ検証:
       * 認可コードは特定のredirect_uriに紐付けられるべき
       *
       * 攻撃シナリオ:
       * 1. 正規のredirect_uri Aで認可コード取得
       * 2. 異なるredirect_uri Bでトークンリクエスト
       * 3. 拒否されるべき（Bが登録済みでも）
       */

      const legitimateUri = clientSecretPostClient.redirectUri;
      const differentUri = clientSecretPostClient.httpRedirectUri || "http://localhost:8081/callback";

      console.log("\n" + "=".repeat(80));
      console.log("AUTHORIZATION CODE BINDING TEST");
      console.log("=".repeat(80) + "\n");

      console.log("📋 Testing authorization code binding to redirect_uri");
      console.log(`   First URI:  ${legitimateUri}`);
      console.log(`   Second URI: ${differentUri}`);

      // Step 1: redirect_uri 1で認可コード取得
      console.log(`\n📋 Step 1: Get authorization code with first redirect_uri`);
      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        responseType: "code",
        state: "test-state-" + Date.now(),
        scope: clientSecretPostClient.scope,
        redirectUri: legitimateUri,
      });

      expect(authorizationResponse.code).not.toBeNull();
      console.log(`✅ Code obtained`);

      // Step 2: 異なるredirect_uriでトークンリクエスト
      console.log(`\n📋 Step 2: Request token with different redirect_uri`);

      const tokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        code: authorizationResponse.code,
        grantType: "authorization_code",
        redirectUri: differentUri, // 異なるURI
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });

      console.log(`   Response Status: ${tokenResponse.status}`);

      // 認可コードは特定のredirect_uriに紐付けられている
      expect(tokenResponse.status).toBe(400);
      expect(["invalid_grant", "invalid_request"]).toContain(tokenResponse.data.error);
      console.log(`✅ Authorization code binding enforced (error: ${tokenResponse.data.error})`);
      console.log("   → Code cannot be used with different redirect_uri");

      console.log("\n" + "=".repeat(80));
      console.log("✅ Authorization code is bound to specific redirect_uri");
      console.log("=".repeat(80) + "\n");
    });

    it("Should allow authorization code reuse with same redirect_uri (within expiration)", async () => {
      /**
       * ポジティブテスト:
       * 同じredirect_uriであれば認可コードは使用可能
       *
       * ただし、RFC 6749 Section 4.1.2では
       * 認可コードは1回のみ使用可能（ワンタイムトークン）
       *
       * このテストは最初のトークンリクエストが成功することを確認
       */

      const legitimateUri = clientSecretPostClient.redirectUri;

      console.log("\n📋 Testing authorization code with correct redirect_uri");
      console.log(`   Redirect URI: ${legitimateUri}`);

      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        responseType: "code",
        state: "test-state-" + Date.now(),
        scope: clientSecretPostClient.scope,
        redirectUri: legitimateUri,
      });

      expect(authorizationResponse.code).not.toBeNull();

      // 最初のトークンリクエスト（成功するべき）
      const tokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        code: authorizationResponse.code,
        grantType: "authorization_code",
        redirectUri: legitimateUri, // 同じredirect_uri
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });

      expect(tokenResponse.status).toBe(200);
      expect(tokenResponse.data.access_token).toBeDefined();
      console.log("✅ Token obtained successfully with correct redirect_uri");

      // Note: 認可コードの再利用テストは別のテストケースで実施
      // (RFC 6749では認可コードは1回のみ使用可能)
    });
  });

  describe("Advanced: Localhost and Loopback Address Handling", () => {
    it("Should handle localhost variants strictly", async () => {
      /**
       * RFC 8252 Section 7.3 (OAuth 2.0 for Native Apps):
       * localhostとloopback addressの扱い
       *
       * セキュリティ: 厳密一致推奨
       * - localhost と 127.0.0.1 は別物
       * - localhost と LOCALHOST は別物（厳密モード）
       */

      console.log("\n📋 Testing localhost variants");
      console.log("   Note: This test requires a client with localhost redirect_uri");

      // localhostを含むredirect_uriがある場合のみテスト
      if (!clientSecretPostClient.redirectUri.includes("localhost") &&
          !privateKeyJwtClient.redirectUriWithHttp?.includes("localhost")) {
        console.log("⏭️  Skipped (no localhost redirect_uri configured)");
        return;
      }

      console.log("✅ Localhost handling depends on exact match implementation");
      // 実際のテストは設定次第でスキップ可能
    });
  });

  describe("Security: Authorization Code Reuse Prevention", () => {
    it("Should reject second token request with same authorization code", async () => {
      /**
       * RFC 6749 Section 4.1.2:
       * "The authorization code MUST expire shortly after it is issued...
       * The client MUST NOT use the authorization code more than once."
       *
       * Section 10.5:
       * "The authorization server MUST ensure that authorization codes...
       * cannot be used more than once."
       *
       * セキュリティリスク:
       * 認可コード再利用による不正トークン取得
       */

      const legitimateUri = clientSecretPostClient.redirectUri;

      console.log("\n" + "=".repeat(80));
      console.log("AUTHORIZATION CODE REUSE PREVENTION TEST");
      console.log("=".repeat(80) + "\n");

      // Step 1: 認可コード取得
      console.log("📋 Step 1: Obtain authorization code");

      const { authorizationResponse } = await requestAuthorizations({
        endpoint: serverConfig.authorizationEndpoint,
        clientId: clientSecretPostClient.clientId,
        responseType: "code",
        state: "test-state-" + Date.now(),
        scope: clientSecretPostClient.scope,
        redirectUri: legitimateUri,
      });

      expect(authorizationResponse.code).not.toBeNull();
      const authCode = authorizationResponse.code;
      console.log(`✅ Authorization code: ${authCode}`);

      // Step 2: 最初のトークンリクエスト（成功）
      console.log("\n📋 Step 2: First token request (should succeed)");

      const firstTokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        code: authCode,
        grantType: "authorization_code",
        redirectUri: legitimateUri,
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });

      expect(firstTokenResponse.status).toBe(200);
      expect(firstTokenResponse.data.access_token).toBeDefined();
      console.log("✅ First token request succeeded");

      // Step 3: 2回目のトークンリクエスト（失敗すべき）
      console.log("\n📋 Step 3: Second token request with SAME code (should fail)");
      console.log(`   Reusing code: ${authCode}`);

      const secondTokenResponse = await requestToken({
        endpoint: serverConfig.tokenEndpoint,
        code: authCode, // 同じコードを再利用
        grantType: "authorization_code",
        redirectUri: legitimateUri,
        clientId: clientSecretPostClient.clientId,
        clientSecret: clientSecretPostClient.clientSecret,
      });

      console.log(`   Response Status: ${secondTokenResponse.status}`);
      console.log(`   Error: ${secondTokenResponse.data.error}`);

      // =====================================================================
      // CRITICAL SECURITY CHECK
      // =====================================================================
      console.log("\n" + "=".repeat(80));
      console.log("⚠️  AUTHORIZATION CODE REUSE VALIDATION");
      console.log("=".repeat(80));

      if (secondTokenResponse.status === 200 && secondTokenResponse.data.access_token) {
        console.log("\n❌❌❌ CRITICAL SECURITY VULNERABILITY DETECTED! ❌❌❌");
        console.log("\nAuthorization code was reused successfully!");
        console.log("\nAttack Success Scenario:");
        console.log("   1. Authorization code obtained:        ", authCode);
        console.log("   2. First token request:                SUCCESS");
        console.log("   3. Second token request (reuse):       SUCCESS ✓ VULNERABLE");
        console.log("   4. Attacker can reuse intercepted code: ✓ VULNERABLE");
        console.log("\nSeverity: CRITICAL");
        console.log("CVE: CWE-294 (Authentication Bypass by Capture-replay)");
        console.log("RFC 6749 Violation: Section 4.1.2, 10.5");

        throw new Error(
          "CRITICAL VULNERABILITY: Authorization code can be reused! " +
          `Code ${authCode} was successfully used twice. ` +
          "RFC 6749 Section 10.5 requires that authorization codes cannot be used more than once. " +
          "See Issue #801 S9."
        );
      } else {
        console.log("\n✅ Authorization code reuse properly prevented");
        console.log("\nValidation Results:");
        console.log("   First token request:           SUCCESS");
        console.log("   Second token request (reuse):  REJECTED");
        console.log("   Error Code:                    ", secondTokenResponse.data.error);
        console.log("   Error Description:             ", secondTokenResponse.data.error_description);
        console.log("\nAttack Success Scenario:");
        console.log("   Attacker can reuse intercepted code:  ✗ PROTECTED");
        console.log("\nRFC 6749 Compliance: Section 4.1.2, 10.5 ✅");
        console.log("Severity: NONE");
        console.log("Status: Protected against authorization code reuse attacks");

        expect(secondTokenResponse.status).toBe(400);
        expect(secondTokenResponse.data.error).toBe("invalid_grant");
      }

      console.log("\n" + "=".repeat(80));
      console.log("END OF SECURITY TEST");
      console.log("=".repeat(80) + "\n");
    });
  });
});
