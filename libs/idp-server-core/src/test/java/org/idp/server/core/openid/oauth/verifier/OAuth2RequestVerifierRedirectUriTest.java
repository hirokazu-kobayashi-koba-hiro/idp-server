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

package org.idp.server.core.openid.oauth.verifier;

import static org.junit.jupiter.api.Assertions.*;

import org.idp.server.core.openid.oauth.OAuthRequestContext;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfiguration;
import org.idp.server.core.openid.oauth.exception.OAuthBadRequestException;
import org.idp.server.core.openid.oauth.request.AuthorizationRequestBuilder;
import org.idp.server.core.openid.oauth.type.oauth.RedirectUri;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * #1835: matching redirect_uri against the registered ones, on the OAuth 2.0 profile.
 *
 * <p>RFC 6749 Section 3.1.2.3 admits any RFC 3986 Section 6 comparison; RFC 9700 Section 2.1 (BCP
 * 240, updating 6749) narrows it to exact string matching. Which of the two applies is the tenant's
 * {@code redirect_uri_exact_match_required}, so both sides are pinned here.
 */
class OAuth2RequestVerifierRedirectUriTest {

  static final String REGISTERED = "https://app.example.com/cb";

  JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();
  OAuth2RequestVerifier verifier = new OAuth2RequestVerifier();

  OAuthRequestContext context(
      String requested, boolean exactMatchRequired, String applicationType) {
    AuthorizationServerConfiguration serverConfiguration =
        jsonConverter.read(
            String.format(
                "{\"extension\":{\"redirect_uri_exact_match_required\":%s}}", exactMatchRequired),
            AuthorizationServerConfiguration.class);
    ClientConfiguration clientConfiguration =
        jsonConverter.read(
            String.format(
                "{\"client_id\":\"client-x\",\"redirect_uris\":[\"%s\",\"http://127.0.0.1:8080/cb\"],"
                    + "\"application_type\":\"%s\"}",
                REGISTERED, applicationType),
            ClientConfiguration.class);

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
        null,
        null,
        null,
        new AuthorizationRequestBuilder().add(new RedirectUri(requested)).build(),
        serverConfiguration,
        clientConfiguration);
  }

  void verify(String requested, boolean exactMatchRequired) {
    verifier.throwExceptionIfUnMatchRedirectUri(context(requested, exactMatchRequired, "web"));
  }

  @Nested
  @DisplayName("RFC 6749 comparison (default)")
  class WithoutExactMatch {

    @Test
    void acceptsAnIdenticalUri() {
      assertDoesNotThrow(() -> verify(REGISTERED, false));
    }

    @Test
    void acceptsDifferencesSyntaxBasedNormalizationRemoves() {
      // RFC 3986 Section 6.2.2 / 6.2.3: scheme and host case, and the default port, do not change
      // the destination.
      assertDoesNotThrow(() -> verify("HTTPS://APP.EXAMPLE.COM/cb", false));
      assertDoesNotThrow(() -> verify("https://app.example.com:443/cb", false));
    }

    @Test
    void acceptsAQueryTheRegisteredUriDoesNotHave() {
      // Not a normalization RFC 3986 defines — the normalized comparison simply never looks at the
      // query. Pinned because it is the difference most likely to be relied on in practice, and so
      // the one that decides whether flipping the default is safe.
      assertDoesNotThrow(() -> verify(REGISTERED + "?foo=bar", false));
    }

    @Test
    void rejectsADifferentHost() {
      assertThrows(
          OAuthBadRequestException.class, () -> verify("https://evil.example.com/cb", false));
    }
  }

  @Nested
  @DisplayName("RFC 9700 exact string matching")
  class WithExactMatch {

    @Test
    void acceptsAnIdenticalUri() {
      assertDoesNotThrow(() -> verify(REGISTERED, true));
    }

    @Test
    void rejectsDifferencesSyntaxBasedNormalizationWouldRemove() {
      assertThrows(
          OAuthBadRequestException.class, () -> verify("HTTPS://APP.EXAMPLE.COM/cb", true));
      assertThrows(
          OAuthBadRequestException.class, () -> verify("https://app.example.com:443/cb", true));
    }

    @Test
    void rejectsAQueryTheRegisteredUriDoesNotHave() {
      assertThrows(OAuthBadRequestException.class, () -> verify(REGISTERED + "?foo=bar", true));
    }

    @Test
    void saysWhichComparisonFailed() {
      // The two comparisons fail for different reasons, so an operator reading the log needs to
      // know which one was in force.
      OAuthBadRequestException exception =
          assertThrows(OAuthBadRequestException.class, () -> verify(REGISTERED + "?foo=bar", true));

      assertTrue(
          exception.getMessage().contains("exact string matching failed"), exception.getMessage());
    }
  }

  @Nested
  @DisplayName("RFC 8252 loopback exception")
  class LoopbackPort {

    /**
     * RFC 9700 Section 2.1 keeps this exception: "except for port numbers in localhost redirection
     * URIs of native apps". Turning exact matching on must not close it.
     */
    @Test
    void stillIgnoresThePortForNativeApps() {
      assertDoesNotThrow(
          () ->
              verifier.throwExceptionIfUnMatchRedirectUri(
                  context("http://127.0.0.1:54321/cb", true, "native")));
    }

    @Test
    void doesNotApplyToWebApps() {
      assertThrows(
          OAuthBadRequestException.class,
          () ->
              verifier.throwExceptionIfUnMatchRedirectUri(
                  context("http://127.0.0.1:54321/cb", true, "web")));
    }
  }
}
