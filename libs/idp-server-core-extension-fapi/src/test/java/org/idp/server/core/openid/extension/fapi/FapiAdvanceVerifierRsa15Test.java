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

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.idp.server.core.openid.oauth.OAuthRequestContext;
import org.idp.server.core.openid.oauth.OAuthRequestPattern;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfiguration;
import org.idp.server.core.openid.oauth.exception.OAuthBadRequestException;
import org.idp.server.core.openid.oauth.exception.OAuthRedirectableBadRequestException;
import org.idp.server.core.openid.oauth.request.AuthorizationRequestBuilder;
import org.idp.server.core.openid.oauth.type.oauth.RedirectUri;
import org.idp.server.platform.jose.JoseContext;
import org.idp.server.platform.jose.JsonWebEncryption;
import org.idp.server.platform.jose.JsonWebKey;
import org.idp.server.platform.jose.JsonWebSignature;
import org.idp.server.platform.jose.JsonWebTokenClaims;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * FAPI 1.0 Advanced 8.6.1: "shall not use RSA1_5". Checked here rather than in E2E because the test
 * tenants have no RSA encryption key to encrypt a request object to.
 */
class FapiAdvanceVerifierRsa15Test {

  JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();
  FapiAdvanceVerifier verifier = new FapiAdvanceVerifier();

  OAuthRequestContext context(String clientJson, JoseContext joseContext) {
    return new OAuthRequestContext(
        new Tenant(
            new TenantIdentifier("2a1b3c4d-5e6f-7a8b-9c0d-1e2f3a4b5c6d"),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            true),
        OAuthRequestPattern.REQUEST_OBJECT,
        null,
        joseContext,
        new AuthorizationRequestBuilder()
            .add(new RedirectUri("https://app.example.com/cb"))
            .build(),
        jsonConverter.read("{}", AuthorizationServerConfiguration.class),
        jsonConverter.read(clientJson, ClientConfiguration.class));
  }

  /** A compact JWE with the given key management algorithm; only its header is ever read. */
  JoseContext encryptedWith(String algorithm) throws Exception {
    String header =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(
                String.format("{\"alg\":\"%s\",\"enc\":\"A128GCM\"}", algorithm)
                    .getBytes(StandardCharsets.UTF_8));
    JsonWebEncryption jsonWebEncryption =
        JsonWebEncryption.parse(header + ".AAAA.AAAAAAAAAAAAAAAA.AAAA.AAAAAAAAAAAAAAAAAAAAAA");
    return new JoseContext(
        jsonWebEncryption,
        new JsonWebSignature(),
        new JsonWebTokenClaims(),
        null,
        new JsonWebKey());
  }

  @Nested
  @DisplayName("responses the authorization server encrypts to the client")
  class EncryptedResponse {

    @Test
    void rejectsRsa15ForTheIdToken() {
      OAuthRequestContext context =
          context(
              "{\"client_id\":\"c\",\"id_token_encrypted_response_alg\":\"RSA1_5\"}",
              new JoseContext());
      OAuthBadRequestException exception =
          assertThrows(
              OAuthBadRequestException.class,
              () -> verifier.throwIfExceptionInvalidConfig(context));
      assertEquals("unauthorized_client", exception.error().value());
    }

    @Test
    void rejectsRsa15ForJarm() {
      OAuthRequestContext context =
          context(
              "{\"client_id\":\"c\",\"authorization_encrypted_response_alg\":\"RSA1_5\"}",
              new JoseContext());
      assertThrows(
          OAuthBadRequestException.class, () -> verifier.throwIfExceptionInvalidConfig(context));
    }

    @Test
    void acceptsRsaOaep() {
      OAuthRequestContext context =
          context(
              "{\"client_id\":\"c\",\"id_token_encrypted_response_alg\":\"RSA-OAEP\"}",
              new JoseContext());
      assertDoesNotThrow(() -> verifier.throwIfExceptionInvalidConfig(context));
    }
  }

  @Nested
  @DisplayName("request objects the client encrypts")
  class EncryptedRequestObject {

    @Test
    void rejectsRsa15() throws Exception {
      OAuthRequestContext context = context("{\"client_id\":\"c\"}", encryptedWith("RSA1_5"));
      OAuthRedirectableBadRequestException exception =
          assertThrows(
              OAuthRedirectableBadRequestException.class,
              () -> verifier.throwExceptionIfRsa15EncryptedRequestObject(context));
      assertEquals("invalid_request_object", exception.error().value());
    }

    @Test
    void acceptsRsaOaep() throws Exception {
      OAuthRequestContext context = context("{\"client_id\":\"c\"}", encryptedWith("RSA-OAEP"));
      assertDoesNotThrow(() -> verifier.throwExceptionIfRsa15EncryptedRequestObject(context));
    }

    @Test
    void ignoresASignedRequestObject() {
      OAuthRequestContext context = context("{\"client_id\":\"c\"}", new JoseContext());
      assertDoesNotThrow(() -> verifier.throwExceptionIfRsa15EncryptedRequestObject(context));
    }
  }
}
