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

package org.idp.server.core.openid.oauth.verifier.extension;

import java.util.List;
import org.idp.server.core.openid.identity.id_token.ClaimsObject;
import org.idp.server.core.openid.identity.id_token.RequestedClaimsPayload;
import org.idp.server.core.openid.identity.id_token.VerifiedClaimsObject;
import org.idp.server.core.openid.oauth.OAuthRequestContext;
import org.idp.server.core.openid.oauth.exception.OAuthRedirectableBadRequestException;
import org.idp.server.core.openid.oauth.verifier.AuthorizationRequestExtensionVerifier;
import org.idp.server.platform.json.JsonNodeWrapper;

/**
 * Verifies the {@code purpose} member of every individual claim request in the {@code claims}
 * parameter: a purpose MUST be between 3 and 300 characters, otherwise the authorization request is
 * rejected with {@code invalid_request}.
 *
 * <p>Source: {@code purpose} is defined in OIDC4IDA Implementer's Draft §5.1; it was dropped in the
 * OIDC4IDA 1.0 final spec but is still exercised by the eKYC OP conformance suite (tag IA-9), which
 * is the driver for this check. It is NOT defined in OpenID Connect Core §5.5.1 (essential / value
 * / values only).
 *
 * <p>{@code purpose} is a generic claims-request member, so this covers <b>all</b> requested claims
 * — the standard {@code id_token} / {@code userinfo} claims as well as the claims nested inside a
 * {@code verified_claims} request ({@code verification.*} and {@code claims.*}), not just verified
 * claims.
 *
 * <p>Runs after the base profile verifier has validated {@code redirect_uri}, so the error is
 * returned to the RP via redirect ({@link OAuthRedirectableBadRequestException}).
 */
public class RequestedClaimsPurposeVerifier implements AuthorizationRequestExtensionVerifier {

  static final int MIN_LENGTH = 3;
  static final int MAX_LENGTH = 300;

  @Override
  public boolean shouldVerify(OAuthRequestContext context) {
    return context.authorizationRequest().hasClaimsPayload();
  }

  @Override
  public void verify(OAuthRequestContext context) {
    RequestedClaimsPayload payload = context.authorizationRequest().requestedClaimsPayload();

    verifyStandardClaims(payload.idToken().claimsObjects(), context);
    verifyStandardClaims(payload.userinfo().claimsObjects(), context);

    verifyVerifiedClaims(payload.idToken().verifiedClaims(), context);
    verifyVerifiedClaims(payload.userinfo().verifiedClaims(), context);
  }

  private void verifyStandardClaims(List<ClaimsObject> claims, OAuthRequestContext context) {
    for (ClaimsObject claim : claims) {
      if (claim.hasPurpose()) {
        verifyLength(claim.purpose(), context);
      }
    }
  }

  private void verifyVerifiedClaims(
      VerifiedClaimsObject verifiedClaims, OAuthRequestContext context) {
    if (verifiedClaims == null) {
      return;
    }
    verifyNestedPurposes(verifiedClaims.claimsNodeWrapper(), context);
    verifyNestedPurposes(verifiedClaims.verificationNodeWrapper(), context);
  }

  /** Checks each member object of a verified_claims sub-tree for a {@code purpose}. */
  private void verifyNestedPurposes(JsonNodeWrapper node, OAuthRequestContext context) {
    if (node == null || !node.exists() || !node.isObject()) {
      return;
    }
    for (String field : node.fieldNamesAsList()) {
      JsonNodeWrapper member = node.getValueAsJsonNode(field);
      if (member != null && member.isObject() && member.contains("purpose")) {
        verifyLength(member.getValueOrEmptyAsString("purpose"), context);
      }
    }
  }

  private void verifyLength(String purpose, OAuthRequestContext context) {
    if (purpose == null || purpose.isEmpty()) {
      return;
    }
    int length = purpose.length();
    if (length < MIN_LENGTH || length > MAX_LENGTH) {
      throw new OAuthRedirectableBadRequestException(
          "invalid_request",
          String.format(
              "claims purpose must be between %d and %d characters (was %d)",
              MIN_LENGTH, MAX_LENGTH, length),
          context);
    }
  }
}
