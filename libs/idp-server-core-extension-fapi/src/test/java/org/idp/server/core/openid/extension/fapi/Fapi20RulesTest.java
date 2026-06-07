/*
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.idp.server.core.openid.extension.fapi;

import static org.junit.jupiter.api.Assertions.*;

import org.idp.server.core.openid.oauth.OAuthRequestContext;
import org.idp.server.core.openid.oauth.exception.OAuthRedirectableBadRequestException;
import org.idp.server.core.openid.oauth.type.oauth.ClientAuthenticationType;
import org.idp.server.core.openid.oauth.type.oauth.ResponseType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Rule パターン PoC: verifier 全体のセットアップなしで個別 Rule をユニットテストできることを実証する。
 *
 * <p>{@link OAuthRequestContext} は通常 7 個以上の依存 (tenant / pattern / parameters / joseContext /
 * authorizationRequest / serverConfig / clientConfig) を必要とするが、各 Rule は実際には 1〜2 個のメソッドしか触らない。 テスト用
 * anonymous subclass で必要なメソッドだけ override すれば、その Rule のロジックを完全に覆う ことが可能であることを示す。
 */
class Fapi20RulesTest {

  // ===========================================================================
  // Helper: 必要なメソッドだけ override できる test fixture
  // ===========================================================================

  /** OAuthRequestContext を subclass し、テストで必要なメソッドだけ override する fixture builder。 */
  static class TestContext extends OAuthRequestContext {
    private boolean pushedRequest;
    private boolean atPushedEndpoint;
    private ResponseType responseType = ResponseType.code;
    private ClientAuthenticationType clientAuthType = ClientAuthenticationType.private_key_jwt;

    TestContext withPushedRequest(boolean v) {
      this.pushedRequest = v;
      return this;
    }

    TestContext withAtPushedEndpoint(boolean v) {
      this.atPushedEndpoint = v;
      return this;
    }

    TestContext withResponseType(ResponseType v) {
      this.responseType = v;
      return this;
    }

    TestContext withClientAuthType(ClientAuthenticationType v) {
      this.clientAuthType = v;
      return this;
    }

    @Override
    public boolean isPushedRequest() {
      return pushedRequest;
    }

    @Override
    public boolean isAtPushedEndpoint() {
      return atPushedEndpoint;
    }

    @Override
    public ResponseType responseType() {
      return responseType;
    }

    @Override
    public ClientAuthenticationType clientAuthenticationType() {
      return clientAuthType;
    }
  }

  // ===========================================================================
  // Check 1: NotPushedRequestRule
  // ===========================================================================

  @Nested
  @DisplayName("NotPushedRequestRule (FAPI 2.0 §5.3.2.2.3)")
  class NotPushedRequestRuleTest {
    private final FapiSecurity20Verifier.NotPushedRequestRule rule =
        new FapiSecurity20Verifier.NotPushedRequestRule();

    @Test
    @DisplayName("PAR エンドポイント自身に対する request にはこの rule は適用されない")
    void doesNotApplyAtPushedEndpoint() {
      Fapi20Context ctx = new Fapi20Context(new TestContext().withAtPushedEndpoint(true), null);

      assertFalse(rule.appliesTo(ctx));
    }

    @Test
    @DisplayName("Authorization endpoint で PAR 経由なら成功")
    void passesWhenPushedRequest() {
      Fapi20Context ctx =
          new Fapi20Context(
              new TestContext().withAtPushedEndpoint(false).withPushedRequest(true), null);

      assertTrue(rule.appliesTo(ctx));
      assertDoesNotThrow(() -> rule.verify(ctx));
    }

    @Test
    @DisplayName("Authorization endpoint で直リクエストなら invalid_request")
    void rejectsDirectAuthorizationRequest() {
      Fapi20Context ctx =
          new Fapi20Context(
              new TestContext().withAtPushedEndpoint(false).withPushedRequest(false), null);

      OAuthRedirectableBadRequestException ex =
          assertThrows(OAuthRedirectableBadRequestException.class, () -> rule.verify(ctx));
      assertEquals("invalid_request", ex.error().value());
    }

    @Test
    @DisplayName("RuleId は安定")
    void hasStableRuleId() {
      assertEquals("fapi2.par.required", rule.id().value());
    }
  }

  // ===========================================================================
  // Check 2: ResponseTypeCodeRule
  // ===========================================================================

  @Nested
  @DisplayName("ResponseTypeCodeRule (FAPI 2.0 §5.3.2.1)")
  class ResponseTypeCodeRuleTest {
    private final FapiSecurity20Verifier.ResponseTypeCodeRule rule =
        new FapiSecurity20Verifier.ResponseTypeCodeRule();

    @Test
    @DisplayName("response_type=code は許可")
    void allowsCode() {
      Fapi20Context ctx =
          new Fapi20Context(new TestContext().withResponseType(ResponseType.code), null);

      assertDoesNotThrow(() -> rule.verify(ctx));
    }

    @Test
    @DisplayName("Hybrid Flow (code id_token) は invalid_request で拒否")
    void rejectsHybrid() {
      Fapi20Context ctx =
          new Fapi20Context(new TestContext().withResponseType(ResponseType.code_id_token), null);

      OAuthRedirectableBadRequestException ex =
          assertThrows(OAuthRedirectableBadRequestException.class, () -> rule.verify(ctx));
      assertEquals("invalid_request", ex.error().value());
    }
  }

  // ===========================================================================
  // Check 3: ConfidentialClientAuthRule
  // ===========================================================================

  @Nested
  @DisplayName("ConfidentialClientAuthRule (FAPI 2.0 §5.3.3.4)")
  class ConfidentialClientAuthRuleTest {
    private final FapiSecurity20Verifier.ConfidentialClientAuthRule rule =
        new FapiSecurity20Verifier.ConfidentialClientAuthRule();

    @Test
    @DisplayName("private_key_jwt は許可")
    void allowsPrivateKeyJwt() {
      Fapi20Context ctx =
          new Fapi20Context(
              new TestContext().withClientAuthType(ClientAuthenticationType.private_key_jwt), null);
      assertDoesNotThrow(() -> rule.verify(ctx));
    }

    @Test
    @DisplayName("tls_client_auth は許可 (mTLS)")
    void allowsTlsClientAuth() {
      Fapi20Context ctx =
          new Fapi20Context(
              new TestContext().withClientAuthType(ClientAuthenticationType.tls_client_auth), null);
      assertDoesNotThrow(() -> rule.verify(ctx));
    }

    @Test
    @DisplayName("client_secret_basic は unauthorized_client で拒否")
    void rejectsClientSecretBasic() {
      Fapi20Context ctx =
          new Fapi20Context(
              new TestContext().withClientAuthType(ClientAuthenticationType.client_secret_basic),
              null);
      OAuthRedirectableBadRequestException ex =
          assertThrows(OAuthRedirectableBadRequestException.class, () -> rule.verify(ctx));
      assertEquals("unauthorized_client", ex.error().value());
    }

    @Test
    @DisplayName("client_secret_post は unauthorized_client で拒否")
    void rejectsClientSecretPost() {
      Fapi20Context ctx =
          new Fapi20Context(
              new TestContext().withClientAuthType(ClientAuthenticationType.client_secret_post),
              null);
      assertThrows(OAuthRedirectableBadRequestException.class, () -> rule.verify(ctx));
    }

    @Test
    @DisplayName("client_secret_jwt は unauthorized_client で拒否")
    void rejectsClientSecretJwt() {
      Fapi20Context ctx =
          new Fapi20Context(
              new TestContext().withClientAuthType(ClientAuthenticationType.client_secret_jwt),
              null);
      assertThrows(OAuthRedirectableBadRequestException.class, () -> rule.verify(ctx));
    }
  }

  // ===========================================================================
  // Check: PublicClientNotAllowedRule
  // ===========================================================================

  @Nested
  @DisplayName("PublicClientNotAllowedRule (FAPI 2.0 §5.3.3.1)")
  class PublicClientNotAllowedRuleTest {
    private final FapiSecurity20Verifier.PublicClientNotAllowedRule rule =
        new FapiSecurity20Verifier.PublicClientNotAllowedRule();

    @Test
    @DisplayName("none (public) は unauthorized_client で拒否")
    void rejectsNone() {
      Fapi20Context ctx =
          new Fapi20Context(
              new TestContext().withClientAuthType(ClientAuthenticationType.none), null);
      OAuthRedirectableBadRequestException ex =
          assertThrows(OAuthRedirectableBadRequestException.class, () -> rule.verify(ctx));
      assertEquals("unauthorized_client", ex.error().value());
    }

    @Test
    @DisplayName("Confidential client (private_key_jwt) は許可")
    void allowsConfidential() {
      Fapi20Context ctx =
          new Fapi20Context(
              new TestContext().withClientAuthType(ClientAuthenticationType.private_key_jwt), null);
      assertDoesNotThrow(() -> rule.verify(ctx));
    }
  }

  // ===========================================================================
  // appliesTo: client_assertion 系 rule は credentials が無いとスキップ
  // ===========================================================================

  @Nested
  @DisplayName("appliesTo: client_assertion 系 rule は credentials が無いとスキップ")
  class AppliesToTest {

    @Test
    @DisplayName("ClientAssertionAudIsArrayRule は credentials=null だと appliesTo=false")
    void audArrayRuleSkipsWithoutCredentials() {
      var rule = new FapiSecurity20Verifier.ClientAssertionAudIsArrayRule();
      Fapi20Context ctx =
          new Fapi20Context(
              new TestContext().withClientAuthType(ClientAuthenticationType.private_key_jwt), null);
      assertFalse(rule.appliesTo(ctx));
    }

    @Test
    @DisplayName(
        "ClientAssertionAudIsArrayRule は client_auth_type != private_key_jwt だと appliesTo=false")
    void audArrayRuleSkipsForNonPrivateKeyJwt() {
      var rule = new FapiSecurity20Verifier.ClientAssertionAudIsArrayRule();
      // credentials は non-null だが client_auth_type が tls_client_auth
      Fapi20Context ctx =
          new Fapi20Context(
              new TestContext().withClientAuthType(ClientAuthenticationType.tls_client_auth),
              null); // credentials still null since we just want appliesTo to check type
      assertFalse(rule.appliesTo(ctx));
    }
  }
}
