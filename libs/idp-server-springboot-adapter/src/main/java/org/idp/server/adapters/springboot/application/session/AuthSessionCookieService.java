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
import org.idp.server.core.openid.session.AuthSessionCookieDelegate;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.config.SessionConfiguration;
import org.springframework.stereotype.Service;

/**
 * AuthSessionCookieService
 *
 * <p>Spring Boot implementation of AuthSessionCookieDelegate. Manages AUTH_SESSION cookie to
 * prevent authorization flow hijacking attacks.
 *
 * <p><b>Cookie Properties:</b>
 *
 * <ul>
 *   <li>Name: IDP_AUTH_SESSION
 *   <li>HttpOnly: Yes (prevents XSS attacks from reading the cookie)
 *   <li>Secure: Yes (HTTPS only, or configurable for development)
 *   <li>SameSite: Lax (same-origin) or None (cross-origin, requires HTTPS)
 *   <li>Path: /{tenantId}/ (scoped to tenant to prevent cookie conflicts in federation flows)
 * </ul>
 *
 * <p><b>Cross-Origin Configuration:</b>
 *
 * <p>For cross-origin deployments (app-view on different domain), set:
 *
 * <ul>
 *   <li>IDP_AUTH_SESSION_SAME_SITE=None
 *   <li>IDP_AUTH_SESSION_SECURE=true (required for SameSite=None)
 * </ul>
 *
 * <p><b>Similar to:</b> Keycloak's AUTH_SESSION_ID cookie
 *
 * @see org.idp.server.core.openid.session.AuthSessionCookieDelegate
 */
@Service
public class AuthSessionCookieService implements AuthSessionCookieDelegate {

  public static final String AUTH_SESSION_COOKIE_NAME = "IDP_AUTH_SESSION";

  private static final LoggerWrapper log = LoggerWrapper.getLogger(AuthSessionCookieService.class);

  private final HttpServletRequest httpServletRequest;
  private final HttpServletResponse httpServletResponse;
  private final boolean secureCookie;
  private final String sameSite;

  public AuthSessionCookieService(
      HttpServletRequest httpServletRequest,
      HttpServletResponse httpServletResponse,
      @org.springframework.beans.factory.annotation.Value("${idp.auth-session.secure:true}")
          boolean secureCookie,
      @org.springframework.beans.factory.annotation.Value("${idp.auth-session.same-site:Lax}")
          String sameSite) {
    this.httpServletRequest = httpServletRequest;
    this.httpServletResponse = httpServletResponse;
    this.secureCookie = secureCookie;
    this.sameSite = sameSite;
  }

  @Override
  public void setAuthSessionCookie(Tenant tenant, String authSessionId, long maxAgeSeconds) {
    SessionConfiguration sessionConfiguration = tenant.sessionConfiguration();
    String cookiePath = resolveCookiePath(tenant, sessionConfiguration);
    String cookieDomain = resolveCookieDomain(sessionConfiguration);
    boolean secure = resolveSecure(sessionConfiguration);
    String sameSiteValue = resolveSameSite(sessionConfiguration);

    Cookie authSessionCookie = new Cookie(AUTH_SESSION_COOKIE_NAME, authSessionId);
    authSessionCookie.setMaxAge((int) maxAgeSeconds);
    authSessionCookie.setPath(cookiePath);
    authSessionCookie.setHttpOnly(true);
    authSessionCookie.setSecure(secure);

    addCookieWithSameSiteAndDomain(authSessionCookie, sameSiteValue, cookieDomain);

    log.debug(
        "AUTH_SESSION cookie set: id={}, path={}, domain={} (SameSite={}, Secure={})",
        maskSessionId(authSessionId),
        cookiePath,
        cookieDomain != null ? cookieDomain : "(host only)",
        sameSiteValue,
        secure);
  }

  @Override
  public Optional<String> getAuthSessionId() {
    Cookie[] cookies = httpServletRequest.getCookies();
    if (cookies == null) {
      log.debug("AUTH_SESSION cookie not found: no cookies in request");
      return Optional.empty();
    }

    for (Cookie cookie : cookies) {
      if (AUTH_SESSION_COOKIE_NAME.equals(cookie.getName())) {
        String value = cookie.getValue();
        if (value != null && !value.isEmpty()) {
          log.debug("AUTH_SESSION cookie found: id={}", maskSessionId(value));
          return Optional.of(value);
        }
      }
    }
    log.debug("AUTH_SESSION cookie not found: {} cookie not present", AUTH_SESSION_COOKIE_NAME);
    return Optional.empty();
  }

  @Override
  public void clearAuthSessionCookie(Tenant tenant) {
    SessionConfiguration sessionConfiguration = tenant.sessionConfiguration();
    String cookiePath = resolveCookiePath(tenant, sessionConfiguration);
    String cookieDomain = resolveCookieDomain(sessionConfiguration);
    boolean secure = resolveSecure(sessionConfiguration);
    String sameSiteValue = resolveSameSite(sessionConfiguration);

    Cookie authSessionCookie = new Cookie(AUTH_SESSION_COOKIE_NAME, "");
    authSessionCookie.setMaxAge(0);
    authSessionCookie.setPath(cookiePath);
    authSessionCookie.setHttpOnly(true);
    authSessionCookie.setSecure(secure);

    addCookieWithSameSiteAndDomain(authSessionCookie, sameSiteValue, cookieDomain);

    log.debug("AUTH_SESSION cookie cleared: path={}, domain={}", cookiePath, cookieDomain);
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
    return secureCookie;
  }

  private String resolveSameSite(SessionConfiguration sessionConfiguration) {
    if (sessionConfiguration != null) {
      return sessionConfiguration.cookieSameSite();
    }
    return sameSite;
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
   * @param sessionConfiguration session configuration
   * @return cookie path in format: {basePath}/{tenantId}/
   */
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

  /**
   * Masks session ID for secure logging (shows first 8 and last 4 characters).
   *
   * @param sessionId the session ID to mask
   * @return masked session ID
   */
  private String maskSessionId(String sessionId) {
    if (sessionId == null || sessionId.length() <= 12) {
      return "***";
    }
    return sessionId.substring(0, 8) + "..." + sessionId.substring(sessionId.length() - 4);
  }
}
