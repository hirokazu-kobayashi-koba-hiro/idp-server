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

package org.idp.server.core.extension.identity.verified;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.openid.authentication.Authentication;
import org.idp.server.core.openid.grant_management.grant.AuthorizationGrant;
import org.idp.server.core.openid.grant_management.grant.GrantIdTokenClaims;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.id_token.IdTokenCustomClaims;
import org.idp.server.core.openid.identity.id_token.RequestedClaimsPayload;
import org.idp.server.core.openid.identity.id_token.RequestedIdTokenClaims;
import org.idp.server.core.openid.identity.id_token.VerifiedClaimsObject;
import org.idp.server.core.openid.identity.id_token.plugin.CustomIndividualClaimsCreator;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfiguration;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.log.LoggerWrapper;

/**
 * Builds the OIDC4IDA {@code verified_claims} element returned in the ID Token, from the {@code
 * claims} request parameter (its {@code id_token.verified_claims} member).
 *
 * <p>The output follows the OpenID Connect for Identity Assurance 1.0 structure (§5.1): a {@code
 * verification} object — whose {@code trust_framework} is REQUIRED — plus a {@code claims} object
 * carrying the verified end-user claims.
 *
 * <h3>Returning less data than requested (§5.7)</h3>
 *
 * <p>Only data the user actually has is returned, and the element is omitted entirely when it
 * cannot be formed validly — never returned as a partial/empty shell such as {@code
 * {"verification": {}, "claims": {...}}} or {@code {"verification": {...}, "claims": {}}}:
 *
 * <ul>
 *   <li><b>§5.7.2 Unavailable data</b>: "If the OP does not have data about a certain claim, does
 *       not understand/support the respective claim, OPs shall omit the respective claim from any
 *       corresponding ID Token or UserInfo response." Each requested claim is included only when
 *       present on the user.
 *   <li><b>§5.7.4 Data not matching requirements</b>: "the OP shall omit the whole verified_claims
 *       element" / "the OP shall not return an error to the RP." When the verification requirement
 *       cannot be met (no {@code trust_framework}), the whole element is omitted rather than
 *       emitting a schema-invalid {@code verification}.
 *   <li><b>§5.7.5 Omitting elements</b>: "If an element is to be omitted according to the rules
 *       above, but is a requirement for a valid response, the OP shall omit its parent element as
 *       well." When no requested claim is available the {@code claims} object would be empty; since
 *       {@code claims} is required for a valid {@code verified_claims}, the whole element is
 *       omitted.
 * </ul>
 *
 * <p>The scope-based selective path (access token / UserInfo) is handled by {@link
 * SelectiveVerifiedClaims}, which applies the same §5.7 omission rules.
 *
 * @see <a href="https://openid.net/specs/openid-connect-4-identity-assurance-1_0.html">OpenID
 *     Connect for Identity Assurance 1.0</a>
 */
public class VerifiedClaimsCreator implements CustomIndividualClaimsCreator {

  private static final LoggerWrapper log = LoggerWrapper.getLogger(VerifiedClaimsCreator.class);

  @Override
  public boolean shouldCreate(
      User user,
      Authentication authentication,
      AuthorizationGrant authorizationGrant,
      IdTokenCustomClaims customClaims,
      RequestedClaimsPayload requestedClaimsPayload,
      AuthorizationServerConfiguration authorizationServerConfiguration,
      ClientConfiguration clientConfiguration) {

    GrantIdTokenClaims idTokenClaims = authorizationGrant.idTokenClaims();
    return idTokenClaims.hasVerifiedClaims() && user.hasVerifiedClaims();
  }

  @Override
  public Map<String, Object> create(
      User user,
      Authentication authentication,
      AuthorizationGrant authorizationGrant,
      IdTokenCustomClaims customClaims,
      RequestedClaimsPayload requestedClaimsPayload,
      AuthorizationServerConfiguration authorizationServerConfiguration,
      ClientConfiguration clientConfiguration) {

    RequestedIdTokenClaims requestedIdTokenClaims = requestedClaimsPayload.idToken();

    HashMap<String, Object> claims = new HashMap<>();

    VerifiedClaimsObject verifiedClaimsObject = requestedIdTokenClaims.verifiedClaims();
    JsonNodeWrapper userVerifiedClaims = user.verifiedClaimsNodeWrapper();
    Map<String, Object> verified = new HashMap<>();

    JsonNodeWrapper verificationNodeWrapper = verifiedClaimsObject.verificationNodeWrapper();
    Map<String, Object> verification = new HashMap<>();
    JsonNodeWrapper verificationClaim = userVerifiedClaims.getValueAsJsonNode("verification");
    if (verificationNodeWrapper.contains("trust_framework")
        && verificationClaim.contains("trust_framework")) {
      verification.put(
          "trust_framework", verificationClaim.getValueOrEmptyAsString("trust_framework"));
    }
    if (verificationNodeWrapper.contains("evidence") && verificationClaim.contains("evidence")) {
      verification.put("evidence", verificationClaim.getValueAsJsonNodeListAsMap("evidence"));
    }

    JsonNodeWrapper claimsNodeWrapper = verifiedClaimsObject.claimsNodeWrapper();
    JsonNodeWrapper userClaims = userVerifiedClaims.getValueAsJsonNode("claims");
    Map<String, Object> verifiedClaims = new HashMap<>();
    if (claimsNodeWrapper.contains("name") && userClaims.contains("name")) {
      verifiedClaims.put("name", userClaims.getValueOrEmptyAsString("name"));
    }
    if (claimsNodeWrapper.contains("given_name") && userClaims.contains("given_name")) {
      verifiedClaims.put("given_name", userClaims.getValueOrEmptyAsString("given_name"));
    }
    if (claimsNodeWrapper.contains("family_name") && userClaims.contains("family_name")) {
      verifiedClaims.put("family_name", userClaims.getValueOrEmptyAsString("family_name"));
    }
    if (claimsNodeWrapper.contains("middle_name") && userClaims.contains("middle_name")) {
      verifiedClaims.put("middle_name", userClaims.getValueOrEmptyAsString("middle_name"));
    }
    if (claimsNodeWrapper.contains("gender") && userClaims.contains("gender")) {
      verifiedClaims.put("gender", userClaims.getValueOrEmptyAsString("gender"));
    }
    if (claimsNodeWrapper.contains("birthdate") && userClaims.contains("birthdate")) {
      verifiedClaims.put("birthdate", userClaims.getValueOrEmptyAsString("birthdate"));
    }
    if (claimsNodeWrapper.contains("locale") && userClaims.contains("locale")) {
      verifiedClaims.put("locale", userClaims.getValueOrEmptyAsString("locale"));
    }
    if (claimsNodeWrapper.contains("address") && userClaims.contains("address")) {
      verifiedClaims.put("address", userClaims.getValueAsJsonNode("address").toMap());
    }
    if (claimsNodeWrapper.contains("phone_number") && userClaims.contains("phone_number")) {
      verifiedClaims.put("phone_number", userClaims.getValueOrEmptyAsString("phone_number"));
    }
    if (claimsNodeWrapper.contains("phone_number_verified")
        && userClaims.contains("phone_number_verified")) {
      verifiedClaims.put(
          "phone_number_verified", userClaims.getValueAsBoolean("phone_number_verified"));
    }
    if (claimsNodeWrapper.contains("email") && userClaims.contains("email")) {
      verifiedClaims.put("email", userClaims.getValueOrEmptyAsString("email"));
    }
    if (claimsNodeWrapper.contains("email_verified") && userClaims.contains("email_verified")) {
      verifiedClaims.put("email_verified", userVerifiedClaims.getValueAsBoolean("email_verified"));
    }
    // OIDC4IDA §5.7.4 / §5.7.5: when no requested claim matched a user claim, omit verified_claims
    // entirely rather than emit an empty claims object (and leak verification metadata).
    if (verifiedClaims.isEmpty()) {
      return claims;
    }

    // IDA schema §5.1 / OIDC4IDA §5.7.4: verification.trust_framework is REQUIRED. Without it the
    // verification requirement cannot be met, so omit verified_claims entirely rather than emit a
    // schema-invalid verification block (e.g. an empty {} or one carrying only evidence).
    if (!verification.containsKey("trust_framework")) {
      log.warn(
          "verified_claims omitted: stored verification has no required trust_framework"
              + " (check the tenant's verified_claims mapping configuration)");
      return claims;
    }

    verified.put("verification", verification);
    verified.put("claims", verifiedClaims);
    claims.put("verified_claims", verified);

    return claims;
  }
}
