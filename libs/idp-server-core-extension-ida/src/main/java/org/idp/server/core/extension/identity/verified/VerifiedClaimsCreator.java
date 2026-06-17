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
 *       element" / "the OP shall not return an error to the RP." A requested {@code value} / {@code
 *       values} constraint (OpenID Connect §5.5.1) is enforced: a mismatch on a {@code
 *       verification} element (or a missing required {@code trust_framework}) omits the whole
 *       {@code verified_claims}; a mismatch on a {@code claims} element omits just that claim.
 *   <li><b>§5.7.5 Omitting elements</b>: "If an element is to be omitted according to the rules
 *       above, but is a requirement for a valid response, the OP shall omit its parent element as
 *       well." When no requested claim is available the {@code claims} object would be empty; since
 *       {@code claims} is required for a valid {@code verified_claims}, the whole element is
 *       omitted.
 * </ul>
 *
 * <p>{@code trust_framework} is the REQUIRED floor of {@code verification}: it is returned whenever
 * the user holds it, never gated behind an explicit request. Verified claims are resolved
 * dynamically against the user's stored claims (no fixed claim list); other {@code verification}
 * elements (e.g. {@code evidence}, which can carry PII) are opt-in via explicit request.
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
    VerifiedClaimsObject requested = requestedIdTokenClaims.verifiedClaims();
    JsonNodeWrapper userVerifiedClaims = user.verifiedClaimsNodeWrapper();

    HashMap<String, Object> result = new HashMap<>();

    // claims: resolve the requested claim names dynamically against the user's stored claims,
    // honoring any value/values constraint. A claim that is unavailable (§5.7.2) or fails its
    // constraint (§5.7.4, claims branch) is dropped individually.
    Map<String, Object> claims =
        selectClaims(requested.claimsNodeWrapper(), userBlock(userVerifiedClaims, "claims"));

    // OIDC4IDA §5.7.4 / §5.7.5: when no requested claim is available, omit the whole
    // verified_claims rather than emit an empty claims object (and leak verification metadata).
    if (claims.isEmpty()) {
      return result;
    }

    Map<String, Object> verification =
        selectVerification(
            requested.verificationNodeWrapper(), userBlock(userVerifiedClaims, "verification"));

    // §5.7.4 (verification branch): a requested verification element failed its value/values
    // constraint, so the verification requirement is unmet — omit the whole verified_claims.
    if (verification == null) {
      log.warn(
          "verified_claims omitted: a requested verification element did not match its"
              + " value/values constraint");
      return result;
    }

    // IDA schema §5.1 / §5.7.4: verification.trust_framework is REQUIRED. Without it the
    // verification requirement cannot be met, so omit verified_claims entirely rather than emit a
    // schema-invalid verification block.
    if (!verification.containsKey("trust_framework")) {
      log.warn(
          "verified_claims omitted: stored verification has no required trust_framework"
              + " (check the tenant's verified_claims mapping configuration)");
      return result;
    }

    Map<String, Object> verified = new HashMap<>();
    verified.put("verification", verification);
    verified.put("claims", claims);
    result.put("verified_claims", verified);

    return result;
  }

  /** Returns the user's stored {@code verification} / {@code claims} block as a Java map. */
  private Map<String, Object> userBlock(JsonNodeWrapper userVerifiedClaims, String key) {
    if (!userVerifiedClaims.contains(key)) {
      return Map.of();
    }
    return userVerifiedClaims.getValueAsJsonNode(key).toMap();
  }

  /**
   * Selects each requested claim that the user holds and whose stored value satisfies the requested
   * {@code value} / {@code values} constraint. Unavailable or non-matching claims are dropped
   * individually (§5.7.2 / §5.7.4 claims branch).
   */
  private Map<String, Object> selectClaims(
      JsonNodeWrapper requestedClaims, Map<String, Object> userClaims) {
    Map<String, Object> selected = new HashMap<>();
    for (String name : requestedClaims.fieldNamesAsList()) {
      if (!userClaims.containsKey(name)) {
        continue;
      }
      Object userValue = userClaims.get(name);
      if (matchesConstraint(requestedClaims.getValueAsJsonNode(name), userValue)) {
        selected.put(name, userValue);
      }
    }
    return selected;
  }

  /**
   * Builds the {@code verification} block. {@code trust_framework} is the REQUIRED floor: always
   * included when the user holds it (never gated behind an explicit request), mirroring {@link
   * SelectiveVerifiedClaims}. Other elements (e.g. {@code evidence}) are opt-in via explicit
   * request. Returns {@code null} when a requested verification element fails its {@code value} /
   * {@code values} constraint, signaling the whole verified_claims must be omitted (§5.7.4).
   */
  private Map<String, Object> selectVerification(
      JsonNodeWrapper requestedVerification, Map<String, Object> userVerification) {
    Map<String, Object> selected = new HashMap<>();

    if (userVerification.containsKey("trust_framework")) {
      Object userValue = userVerification.get("trust_framework");
      if (!matchesConstraint(
          requestedVerification.getValueAsJsonNode("trust_framework"), userValue)) {
        return null;
      }
      selected.put("trust_framework", userValue);
    }

    for (String element : requestedVerification.fieldNamesAsList()) {
      if (element.equals("trust_framework") || !userVerification.containsKey(element)) {
        continue;
      }
      Object userValue = userVerification.get(element);
      if (!matchesConstraint(requestedVerification.getValueAsJsonNode(element), userValue)) {
        return null;
      }
      selected.put(element, userValue);
    }
    return selected;
  }

  /**
   * Matches a stored value against a requested claim's {@code value} / {@code values} constraint
   * (OpenID Connect §5.5.1). A {@code null} request (no constraint object) or one carrying neither
   * {@code value} nor {@code values} always matches — the claim was requested, not constrained.
   * {@code max_age} is out of scope.
   */
  private boolean matchesConstraint(JsonNodeWrapper constraint, Object userValue) {
    if (constraint == null || !constraint.exists() || !constraint.isObject()) {
      return true;
    }
    if (constraint.contains("value")) {
      return stringEquals(constraint.getValueOrEmptyAsString("value"), userValue);
    }
    if (constraint.contains("values")) {
      for (JsonNodeWrapper candidate : constraint.getValueAsJsonNodeList("values")) {
        if (stringEquals(candidate.asText(), userValue)) {
          return true;
        }
      }
      return false;
    }
    return true;
  }

  private boolean stringEquals(String requested, Object userValue) {
    return userValue != null && requested.equals(String.valueOf(userValue));
  }
}
