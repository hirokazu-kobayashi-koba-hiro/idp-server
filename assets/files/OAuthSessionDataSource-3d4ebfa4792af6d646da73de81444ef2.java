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

package org.idp.server.adapters.springboot.application.session.datasource;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.idp.server.core.openid.oauth.OAuthSession;
import org.idp.server.core.openid.oauth.OAuthSessionKey;
import org.idp.server.core.openid.oauth.repository.OAuthSessionRepository;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.springframework.stereotype.Repository;

@Repository
public class OAuthSessionDataSource implements OAuthSessionRepository {

  HttpSession httpSession;
  HttpServletRequest httpServletRequest;
  LoggerWrapper log = LoggerWrapper.getLogger(OAuthSessionDataSource.class);

  public OAuthSessionDataSource(HttpSession httpSession, HttpServletRequest httpServletRequest) {
    this.httpSession = httpSession;
    this.httpServletRequest = httpServletRequest;
  }

  @Override
  public void register(Tenant tenant, OAuthSession oAuthSession) {
    String sessionKey = oAuthSession.sessionKeyValue();
    log.debug("registerSession: {}", sessionKey);
    log.debug("register sessionId: {}", httpSession.getId());

    int timeoutSeconds = tenant.sessionConfiguration().timeoutSeconds();
    httpSession.setMaxInactiveInterval(timeoutSeconds);
    log.debug(
        "session timeout set to {} seconds for tenant {}",
        timeoutSeconds,
        tenant.identifierValue());
    httpSession.setAttribute(sessionKey, oAuthSession);
  }

  @Override
  public OAuthSession find(OAuthSessionKey oAuthSessionKey) {
    String sessionKey = oAuthSessionKey.key();
    OAuthSession oAuthSession = (OAuthSession) httpSession.getAttribute(sessionKey);
    log.debug("find sessionId: {}", httpSession.getId());
    log.debug("findSession: {}", sessionKey);
    if (oAuthSession == null) {
      log.debug("session not found");
      return new OAuthSession();
    }
    return oAuthSession;
  }

  @Override
  public void update(OAuthSession oAuthSession) {
    String sessionKey = oAuthSession.sessionKeyValue();
    String oldSessionId = httpSession.getId();
    log.debug("update sessionId (before): {}", oldSessionId);
    log.debug("updateSession: {}", sessionKey);

    // Session Fixation Attack Prevention (Issue #736)
    // Regenerate session ID when updating to authenticated session
    if (oAuthSession.hasUser()) {
      regenerateSessionId();
    }

    log.debug("update sessionId (after): {}", httpSession.getId());
    httpSession.setAttribute(sessionKey, oAuthSession);
  }

  @Override
  public void delete(OAuthSessionKey oAuthSessionKey) {
    log.debug("delete sessionId: {}", httpSession.getId());
    log.debug("deleteSession: {}", oAuthSessionKey.key());
    // FIXME every client
    httpSession.invalidate();
  }

  /**
   * Regenerates the session ID to prevent session fixation attacks.
   *
   * <p>This method is called when registering an authenticated OAuth session to ensure that the
   * session ID established before authentication cannot be reused by an attacker. This is a
   * critical security measure to prevent session fixation attacks (CWE-384).
   *
   * <p>Session Fixation Attack Prevention Flow:
   *
   * <ol>
   *   <li>Before authentication: User has session ID "abc123" (potentially from attacker)
   *   <li>User successfully authenticates and OAuth session is registered
   *   <li>This method is called: httpServletRequest.changeSessionId()
   *   <li>After authentication: New session ID "xyz789" is generated
   *   <li>Old session ID "abc123" is invalidated and cannot be used
   * </ol>
   *
   * <p>Security Considerations:
   *
   * <ul>
   *   <li>MUST be called when registering authenticated sessions (hasUser() == true)
   *   <li>MUST be called BEFORE setting session attributes
   *   <li>Works with both Servlet-based and Redis-based session management
   *   <li>The new session ID is automatically set in the response cookie
   * </ul>
   *
   * @see <a href="https://owasp.org/www-community/attacks/Session_fixation">OWASP Session
   *     Fixation</a>
   * @see <a href="https://cwe.mitre.org/data/definitions/384.html">CWE-384: Session Fixation</a>
   */
  private void regenerateSessionId() {
    try {
      String oldSessionId = httpSession.getId();

      // Change session ID (invalidates old session and creates new one)
      String newSessionId = httpServletRequest.changeSessionId();

      // Log session ID regeneration for security audit
      log.info(
          "Session ID regenerated for security (Session Fixation Prevention): {} -> {}",
          oldSessionId,
          newSessionId);
    } catch (IllegalStateException e) {
      // Session already invalidated or no session exists
      // This is acceptable - session may not exist for stateless authentication
      log.warn("Failed to regenerate session ID: {}", e.getMessage());
    }
  }
}
