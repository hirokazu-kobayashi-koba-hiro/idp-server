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

package org.idp.server.platform.multi_tenancy.tenant.config;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Reading the tenant's UI configuration, and the variants a canary release runs beside it. */
class UIConfigurationTest {

  @Nested
  @DisplayName("a tenant that declares no variant")
  class WithoutVariants {

    @Test
    void keepsBehavingAsBefore() {
      UIConfiguration configuration =
          new UIConfiguration(Map.of("base_url", "https://auth.example.com"));

      assertFalse(configuration.hasVariants());
      assertEquals("/auth-views/signin/index.html", configuration.signinPage());
    }

    @Test
    void resolvesAnyNameToAnInheritingVariant() {
      UIConfiguration configuration = new UIConfiguration(Map.of());

      UIViewVariant variant = configuration.variant("v2");

      assertFalse(variant.hasBaseUrl());
      assertFalse(variant.hasSigninPage());
      assertFalse(variant.hasSignupPage());
    }

    @Test
    void namesViewVersionAsTheParameterByDefault() {
      assertEquals("view_version", new UIConfiguration(Map.of()).variantParam());
    }
  }

  @Nested
  @DisplayName("a tenant running a variant beside its default pages")
  class WithVariants {

    UIConfiguration configuration() {
      return new UIConfiguration(
          Map.of(
              "base_url", "https://auth.example.com",
              "signin_page", "/v1/signin",
              "signup_page", "/v1/signup",
              "variants",
                  Map.of(
                      "v2",
                      Map.of(
                          "base_url", "https://auth-next.example.com",
                          "signin_page", "/signin"))));
    }

    @Test
    void resolvesADeclaredName() {
      UIViewVariant variant = configuration().variant("v2");

      assertEquals("https://auth-next.example.com", variant.baseUrl());
      assertEquals("/signin", variant.signinPage());
    }

    @Test
    void leavesWhatTheVariantOmittedToBeInherited() {
      // The variant moves the sign-in page only, so the caller falls back for the sign-up page.
      assertFalse(configuration().variant("v2").hasSignupPage());
    }

    @Test
    void resolvesAnUndeclaredNameToAnInheritingVariant() {
      // The authorization URL is public, so a name nobody declared has to land on the defaults
      // rather than fail the request.
      UIViewVariant variant = configuration().variant("v9");

      assertFalse(variant.hasBaseUrl());
      assertFalse(variant.hasSigninPage());
    }

    @Test
    void resolvesABlankNameToAnInheritingVariant() {
      assertFalse(configuration().variant("").hasSigninPage());
      assertFalse(configuration().variant(null).hasSigninPage());
    }

    @Test
    void survivesTheRoundTripThroughAMap() {
      // Tenant configuration is updated by replacing the whole document, so a variant that does not
      // come back out of toMap would be dropped by a read-modify-write.
      UIConfiguration restored = new UIConfiguration(configuration().toMap());

      assertTrue(restored.hasVariants());
      assertEquals("https://auth-next.example.com", restored.variant("v2").baseUrl());
      assertEquals("/signin", restored.variant("v2").signinPage());
      assertFalse(restored.variant("v2").hasSignupPage());
    }
  }

  @Nested
  @DisplayName("a tenant that moves the selection aside")
  class WithCustomVariantParam {

    @Test
    void readsTheConfiguredParameterName() {
      UIConfiguration configuration =
          new UIConfiguration(Map.of("variant_param", "auth_view_release"));

      assertEquals("auth_view_release", configuration.variantParam());
    }
  }

  @Nested
  @DisplayName("a malformed declaration")
  class Malformed {

    @Test
    void ignoresAVariantThatIsNotAnObject() {
      UIConfiguration configuration =
          new UIConfiguration(Map.of("variants", Map.of("v2", "/v2/signin")));

      assertFalse(configuration.hasVariants());
    }

    @Test
    void ignoresVariantsThatAreNotAnObject() {
      UIConfiguration configuration = new UIConfiguration(Map.of("variants", "v2"));

      assertFalse(configuration.hasVariants());
    }
  }
}
