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
import org.idp.server.core.openid.authentication.policy.AuthenticationPolicy;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.oauth.request.AuthorizationRequest;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.type.RequestAttributes;

/**
 * OIDCSessionHandler
 *
 * <p>Handles OIDC Session Management operations. This class encapsulates the business logic for
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
public class OIDCSessionHandler {

  private static final LoggerWrapper log = LoggerWrapper.getLogger(OIDCSessionHandler.class);
  private static final long DEFAULT_SESSION_MAX_AGE_SECONDS = 36000L; // 10 hours

  private final OIDCSessionService sessionService;
  private final OIDCSessionVerifier sessionVerifier;

  public OIDCSessionHandler(OIDCSessionService sessionService) {
    this.sessionService = sessionService;
    this.sessionVerifier = new OIDCSessionVerifier();
  }

  /**
   * Handles session creation or reuse on successful authentication.
   *
   * <p>This method implements session switch policy:
   *
   * <ul>
   *   <li>Same user with active session: Reuse existing session (touch lastAccessedAt)
   *   <li>Different user with STRICT policy: Throw DifferentUserAuthenticatedException
   *   <li>Different user with SWITCH_ALLOWED policy: Terminate old session, create new
   *   <li>Different user with MULTI_SESSION policy: Create new session (old remains)
   *   <li>No existing session: Create new session
   * </ul>
   *
   * @param tenant the tenant
   * @param user the authenticated user
   * @param authentication the authentication result
   * @param interactionResults the authentication interaction results for policy evaluation
   * @param existingSession the existing OPSession from cookie, or null if none
   * @return the OPSession (reused or newly created)
   * @throws DifferentUserAuthenticatedException if STRICT policy and different user
   */
  public OPSession onAuthenticationSuccess(
      Tenant tenant,
      User user,
      Authentication authentication,
      Map<String, Map<String, Object>> interactionResults,
      OPSession existingSession,
      RequestAttributes requestAttributes) {

    // Check if we have an active existing session
    if (existingSession != null && existingSession.isActive()) {
      String existingSub = existingSession.sub();
      String authenticatedSub = user.sub();

      // Same user: reuse existing session
      if (existingSub != null && existingSub.equals(authenticatedSub)) {
        log.debug(
            "Reusing existing OPSession for same user. sessionId:{}, sub:{}",
            existingSession.id().value(),
            existingSub);
        sessionService.touchOPSession(tenant, existingSession);
        return existingSession;
      }

      // Different user: apply session switch policy
      SessionSwitchPolicy policy = getSessionSwitchPolicy(tenant);
      log.debug(
          "Different user authenticated. existingSub:{}, authenticatedSub:{}, policy:{}",
          existingSub,
          authenticatedSub,
          policy);

      switch (policy) {
        case STRICT:
          throw new DifferentUserAuthenticatedException(existingSub, authenticatedSub);

        case SWITCH_ALLOWED:
          // Terminate old session and create new
          sessionService.terminateOPSession(
              tenant, existingSession.id(), TerminationReason.USER_SWITCH);
          log.debug(
              "Terminated existing session due to user switch. sessionId:{}",
              existingSession.id().value());
          break;

        case MULTI_SESSION:
        default:
          // Just create new session, old one remains until TTL
          log.debug(
              "Creating new session (MULTI_SESSION mode). Old session remains: {}",
              existingSession.id().value());
          break;
      }
    }

    // Create new session
    return createNewOPSession(tenant, user, authentication, interactionResults, requestAttributes);
  }

  private OPSession createNewOPSession(
      Tenant tenant,
      User user,
      Authentication authentication,
      Map<String, Map<String, Object>> interactionResults,
      RequestAttributes requestAttributes) {
    Instant authTime =
        authentication.hasAuthenticationTime()
            ? authentication.time().atZone(ZoneOffset.UTC).toInstant()
            : Instant.now();

    String ipAddress =
        requestAttributes != null && requestAttributes.hasIpAddress()
            ? requestAttributes.getIpAddress().value()
            : null;
    String userAgent =
        requestAttributes != null && requestAttributes.hasUserAgent()
            ? requestAttributes.getUserAgent().value()
            : null;

    long sessionTimeoutSeconds = getSessionTimeoutSeconds(tenant);
    return sessionService.createOPSession(
        tenant,
        user,
        authTime,
        authentication.acr(),
        authentication.methods(),
        interactionResults,
        sessionTimeoutSeconds,
        ipAddress,
        userAgent);
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
    return sessionService.getOPSession(tenant, new OPSessionIdentifier(opSessionId));
  }

  /**
   * Validates session for authorize-with-session flow.
   *
   * <p>Performs comprehensive validation including:
   *
   * <ul>
   *   <li>Session existence and expiration
   *   <li>max_age constraint from authorization request
   *   <li>acr_values constraint - prevents ACR downgrade attacks
   *   <li>Authentication policy successConditions - prevents policy bypass
   * </ul>
   *
   * @param opSession the OP session (may be null)
   * @param authorizationRequest the authorization request
   * @param authenticationPolicy the authentication policy for the client
   * @return validation result with error details if invalid
   */
  public SessionValidationResult validateSessionForAuthorization(
      OPSession opSession,
      AuthorizationRequest authorizationRequest,
      AuthenticationPolicy authenticationPolicy) {
    return sessionVerifier.verifyForAuthorization(
        opSession, authorizationRequest, authenticationPolicy);
  }

  /**
   * Gets or creates a ClientSession on authorization and returns the sid for ID Token.
   *
   * <p>If a ClientSession already exists for the same OPSession and clientId, it will be reused
   * (refreshed with new authorization context). This prevents accumulation of stale ClientSessions
   * in Redis when the same user authorizes the same client multiple times.
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
    long sessionTimeoutSeconds = getSessionTimeoutSeconds(tenant);
    ClientSession clientSession =
        sessionService.getOrCreateClientSession(
            tenant, opSession, clientId, scopes, Map.of(), nonce, sessionTimeoutSeconds);
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
    return sessionService.getClientSession(tenant, sid).map(ClientSession::opSessionId);
  }

  /**
   * Terminates the OP session and all associated client sessions.
   *
   * @param tenant the tenant
   * @param opSessionId the OP session identifier
   * @param reason the termination reason
   * @return the client sessions that were terminated
   */
  public ClientSessions terminateOPSession(
      Tenant tenant, OPSessionIdentifier opSessionId, TerminationReason reason) {
    return sessionService.terminateOPSession(tenant, opSessionId, reason);
  }

  /**
   * Sets session cookies after successful authentication.
   *
   * @param tenant the tenant (for session timeout configuration)
   * @param opSession the OP session
   * @param sessionCookieDelegate the delegate for setting cookies
   */
  public void registerSessionCookies(
      Tenant tenant, OPSession opSession, SessionCookieDelegate sessionCookieDelegate) {
    long maxAgeSeconds = getSessionTimeoutSeconds(tenant);

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
    return sessionCookieDelegate.getIdentityToken().flatMap(token -> getOPSession(tenant, token));
  }

  /**
   * Gets the session timeout from tenant configuration.
   *
   * @param tenant the tenant
   * @return session timeout in seconds
   */
  private long getSessionTimeoutSeconds(Tenant tenant) {
    if (tenant.sessionConfiguration() != null) {
      return tenant.sessionConfiguration().timeoutSeconds();
    }
    return DEFAULT_SESSION_MAX_AGE_SECONDS;
  }

  /**
   * Gets the session switch policy from tenant configuration.
   *
   * @param tenant the tenant
   * @return session switch policy (default: SWITCH_ALLOWED)
   */
  private SessionSwitchPolicy getSessionSwitchPolicy(Tenant tenant) {
    if (tenant.sessionConfiguration() != null) {
      return SessionSwitchPolicy.of(tenant.sessionConfiguration().switchPolicy());
    }
    return SessionSwitchPolicy.SWITCH_ALLOWED;
  }
}
