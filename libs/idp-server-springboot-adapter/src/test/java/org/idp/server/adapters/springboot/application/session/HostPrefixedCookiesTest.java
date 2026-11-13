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

package org.idp.server.adapters.springboot.application.session;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Map;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.config.SessionConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * The browser proof and the linking binding are {@code __Host-} cookies. A browser keeps one only
 * when it is Secure, has Path=/ and no Domain, so the tenant's session settings must not leak into
 * them; and since only this host can set them, two by the same name are not trusted.
 */
class HostPrefixedCookiesTest {

  private static final String TENANT_A = "11111111-1111-1111-1111-111111111111";
  private static final String TENANT_B = "22222222-2222-2222-2222-222222222222";

  /** The settings that must not reach a __Host- cookie: no Secure, a domain, a context path. */
  private static Tenant tenant(String id) {
    SessionConfiguration sessionConfiguration =
        new SessionConfiguration(
            Map.of(
                "use_secure_cookie", false,
                "cookie_domain", "example.com",
                "cookie_path", "/idp-admin",
                "cookie_same_site", "None"));
    return new Tenant(
        new TenantIdentifier(id),
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        sessionConfiguration,
        null,
        null,
        null,
        null,
        true);
  }

  @Test
  void browserProofCookieIsHostPrefixedWhateverTheTenantSays() {
    MockHttpServletResponse response = new MockHttpServletResponse();
    SessionCookieService service = new SessionCookieService(new MockHttpServletRequest(), response);

    service.registerBrowserProofCookie(tenant(TENANT_A), "proof-value", 3600);

    String header = only(response.getHeaders("Set-Cookie"));
    assertTrue(header.startsWith("__Host-IDP_BROWSER_" + TENANT_A + "=proof-value;"), header);
    assertHostPrefixAttributes(header);
    assertTrue(header.contains("; HttpOnly"), header);
    assertTrue(header.contains("; SameSite=None"), "follows the session cookie: " + header);
  }

  @Test
  void logoutClearsTheBrowserProof() {
    MockHttpServletResponse response = new MockHttpServletResponse();
    SessionCookieService service = new SessionCookieService(new MockHttpServletRequest(), response);

    service.clearSessionCookies(tenant(TENANT_A));

    String cleared =
        response.getHeaders("Set-Cookie").stream()
            .filter(h -> h.startsWith("__Host-IDP_BROWSER_" + TENANT_A + "="))
            .findFirst()
            .orElseThrow();
    assertTrue(cleared.contains("Max-Age=0"), cleared);
    assertHostPrefixAttributes(cleared);
  }

  @Test
  void browserProofIsReadForItsOwnTenantOnly() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setCookies(
        new Cookie("__Host-IDP_BROWSER_" + TENANT_A, "proof-a"),
        new Cookie("__Host-IDP_BROWSER_" + TENANT_B, "proof-b"));
    SessionCookieService service = new SessionCookieService(request, new MockHttpServletResponse());

    assertEquals("proof-a", service.getBrowserProof(tenant(TENANT_A)).orElseThrow());
    assertEquals("proof-b", service.getBrowserProof(tenant(TENANT_B)).orElseThrow());
  }

  @Test
  void browserProofPresentedTwiceIsNotTrusted() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setCookies(
        new Cookie("__Host-IDP_BROWSER_" + TENANT_A, "proof-1"),
        new Cookie("__Host-IDP_BROWSER_" + TENANT_A, "proof-2"));
    SessionCookieService service = new SessionCookieService(request, new MockHttpServletResponse());

    assertTrue(service.getBrowserProof(tenant(TENANT_A)).isEmpty());
  }

  @Test
  void bindingCookieIsHostPrefixedAndLax() {
    MockHttpServletResponse response = new MockHttpServletResponse();
    AccountLinkingCookieService service =
        new AccountLinkingCookieService(new MockHttpServletRequest(), response);

    service.setBrowserBindingCookie(tenant(TENANT_A), "binding-value", 900);

    String header = only(response.getHeaders("Set-Cookie"));
    assertTrue(
        header.startsWith("__Host-IDP_LINK_BINDING_" + TENANT_A + "=binding-value;"), header);
    assertTrue(header.contains("Max-Age=900"), header);
    assertHostPrefixAttributes(header);
    assertTrue(header.contains("; HttpOnly"), header);
    assertTrue(header.contains("; SameSite=Lax"), "Lax whatever the tenant says: " + header);
  }

  @Test
  void bindingIsReadForItsOwnTenantOnlyAndNotUnderTheOldName() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setCookies(
        new Cookie("IDP_LINK_BINDING", "planted"),
        new Cookie("__Host-IDP_LINK_BINDING_" + TENANT_B, "binding-b"));
    AccountLinkingCookieService service =
        new AccountLinkingCookieService(request, new MockHttpServletResponse());

    assertTrue(service.getBrowserBindingSecret(tenant(TENANT_A)).isEmpty());
    assertEquals("binding-b", service.getBrowserBindingSecret(tenant(TENANT_B)).orElseThrow());
  }

  @Test
  void bindingPresentedTwiceIsNotTrusted() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setCookies(
        new Cookie("__Host-IDP_LINK_BINDING_" + TENANT_A, "binding-1"),
        new Cookie("__Host-IDP_LINK_BINDING_" + TENANT_A, "binding-2"));
    AccountLinkingCookieService service =
        new AccountLinkingCookieService(request, new MockHttpServletResponse());

    assertTrue(service.getBrowserBindingSecret(tenant(TENANT_A)).isEmpty());
  }

  private static void assertHostPrefixAttributes(String header) {
    assertTrue(header.contains("; Path=/;") || header.endsWith("; Path=/"), header);
    assertTrue(header.contains("; Secure"), header);
    assertFalse(header.contains("Domain="), header);
    assertFalse(header.contains("/idp-admin"), header);
  }

  private static String only(List<String> headers) {
    assertEquals(1, headers.size(), headers.toString());
    return headers.get(0);
  }
}
