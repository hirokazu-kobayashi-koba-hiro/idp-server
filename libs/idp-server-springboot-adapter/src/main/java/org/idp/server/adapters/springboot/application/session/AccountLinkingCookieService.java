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

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.idp.server.account_linking.AccountLinkingCookieDelegate;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.springframework.stereotype.Service;

/**
 * Spring Boot implementation of {@link AccountLinkingCookieDelegate}.
 *
 * <p>Cookie properties:
 *
 * <ul>
 *   <li>Name: __Host-IDP_LINK_BINDING_{tenantId}
 *   <li>HttpOnly: yes — the secret is only ever compared server side
 *   <li>SameSite: <b>Lax, fixed</b> — see below
 *   <li>Secure, Path=/ and no Domain: <b>fixed</b> — what the {@code __Host-} prefix requires
 * </ul>
 *
 * <p>SameSite is deliberately not taken from the tenant's session configuration. The callback is a
 * cross-site top level GET from the external IdP; {@code Lax} is the setting that allows exactly
 * that navigation to carry the cookie, while {@code Strict} would withhold it and make every link
 * fail. {@code None} would be weaker for no benefit here.
 *
 * <p>Secure, path and domain are not taken from it either. With the {@code __Host-} prefix the
 * browser refuses the cookie unless it is {@code Secure}, has {@code Path=/} and no {@code Domain},
 * and in exchange no other host, a sibling subdomain included, can set one by that name.
 */
@Service
public class AccountLinkingCookieService implements AccountLinkingCookieDelegate {

  public static final String LINK_BINDING_COOKIE_PREFIX = "__Host-IDP_LINK_BINDING_";

  private static final String SAME_SITE = "Lax";
  private static final LoggerWrapper log =
      LoggerWrapper.getLogger(AccountLinkingCookieService.class);

  private final HttpServletRequest httpServletRequest;
  private final HttpServletResponse httpServletResponse;

  public AccountLinkingCookieService(
      HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) {
    this.httpServletRequest = httpServletRequest;
    this.httpServletResponse = httpServletResponse;
  }

  @Override
  public void setBrowserBindingCookie(Tenant tenant, String secret, long maxAgeSeconds) {
    writeCookie(tenant, secret, (int) maxAgeSeconds);
    log.debug("Account linking binding cookie set: tenant={}", tenant.identifierValue());
  }

  @Override
  public Optional<String> getBrowserBindingSecret(Tenant tenant) {
    String cookieName = cookieName(tenant);
    Cookie[] cookies = httpServletRequest.getCookies();
    if (cookies == null) {
      return Optional.empty();
    }
    List<String> values =
        Arrays.stream(cookies)
            .filter(cookie -> cookieName.equals(cookie.getName()))
            .map(Cookie::getValue)
            .filter(value -> value != null && !value.isEmpty())
            .toList();
    // Only this host can set the cookie, so two of them is not a browser that went through
    // /linking/start here; neither is trusted.
    if (values.size() != 1) {
      if (values.size() > 1) {
        log.warn(
            "Account linking binding cookie presented more than once: tenant={}, count={}",
            tenant.identifierValue(),
            values.size());
      }
      return Optional.empty();
    }
    return Optional.of(values.get(0));
  }

  @Override
  public void clearBrowserBindingCookie(Tenant tenant) {
    writeCookie(tenant, "", 0);
  }

  private void writeCookie(Tenant tenant, String value, int maxAge) {
    StringBuilder cookieHeader = new StringBuilder();
    cookieHeader.append(cookieName(tenant)).append("=").append(value);
    cookieHeader.append("; Max-Age=").append(maxAge);
    cookieHeader.append("; Path=/");
    cookieHeader.append("; Secure");
    cookieHeader.append("; HttpOnly");
    cookieHeader.append("; SameSite=").append(SAME_SITE);

    httpServletResponse.addHeader("Set-Cookie", cookieHeader.toString());
  }

  private String cookieName(Tenant tenant) {
    return LINK_BINDING_COOKIE_PREFIX + tenant.identifierValue();
  }
}
