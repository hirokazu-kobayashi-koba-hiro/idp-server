/*
 * Copyright 2026 Hirokazu Kobayashi
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
import org.idp.server.core.openid.authentication.AuthenticationInteractionResults;
import org.idp.server.core.openid.authentication.evaluator.MfaConditionEvaluator;
import org.idp.server.core.openid.authentication.policy.AuthenticationPolicy;
import org.idp.server.core.openid.oauth.request.AuthorizationRequest;

/**
 * OIDCSessionVerifier
 *
 * <p>Verifies OIDC sessions for various authorization scenarios. This class encapsulates all
 * session verification logic, keeping OIDCSessionHandler focused on orchestration.
 *
 * <p>Verification includes:
 *
 * <ul>
 *   <li>Session existence and expiration
 *   <li>max_age constraint from authorization request
 *   <li>acr_values constraint - prevents ACR downgrade attacks
 *   <li>Authentication policy successConditions - prevents policy bypass
 * </ul>
 */
public class OIDCSessionVerifier {

  private boolean isSessionValid(OPSession opSession, Long maxAge) {
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
   * Verifies session for authorize-with-session flow.
   *
   * <p>Performs comprehensive verification including:
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
   * @return verification result with error details if invalid
   */
  public SessionValidationResult verifyForAuthorization(
      OPSession opSession,
      AuthorizationRequest authorizationRequest,
      AuthenticationPolicy authenticationPolicy) {

    // 1. Session existence check
    if (opSession == null || !opSession.exists()) {
      return SessionValidationResult.sessionNotFound();
    }

    // 2. Session expiration and max_age check
    Long maxAge =
        authorizationRequest.maxAge().exists() ? authorizationRequest.maxAge().toLongValue() : null;
    if (!isSessionValid(opSession, maxAge)) {
      return SessionValidationResult.sessionExpired();
    }

    // 3. ACR values check - prevent ACR downgrade attacks
    if (authorizationRequest.hasAcrValues()) {
      String sessionAcr = opSession.acr();
      if (sessionAcr == null
          || sessionAcr.isEmpty()
          || !authorizationRequest.acrValues().contains(sessionAcr)) {
        return SessionValidationResult.acrMismatch();
      }
    }

    // 4. Authentication policy check - prevent authentication policy bypass
    if (authenticationPolicy != null && authenticationPolicy.hasSuccessConditions()) {
      AuthenticationInteractionResults sessionResults =
          opSession.toAuthenticationInteractionResults();
      if (!MfaConditionEvaluator.isSuccessSatisfied(
          authenticationPolicy.successConditions(), sessionResults)) {
        return SessionValidationResult.policyMismatch();
      }
    }

    return SessionValidationResult.success();
  }
}
