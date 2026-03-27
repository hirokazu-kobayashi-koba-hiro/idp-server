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

package org.idp.server.core.openid.token.verifier;

import java.util.Map;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.core.openid.token.exception.TokenBadRequestException;
import org.idp.server.core.openid.token.service.SubjectTokenVerificationResult;
import org.idp.server.platform.log.LoggerWrapper;

/**
 * TokenExchangeDelegationVerifier
 *
 * <p>Verifies delegation authorization for Token Exchange (RFC 8693 Section 4.4). Checks that the
 * actor identified by the actor_token is authorized by the may_act claim in the subject_token.
 *
 * <p>RFC 8693 Section 4.4:
 *
 * <blockquote>
 *
 * The "may_act" claim makes a statement that one party is authorized to become the actor and act on
 * behalf of another party. The claim value is a JSON object, and members in the JSON object are
 * claims that identify the party that is asserted as being eligible to act for the party identified
 * by the JWT containing the claim.
 *
 * </blockquote>
 *
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc8693#section-4.4">RFC 8693 Section 4.4</a>
 */
public class TokenExchangeDelegationVerifier {

  private static final LoggerWrapper log =
      LoggerWrapper.getLogger(TokenExchangeDelegationVerifier.class);

  SubjectTokenVerificationResult subjectResult;
  OAuthToken actorOAuthToken;
  AuthorizationServerConfiguration serverConfiguration;

  public TokenExchangeDelegationVerifier(
      SubjectTokenVerificationResult subjectResult,
      OAuthToken actorOAuthToken,
      AuthorizationServerConfiguration serverConfiguration) {
    this.subjectResult = subjectResult;
    this.actorOAuthToken = actorOAuthToken;
    this.serverConfiguration = serverConfiguration;
  }

  /**
   * Verifies that the actor is authorized by the may_act claim. If may_act is absent, the check is
   * skipped (may_act is OPTIONAL per RFC 8693).
   */
  @SuppressWarnings("unchecked")
  public void verify() {
    Map<String, Object> claims = subjectResult.claims();
    Object mayActObj = claims.get("may_act");
    if (mayActObj == null) {
      return;
    }

    // Type safety guaranteed by TokenExchangeGrantVerifier.verifyMayActClaimType()
    Map<String, Object> mayAct = (Map<String, Object>) mayActObj;

    verifySub(mayAct);
    verifyIss(mayAct);
  }

  private void verifySub(Map<String, Object> mayAct) {
    Object allowedSubObj = mayAct.get("sub");
    if (allowedSubObj == null) {
      return;
    }

    String allowedSub = allowedSubObj.toString();
    String actorSub = actorOAuthToken.subject().value();

    if (!allowedSub.equals(actorSub)) {
      log.warn("may_act check failed: allowed_sub={}, actor_sub={}", allowedSub, actorSub);
      throw new TokenBadRequestException(
          "invalid_grant", "Actor is not authorized by may_act claim: actor sub does not match");
    }
  }

  private void verifyIss(Map<String, Object> mayAct) {
    Object allowedIssObj = mayAct.get("iss");
    if (allowedIssObj == null) {
      return;
    }

    String allowedIss = allowedIssObj.toString();
    String serverIssuer = serverConfiguration.tokenIssuer().value();

    if (!allowedIss.equals(serverIssuer)) {
      log.warn(
          "may_act issuer check failed: allowed_iss={}, server_iss={}", allowedIss, serverIssuer);
      throw new TokenBadRequestException(
          "invalid_grant", "Actor is not authorized by may_act claim: issuer does not match");
    }
  }
}
