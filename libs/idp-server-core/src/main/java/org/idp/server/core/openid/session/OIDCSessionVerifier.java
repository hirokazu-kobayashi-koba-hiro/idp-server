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
import java.util.List;
import java.util.Map;
import org.idp.server.core.openid.authentication.AuthenticationInteractionRequestResult;
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
   *   <li>prompt=login - forces re-authentication
   *   <li>max_age constraint from authorization request
   *   <li>acr_values constraint - prevents ACR downgrade attacks
   *   <li>Authentication policy successConditions - prevents policy bypass
   * </ul>
   *
   * @param opSession the OP session (may be null)
   * @param authorizationRequest the authorization request
   * @param authenticationPolicy the authentication policy for the client
   * @param request {@code $.request.*} for the policy conditions, from this authorization request
   *     (Issue #1907)
   * @param rechecked the policy's verification steps, checked again for this authorization request
   *     (Issue #1907, {@code AuthenticationInteractors#recheckForSessionReuse})
   * @return verification result with error details if invalid
   */
  public SessionValidationResult verifyForAuthorization(
      OPSession opSession,
      AuthorizationRequest authorizationRequest,
      AuthenticationPolicy authenticationPolicy,
      Map<String, Object> request,
      List<AuthenticationInteractionRequestResult> rechecked) {

    // 1. Session existence check
    if (opSession == null || !opSession.exists()) {
      return SessionValidationResult.sessionNotFound();
    }

    // 2. prompt=login forces re-authentication (OIDC Core 3.1.2.1)
    //
    // The screen already asks the server whether the session may be reused
    // (OAuthViewDataCreator.isSessionEnabled) and only calls this endpoint when it may. Checking it
    // again here keeps the decision on the server: this endpoint completes an authorization without
    // authenticating, so it must not depend on the caller honouring that flag.
    if (authorizationRequest.isPromptLogin()) {
      return SessionValidationResult.promptLoginRequired();
    }

    // 3. Session expiration and max_age check
    Long maxAge =
        authorizationRequest.maxAge().exists() ? authorizationRequest.maxAge().toLongValue() : null;
    if (!isSessionValid(opSession, maxAge)) {
      return SessionValidationResult.sessionExpired();
    }

    // 4. ACR values check - prevent ACR downgrade attacks
    if (authorizationRequest.hasAcrValues()) {
      String sessionAcr = opSession.acr();
      if (sessionAcr == null
          || sessionAcr.isEmpty()
          || !authorizationRequest.acrValues().contains(sessionAcr)) {
        return SessionValidationResult.acrMismatch();
      }
    }

    // 5. Verification steps (Issue #1907) - checked again for this request. Their results from the
    // earlier sign-in are not carried over: they belong to the request they ran for.
    AuthenticationInteractionResults sessionResults =
        opSession.toAuthenticationInteractionResults().withoutVerifications();
    for (AuthenticationInteractionRequestResult result : rechecked) {
      if (!result.isSuccess()) {
        return stepNotSatisfied(result);
      }
      sessionResults = sessionResults.updatedWith(result);
    }

    // 6. Authentication policy check - prevent authentication policy bypass
    if (authenticationPolicy != null && authenticationPolicy.hasSuccessConditions()) {
      // Issue #1501: pass the session user so $.user.* conditions evaluate consistently with the
      // live authentication transaction (AuthenticationTransaction#isSuccess); otherwise session
      // reuse would evaluate user attributes against an empty user and could mismatch.
      // Issue #1907: $.request.* comes from this authorization request, not the session's, so a
      // condition on what the relying party asked for is checked again for each request.
      if (!MfaConditionEvaluator.isSuccessSatisfied(
          authenticationPolicy.successConditions(), sessionResults, opSession.user(), request)) {
        return SessionValidationResult.policyMismatch();
      }
    }

    return SessionValidationResult.success();
  }

  private static SessionValidationResult stepNotSatisfied(
      AuthenticationInteractionRequestResult result) {
    Map<String, Object> response = result.response();
    Object error = response.get("error");
    Object description = response.get("error_description");
    return SessionValidationResult.stepNotSatisfied(
        error instanceof String code ? code : "invalid_request",
        description instanceof String text
            ? text
            : "session does not satisfy authentication policy");
  }
}
