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
import org.idp.server.core.openid.session.logout.ClientLogoutUriResolver;
import org.idp.server.core.openid.session.logout.LogoutContext;
import org.idp.server.core.openid.session.logout.LogoutOrchestrator;
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
        tenant, user.sub(), authTime, authentication.acr(), authentication.methods());
  }

  /**
   * Creates an IdentityCookieToken for the IDP_IDENTITY cookie.
   *
   * @param issuer the token issuer
   * @param opSession the OP session
   * @param maxAgeSeconds cookie max age in seconds
   * @return the identity cookie token
   */
  public IdentityCookieToken createIdentityCookieToken(
      String issuer, OPSession opSession, long maxAgeSeconds) {
    return IdentityCookieToken.create(
        issuer, opSession.sub(), opSession.id().value(), opSession.authTime(), maxAgeSeconds);
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
   * Creates a ClientSession on authorization and returns the sid for ID Token.
   *
   * @param tenant the tenant
   * @param opSessionId the OP Session ID
   * @param clientId the client ID
   * @param scopes the authorized scopes
   * @param nonce the nonce from authorization request
   * @return the ClientSession sid, or empty if session not found
   */
  public Optional<ClientSessionIdentifier> onAuthorize(
      Tenant tenant, String opSessionId, String clientId, Set<String> scopes, String nonce) {
    return getOPSession(tenant, opSessionId)
        .map(opSession -> onAuthorize(tenant, opSession, clientId, scopes, nonce));
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
   * @throws NullPointerException if tenant or opSession is null
   * @throws IllegalArgumentException if opSession has invalid id
   */
  public void setSessionCookies(
      Tenant tenant, OPSession opSession, SessionCookieDelegate sessionCookieDelegate) {
    if (sessionCookieDelegate == null) {
      return;
    }

    // Validate required parameters to fail fast on programming errors
    if (tenant == null) {
      throw new NullPointerException("tenant must not be null");
    }
    if (opSession == null) {
      throw new NullPointerException("opSession must not be null");
    }
    if (opSession.id() == null || opSession.id().value() == null) {
      throw new IllegalArgumentException("opSession.id must not be null");
    }

    long maxAgeSeconds =
        tenant.sessionConfiguration() != null
            ? tenant.sessionConfiguration().timeoutSeconds()
            : DEFAULT_SESSION_MAX_AGE_SECONDS;

    String identityTokenValue = opSession.id().value();
    String sessionHash = computeSessionHash(identityTokenValue);

    try {
      sessionCookieDelegate.setSessionCookies(identityTokenValue, sessionHash, maxAgeSeconds);
    } catch (RuntimeException e) {
      // Cookie setting failure should not break authentication flow
      // This can happen due to network issues or response already committed
      log.warn(
          "Failed to set session cookies for opSession: {}, continuing authentication: {}",
          opSession.id().value(),
          e.getMessage());
    }
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

  /**
   * Executes session logout and returns the logout result.
   *
   * <p>This method encapsulates the logout logic that was previously in EntryService. It handles:
   *
   * <ul>
   *   <li>Looking up the OPSession from the sid in the ID token
   *   <li>Creating the LogoutContext
   *   <li>Executing the logout via LogoutOrchestrator
   * </ul>
   *
   * @param tenant the tenant
   * @param sid the session ID from id_token_hint
   * @param sub the subject
   * @param clientId the client ID that initiated logout
   * @param issuer the token issuer
   * @param signingKeyJwks the signing key JWKS
   * @param signingAlgorithm the signing algorithm
   * @param logoutOrchestrator the logout orchestrator
   * @param resolver the client logout URI resolver
   * @return the logout result, or empty if session not found
   */
  public Optional<LogoutOrchestrator.LogoutResult> executeLogout(
      Tenant tenant,
      String sid,
      String sub,
      String clientId,
      String issuer,
      String signingKeyJwks,
      String signingAlgorithm,
      LogoutOrchestrator logoutOrchestrator,
      ClientLogoutUriResolver resolver) {

    if (logoutOrchestrator == null) {
      return Optional.empty();
    }

    ClientSessionIdentifier clientSessionId = new ClientSessionIdentifier(sid);
    Optional<OPSessionIdentifier> opSessionIdOpt = findOPSessionBySid(tenant, clientSessionId);

    if (opSessionIdOpt.isEmpty()) {
      log.debug("OPSession not found for sid: {}", sid);
      return Optional.empty();
    }

    try {
      LogoutContext context =
          new LogoutContext(
              opSessionIdOpt.get(), sub, clientId, issuer, signingKeyJwks, signingAlgorithm);

      LogoutOrchestrator.LogoutResult result =
          logoutOrchestrator.executeRPInitiatedLogout(tenant, context, resolver);

      return Optional.of(result);
    } catch (Exception e) {
      log.warn(
          "Failed to execute session logout notifications, but continuing logout: {}",
          e.getMessage(),
          e);
      return Optional.empty();
    }
  }
}
