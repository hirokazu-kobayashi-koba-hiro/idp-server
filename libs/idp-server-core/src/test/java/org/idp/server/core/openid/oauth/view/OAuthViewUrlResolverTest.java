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

package org.idp.server.core.openid.oauth.view;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.idp.server.core.openid.oauth.OAuthRequestContext;
import org.idp.server.core.openid.oauth.request.AuthorizationRequest;
import org.idp.server.core.openid.oauth.request.AuthorizationRequestBuilder;
import org.idp.server.core.openid.oauth.request.AuthorizationRequestIdentifier;
import org.idp.server.core.openid.oauth.type.oauth.CustomParams;
import org.idp.server.core.openid.oauth.type.oidc.Prompt;
import org.idp.server.core.openid.oauth.type.oidc.Prompts;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantDomain;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.config.UIConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Choosing the page an authorization request is sent to. */
class OAuthViewUrlResolverTest {

  static final String TENANT_ID = "2a1b3c4d-5e6f-7a8b-9c0d-1e2f3a4b5c6d";

  static final Map<String, Object> WITH_VARIANT =
      Map.of(
          "base_url", "https://auth.example.com",
          "signin_page", "/v1/signin",
          "signup_page", "/v1/signup",
          "variants",
              Map.of(
                  "v2",
                  Map.of(
                      "base_url", "https://auth-next.example.com",
                      "signin_page", "/signin")));

  static Tenant tenant(Map<String, Object> uiConfig) {
    return new Tenant(
        new TenantIdentifier(TENANT_ID),
        null,
        null,
        new TenantDomain("https://fallback.example.com"),
        null,
        null,
        new UIConfiguration(uiConfig),
        null,
        null,
        null,
        null,
        null,
        null,
        true);
  }

  static OAuthRequestContext context(Tenant tenant, AuthorizationRequest request) {
    return new OAuthRequestContext(tenant, null, null, null, request, null, null);
  }

  static AuthorizationRequestBuilder request() {
    return new AuthorizationRequestBuilder()
        .add(new AuthorizationRequestIdentifier("a4c9e1f0-1111-2222-3333-444455556666"))
        .add(new TenantIdentifier(TENANT_ID));
  }

  static AuthorizationRequestBuilder requestAskingFor(String variant) {
    return request().add(new CustomParams(Map.of("view_version", variant)));
  }

  @Nested
  @DisplayName("a tenant that declares no variant")
  class WithoutVariants {

    @Test
    void sendsTheRequestToTheConfiguredPage() {
      Tenant tenant =
          tenant(Map.of("base_url", "https://auth.example.com", "signin_page", "/v1/signin"));

      String url = OAuthViewUrlResolver.resolve(context(tenant, request().build()));

      assertTrue(url.startsWith("https://auth.example.com/v1/signin?"), url);
    }

    @Test
    void ignoresAVariantNobodyDeclared() {
      Tenant tenant =
          tenant(Map.of("base_url", "https://auth.example.com", "signin_page", "/v1/signin"));

      String url = OAuthViewUrlResolver.resolve(context(tenant, requestAskingFor("v2").build()));

      assertTrue(url.startsWith("https://auth.example.com/v1/signin?"), url);
    }

    @Test
    void fallsBackToTheTenantDomainWithoutABaseUrl() {
      Tenant tenant = tenant(Map.of("signin_page", "/v1/signin"));

      String url = OAuthViewUrlResolver.resolve(context(tenant, request().build()));

      assertTrue(url.startsWith("https://fallback.example.com/v1/signin?"), url);
    }
  }

  @Nested
  @DisplayName("a tenant running a variant beside its default pages")
  class WithVariants {

    @Test
    void sendsARequestNamingNoVariantToTheDefaultPage() {
      String url = OAuthViewUrlResolver.resolve(context(tenant(WITH_VARIANT), request().build()));

      assertTrue(url.startsWith("https://auth.example.com/v1/signin?"), url);
    }

    @Test
    void sendsARequestNamingTheVariantToItsOwnDeployment() {
      String url =
          OAuthViewUrlResolver.resolve(
              context(tenant(WITH_VARIANT), requestAskingFor("v2").build()));

      assertTrue(url.startsWith("https://auth-next.example.com/signin?"), url);
    }

    @Test
    void inheritsThePageTheVariantDidNotMove() {
      // The variant moves its base URL and sign-in page only, so prompt=create keeps the default
      // sign-up path under the variant's own base URL.
      AuthorizationRequest request =
          requestAskingFor("v2").add(new Prompts(List.of(Prompt.create))).build();

      String url = OAuthViewUrlResolver.resolve(context(tenant(WITH_VARIANT), request));

      assertTrue(url.startsWith("https://auth-next.example.com/v1/signup?"), url);
    }

    @Test
    void sendsARequestNamingAnUndeclaredVariantToTheDefaultPage() {
      // The authorization URL is public, so a name nobody declared has to be harmless.
      String url =
          OAuthViewUrlResolver.resolve(
              context(tenant(WITH_VARIANT), requestAskingFor("../../admin").build()));

      assertTrue(url.startsWith("https://auth.example.com/v1/signin?"), url);
    }

    @Test
    void keepsTheVariantNameOnTheUrlForThePageToRead() {
      String url =
          OAuthViewUrlResolver.resolve(
              context(tenant(WITH_VARIANT), requestAskingFor("v2").build()));

      assertTrue(url.contains("view_version=v2"), url);
    }
  }

  @Nested
  @DisplayName("a tenant that moves the selection aside")
  class WithCustomVariantParam {

    @Test
    void readsTheVariantFromTheConfiguredParameter() {
      Tenant tenant =
          tenant(
              Map.of(
                  "base_url", "https://auth.example.com",
                  "signin_page", "/v1/signin",
                  "variant_param", "auth_view_release",
                  "variants", Map.of("v2", Map.of("signin_page", "/v2/signin"))));
      AuthorizationRequest request =
          request().add(new CustomParams(Map.of("auth_view_release", "v2"))).build();

      String url = OAuthViewUrlResolver.resolve(context(tenant, request));

      assertTrue(url.startsWith("https://auth.example.com/v2/signin?"), url);
    }

    @Test
    void ignoresTheDefaultParameterOnceTheSelectionMoved() {
      Tenant tenant =
          tenant(
              Map.of(
                  "base_url", "https://auth.example.com",
                  "signin_page", "/v1/signin",
                  "variant_param", "auth_view_release",
                  "variants", Map.of("v2", Map.of("signin_page", "/v2/signin"))));

      String url = OAuthViewUrlResolver.resolve(context(tenant, requestAskingFor("v2").build()));

      assertTrue(url.startsWith("https://auth.example.com/v1/signin?"), url);
    }
  }
}
