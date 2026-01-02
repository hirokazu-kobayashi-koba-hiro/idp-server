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

package org.idp.server.core.openid.session;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.idp.server.core.openid.authentication.Authentication;
import org.idp.server.core.openid.identity.User;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

/**
 * OIDCSessionCoordinator
 *
 * <p>Coordinates OIDC Session Management operations. This class encapsulates the business logic for
 * session creation and management, keeping the EntryService focused on orchestration.
 *
 * <p>Session management follows the Keycloak pattern:
 *
 * <ul>
 *   <li>OPSession: Browser-level session (like Keycloak's UserSession)
 *   <li>ClientSession: Per-client session with sid for ID Token
 *   <li>IDP_IDENTITY Cookie: JWT containing opSessionId
 *   <li>IDP_SESSION Cookie: SHA256(opSessionId) for OIDC Session Management iframe
 * </ul>
 *
 * @see <a href="https://openid.net/specs/openid-connect-session-1_0.html">OIDC Session
 *     Management</a>
 */
public class OIDCSessionCoordinator {

  private static final LoggerWrapper log = LoggerWrapper.getLogger(OIDCSessionCoordinator.class);
  private static final long DEFAULT_SESSION_MAX_AGE_SECONDS = 36000L; // 10 hours

  private final OIDCSessionManager sessionManager;

  public OIDCSessionCoordinator(OIDCSessionManager sessionManager) {
    this.sessionManager = sessionManager;
  }

  /**
   * Creates an OPSession on successful authentication.
   *
   * @param tenant the tenant
   * @param user the authenticated user
   * @param authentication the authentication result
   * @return the created OPSession
   */
  public OPSession onAuthenticationSuccess(
      Tenant tenant, User user, Authentication authentication) {
    Instant authTime =
        authentication.hasAuthenticationTime()
            ? authentication.time().atZone(ZoneOffset.UTC).toInstant()
            : Instant.now();

    return sessionManager.createOPSession(
        tenant, user.sub(), user, authTime, authentication.acr(), authentication.methods());
  }

  /**
   * Computes the session hash for IDP_SESSION cookie.
   *
   * @param opSessionId the OP session ID
   * @return SHA256 hash of the session ID
   */
  public String computeSessionHash(String opSessionId) {
    return SessionHashCalculator.sha256UrlEncodedHash(opSessionId);
  }

  /**
   * Gets the OPSession from opSessionId.
   *
   * @param tenant the tenant
   * @param opSessionId the OP session ID
   * @return the OPSession, or empty if not found or expired
   */
  public Optional<OPSession> getOPSession(Tenant tenant, String opSessionId) {
    if (opSessionId == null || opSessionId.isEmpty()) {
      return Optional.empty();
    }
    return sessionManager.getOPSession(tenant, new OPSessionIdentifier(opSessionId));
  }

  /**
   * Validates if the OPSession is valid for SSO.
   *
   * @param opSession the OP session
   * @param maxAge max age from authorization request (optional)
   * @return true if session is valid
   */
  public boolean isSessionValid(OPSession opSession, Long maxAge) {
    if (opSession == null || opSession.isExpired()) {
      return false;
    }

    if (maxAge != null && maxAge > 0) {
      Instant authTime = opSession.authTime();
      Instant maxAuthTime = authTime.plusSeconds(maxAge);
      if (Instant.now().isAfter(maxAuthTime)) {
        return false;
      }
    }

    return true;
  }

  /**
   * Creates a ClientSession on authorization and returns the sid for ID Token.
   *
   * @param tenant the tenant
   * @param opSession the OP session
   * @param clientId the client ID
   * @param scopes the authorized scopes
   * @param nonce the nonce from authorization request
   * @return the ClientSession sid
   */
  public ClientSessionIdentifier onAuthorize(
      Tenant tenant, OPSession opSession, String clientId, Set<String> scopes, String nonce) {
    ClientSession clientSession =
        sessionManager.createClientSession(tenant, opSession, clientId, scopes, Map.of(), nonce);
    return clientSession.sid();
  }

  /**
   * Looks up the OPSession from a ClientSession sid (from ID Token).
   *
   * @param tenant the tenant
   * @param sid the ClientSession identifier from id_token_hint
   * @return the OPSessionIdentifier, or empty if not found
   */
  public Optional<OPSessionIdentifier> findOPSessionBySid(
      Tenant tenant, ClientSessionIdentifier sid) {
    return sessionManager.getClientSession(tenant, sid).map(ClientSession::opSessionId);
  }

  /**
   * Gets the session manager.
   *
   * @return the session manager
   */
  public OIDCSessionManager sessionManager() {
    return sessionManager;
  }

  /**
   * Checks if session management is enabled.
   *
   * @return true if session manager is available
   */
  public boolean isEnabled() {
    return sessionManager != null;
  }

  /**
   * Sets session cookies after successful authentication.
   *
   * <p>This method encapsulates the cookie setting logic that was previously in EntryService,
   * following the principle that the Coordinator should handle "how" while EntryService handles
   * "what".
   *
   * @param tenant the tenant (for session timeout configuration)
   * @param opSession the OP session
   * @param sessionCookieDelegate the delegate for setting cookies
   */
  public void registerSessionCookies(
      Tenant tenant, OPSession opSession, SessionCookieDelegate sessionCookieDelegate) {
    if (sessionCookieDelegate == null) {
      return;
    }

    long maxAgeSeconds =
        tenant.sessionConfiguration() != null
            ? tenant.sessionConfiguration().timeoutSeconds()
            : DEFAULT_SESSION_MAX_AGE_SECONDS;

    String identityTokenValue = opSession.id().value();
    String sessionHash = computeSessionHash(identityTokenValue);

    sessionCookieDelegate.registerSessionCookies(
        tenant, identityTokenValue, sessionHash, maxAgeSeconds);
  }

  /**
   * Gets the OPSession from cookie via delegate.
   *
   * @param tenant the tenant
   * @param sessionCookieDelegate the delegate for reading cookies
   * @return the OPSession, or empty if not found
   */
  public Optional<OPSession> getOPSessionFromCookie(
      Tenant tenant, SessionCookieDelegate sessionCookieDelegate) {
    if (sessionCookieDelegate == null) {
      return Optional.empty();
    }
    return sessionCookieDelegate.getIdentityToken().flatMap(token -> getOPSession(tenant, token));
  }
}
