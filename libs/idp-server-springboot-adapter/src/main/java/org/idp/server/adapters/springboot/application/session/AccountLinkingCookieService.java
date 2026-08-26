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
import java.util.Optional;
import org.idp.server.account_linking.AccountLinkingCookieDelegate;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.config.SessionConfiguration;
import org.springframework.stereotype.Service;

/**
 * Spring Boot implementation of {@link AccountLinkingCookieDelegate}.
 *
 * <p>Cookie properties:
 *
 * <ul>
 *   <li>Name: IDP_LINK_BINDING
 *   <li>HttpOnly: yes — the secret is only ever compared server side
 *   <li>SameSite: <b>Lax, fixed</b> — see below
 *   <li>Path: /{tenantId}/ — matches both /linking/start and /linking/callback
 * </ul>
 *
 * <p>SameSite is deliberately not taken from the tenant's session configuration. The callback is a
 * cross-site top level GET from the external IdP; {@code Lax} is the setting that allows exactly
 * that navigation to carry the cookie, while {@code Strict} would withhold it and make every link
 * fail. {@code None} would be weaker for no benefit here.
 */
@Service
public class AccountLinkingCookieService implements AccountLinkingCookieDelegate {

  public static final String LINK_BINDING_COOKIE_NAME = "IDP_LINK_BINDING";

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
  public Optional<String> getBrowserBindingSecret() {
    Cookie[] cookies = httpServletRequest.getCookies();
    if (cookies == null) {
      return Optional.empty();
    }

    for (Cookie cookie : cookies) {
      if (LINK_BINDING_COOKIE_NAME.equals(cookie.getName())) {
        String value = cookie.getValue();
        if (value != null && !value.isEmpty()) {
          return Optional.of(value);
        }
      }
    }
    return Optional.empty();
  }

  @Override
  public void clearBrowserBindingCookie(Tenant tenant) {
    writeCookie(tenant, "", 0);
  }

  private void writeCookie(Tenant tenant, String value, int maxAge) {
    SessionConfiguration sessionConfiguration = tenant.sessionConfiguration();
    String cookiePath = resolveCookiePath(tenant, sessionConfiguration);
    String cookieDomain = resolveCookieDomain(sessionConfiguration);
    boolean secure = resolveSecure(sessionConfiguration);

    StringBuilder cookieHeader = new StringBuilder();
    cookieHeader.append(LINK_BINDING_COOKIE_NAME).append("=").append(value);
    cookieHeader.append("; Max-Age=").append(maxAge);
    cookieHeader.append("; Path=").append(cookiePath);
    if (cookieDomain != null && !cookieDomain.isEmpty()) {
      cookieHeader.append("; Domain=").append(cookieDomain);
    }
    if (secure) {
      cookieHeader.append("; Secure");
    }
    cookieHeader.append("; HttpOnly");
    cookieHeader.append("; SameSite=").append(SAME_SITE);

    httpServletResponse.addHeader("Set-Cookie", cookieHeader.toString());
  }

  private String resolveCookieDomain(SessionConfiguration sessionConfiguration) {
    if (sessionConfiguration != null && sessionConfiguration.hasCookieDomain()) {
      return sessionConfiguration.cookieDomain();
    }
    return null;
  }

  private boolean resolveSecure(SessionConfiguration sessionConfiguration) {
    if (sessionConfiguration != null) {
      return sessionConfiguration.useSecureCookie();
    }
    return true;
  }

  private String resolveCookiePath(Tenant tenant, SessionConfiguration sessionConfiguration) {
    String basePath = "";
    if (sessionConfiguration != null) {
      String configuredPath = sessionConfiguration.cookiePath();
      if (configuredPath != null && !configuredPath.equals("/")) {
        basePath = configuredPath;
      }
    }
    if (basePath.isEmpty()) {
      basePath = httpServletRequest.getContextPath();
    }
    return basePath + "/" + tenant.identifierValue() + "/";
  }
}
