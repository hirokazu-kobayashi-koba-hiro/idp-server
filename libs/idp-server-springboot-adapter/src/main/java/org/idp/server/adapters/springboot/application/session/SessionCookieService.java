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
import org.idp.server.core.openid.session.SessionCookieDelegate;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.springframework.stereotype.Service;

/**
 * SessionCookieService
 *
 * <p>Spring Boot implementation of SessionCookieDelegate. Manages OIDC Session Management cookies:
 *
 * <ul>
 *   <li>IDP_IDENTITY: Contains session information (opSessionId) - HttpOnly
 *   <li>IDP_SESSION: SHA256(opSessionId) for OIDC Session Management iframe - NOT HttpOnly
 * </ul>
 *
 * <p>Following Keycloak's cookie management pattern.
 *
 * @see <a href="https://openid.net/specs/openid-connect-session-1_0.html">OIDC Session
 *     Management</a>
 */
@Service
public class SessionCookieService implements SessionCookieDelegate {

  public static final String IDENTITY_COOKIE_NAME = "IDP_IDENTITY";
  public static final String SESSION_COOKIE_NAME = "IDP_SESSION";

  private static final LoggerWrapper log = LoggerWrapper.getLogger(SessionCookieService.class);
  private static final boolean DEFAULT_SECURE = true;
  private static final String DEFAULT_SAME_SITE = "Lax";

  private final HttpServletRequest httpServletRequest;
  private final HttpServletResponse httpServletResponse;

  public SessionCookieService(
      HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) {
    this.httpServletRequest = httpServletRequest;
    this.httpServletResponse = httpServletResponse;
  }

  @Override
  public void registerSessionCookies(
      Tenant tenant, String identityToken, String sessionHash, long maxAgeSeconds) {
    String cookiePath = resolveCookiePath(tenant);
    String cookieDomain = getCookieDomain(tenant);
    boolean secureCookie = getSecureCookie(tenant);
    String sameSite = getSameSite(tenant);

    // Set IDP_IDENTITY cookie (HttpOnly for security)
    Cookie identityCookie = new Cookie(IDENTITY_COOKIE_NAME, identityToken);
    identityCookie.setMaxAge((int) maxAgeSeconds);
    identityCookie.setPath(cookiePath);
    identityCookie.setHttpOnly(true);
    identityCookie.setSecure(secureCookie);

    // Set IDP_SESSION cookie (NOT HttpOnly - accessible by JavaScript for session management
    // iframe)
    Cookie sessionCookie = new Cookie(SESSION_COOKIE_NAME, sessionHash);
    sessionCookie.setMaxAge((int) maxAgeSeconds);
    sessionCookie.setPath(cookiePath);
    sessionCookie.setHttpOnly(false);
    sessionCookie.setSecure(secureCookie);

    // Set SameSite and Domain via header (Cookie API doesn't support SameSite directly)
    addCookieWithSameSiteAndDomain(identityCookie, sameSite, cookieDomain);
    addCookieWithSameSiteAndDomain(sessionCookie, sameSite, cookieDomain);

    log.debug(
        "Session cookies set: IDP_IDENTITY and IDP_SESSION, path={}, domain={}",
        cookiePath,
        cookieDomain != null ? cookieDomain : "(host only)");
  }

  @Override
  public Optional<String> getIdentityToken() {
    return getCookieValue(IDENTITY_COOKIE_NAME);
  }

  @Override
  public Optional<String> getSessionHash() {
    return getCookieValue(SESSION_COOKIE_NAME);
  }

  @Override
  public void clearSessionCookies(Tenant tenant) {
    String cookiePath = resolveCookiePath(tenant);
    String cookieDomain = getCookieDomain(tenant);
    boolean secureCookie = getSecureCookie(tenant);
    String sameSite = getSameSite(tenant);

    // Clear IDP_IDENTITY cookie
    Cookie identityCookie = new Cookie(IDENTITY_COOKIE_NAME, "");
    identityCookie.setMaxAge(0);
    identityCookie.setPath(cookiePath);
    identityCookie.setHttpOnly(true);
    identityCookie.setSecure(secureCookie);

    // Clear IDP_SESSION cookie
    Cookie sessionCookie = new Cookie(SESSION_COOKIE_NAME, "");
    sessionCookie.setMaxAge(0);
    sessionCookie.setPath(cookiePath);
    sessionCookie.setHttpOnly(false);
    sessionCookie.setSecure(secureCookie);

    addCookieWithSameSiteAndDomain(identityCookie, sameSite, cookieDomain);
    addCookieWithSameSiteAndDomain(sessionCookie, sameSite, cookieDomain);

    log.debug("Session cookies cleared, path={}, domain={}", cookiePath, cookieDomain);
  }

  private Optional<String> getCookieValue(String cookieName) {
    Cookie[] cookies = httpServletRequest.getCookies();
    if (cookies == null) {
      return Optional.empty();
    }

    for (Cookie cookie : cookies) {
      if (cookieName.equals(cookie.getName())) {
        String value = cookie.getValue();
        if (value != null && !value.isEmpty()) {
          return Optional.of(value);
        }
      }
    }
    return Optional.empty();
  }

  private void addCookieWithSameSiteAndDomain(Cookie cookie, String sameSite, String cookieDomain) {
    StringBuilder cookieHeader = new StringBuilder();
    cookieHeader.append(cookie.getName()).append("=").append(cookie.getValue());

    if (cookie.getMaxAge() >= 0) {
      cookieHeader.append("; Max-Age=").append(cookie.getMaxAge());
    }

    if (cookie.getPath() != null) {
      cookieHeader.append("; Path=").append(cookie.getPath());
    }

    if (cookieDomain != null && !cookieDomain.isEmpty()) {
      cookieHeader.append("; Domain=").append(cookieDomain);
    }

    if (cookie.getSecure()) {
      cookieHeader.append("; Secure");
    }

    if (cookie.isHttpOnly()) {
      cookieHeader.append("; HttpOnly");
    }

    if (sameSite != null && !sameSite.isEmpty()) {
      cookieHeader.append("; SameSite=").append(sameSite);
    }

    httpServletResponse.addHeader("Set-Cookie", cookieHeader.toString());
  }

  private String getCookieDomain(Tenant tenant) {
    if (tenant.sessionConfiguration() != null && tenant.sessionConfiguration().hasCookieDomain()) {
      return tenant.sessionConfiguration().cookieDomain();
    }
    return null;
  }

  private boolean getSecureCookie(Tenant tenant) {
    if (tenant.sessionConfiguration() != null) {
      return tenant.sessionConfiguration().useSecureCookie();
    }
    return DEFAULT_SECURE;
  }

  private String getSameSite(Tenant tenant) {
    if (tenant.sessionConfiguration() != null) {
      return tenant.sessionConfiguration().cookieSameSite();
    }
    return DEFAULT_SAME_SITE;
  }

  /**
   * Resolves cookie path considering deployment context path.
   *
   * <p>When deployed behind API Gateway or reverse proxy with a context path (e.g., /idp-admin),
   * the cookie path must include this prefix for the browser to send cookies correctly.
   *
   * <p>Resolution order:
   *
   * <ol>
   *   <li>If session_config.cookie_path is set (not "/" default), use it as base path
   *   <li>Otherwise, use servlet context path from request
   * </ol>
   *
   * @param tenant the tenant
   * @return cookie path in format: {basePath}/{tenantId}/
   */
  private String resolveCookiePath(Tenant tenant) {
    String basePath = "";
    if (tenant.sessionConfiguration() != null) {
      String configuredPath = tenant.sessionConfiguration().cookiePath();
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
