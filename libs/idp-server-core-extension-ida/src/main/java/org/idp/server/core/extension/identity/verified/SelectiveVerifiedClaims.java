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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.core.openid.oauth.type.oauth.Scopes;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.log.LoggerWrapper;

/**
 * Shared logic for scope-based selective {@code verified_claims} output, used by both the access
 * token and UserInfo creators.
 *
 * <p>Builds the OIDC4IDA {@code verified_claims} structure ({@code verification} + {@code claims}):
 *
 * <ul>
 *   <li>{@code verified_claims:<name>} selects a verified claim (e.g. {@code
 *       verified_claims:given_name}).
 *   <li>{@code verified_claims:verification:<element>} selects an <em>optional</em> verification
 *       element such as {@code evidence}. Note {@code trust_framework} is the REQUIRED floor of the
 *       verification block (IDA schema §5.2): it is always included when present and never gated
 *       behind a scope, so {@code verification} is never emitted as an invalid empty object.
 * </ul>
 *
 * <p><b>Why the access token also uses the canonical IDA structure.</b> OIDC4IDA §4.7 only notes
 * that {@code verified_claims} "can be utilized" in an OAuth access token, without specifying its
 * structure. RFC 9068 §2.2.2 fills that gap: a registered claim SHOULD be encoded using its IANA
 * registered name and definition. {@code verified_claims} is IANA-registered (JWT claims registry →
 * OpenID Identity Assurance Schema Definition 1.0 §5), whose §5.2 / §5.4.2 mandate the nested
 * {@code {verification, claims}} object with a REQUIRED {@code verification.trust_framework} (an
 * empty {@code verification: {}} is invalid). So the access token, ID token and UserInfo all emit
 * the same canonical structure — the access token is not a place to invent a flat/ad-hoc shape.
 *
 * <p>Note the asymmetry: a verified claim lives under the {@code claims} block, so its scope would
 * canonically be {@code verified_claims:claims:<name>}. The redundant {@code claims:} segment is
 * omitted for brevity, leaving {@code verified_claims:<name>}. The {@code verification:} segment is
 * kept because it disambiguates the verification block from a verified claim of the same name. Any
 * scope under the {@code verified_claims:verification:} namespace is therefore treated as a
 * verification-element selector, never as a verified claim named {@code verification:...}.
 *
 * <p>Both selections look up names dynamically against the user's stored {@code verified_claims},
 * so the selectable names follow the tenant's verified_claims mapping configuration — no fixed
 * claim/element list is hard-coded. Sensitive verification data (e.g. {@code evidence}, which can
 * carry document numbers) is therefore returned only when explicitly requested via its scope.
 *
 * <p>{@code verified_claims} is omitted entirely (OIDC4IDA §5.7.4) when no requested claim matches
 * a user claim, or when the user has no {@code verification.trust_framework} (the verification
 * requirement cannot be met) — rather than leaking verification metadata without a verified claim,
 * or emitting an invalid empty {@code verification}.
 */
final class SelectiveVerifiedClaims {

  static final String PREFIX = "verified_claims:";
  static final String VERIFICATION_PREFIX = "verified_claims:verification:";

  private static final LoggerWrapper log = LoggerWrapper.getLogger(SelectiveVerifiedClaims.class);

  private SelectiveVerifiedClaims() {}

  /**
   * @return {@code true} if at least one requested {@code verified_claims:<name>} scope matches a
   *     claim the user has.
   */
  static boolean hasSelectableClaims(Scopes scopes, JsonNodeWrapper userVerifiedClaims) {
    if (!userVerifiedClaims.contains("claims")) {
      return false;
    }
    Map<String, Object> userClaims = userVerifiedClaims.getValueAsJsonNode("claims").toMap();
    for (String claimName : requestedClaimNames(scopes)) {
      if (userClaims.containsKey(claimName)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Builds {@code {"verified_claims": {"verification": {...}, "claims": {...}}}} for the requested
   * scopes. Returns an empty map when no requested claim matches, so the caller emits no {@code
   * verified_claims}.
   */
  static Map<String, Object> build(Scopes scopes, JsonNodeWrapper userVerifiedClaims) {
    if (!userVerifiedClaims.contains("claims")) {
      return new HashMap<>();
    }
    Map<String, Object> userClaims = userVerifiedClaims.getValueAsJsonNode("claims").toMap();

    Map<String, Object> selectedClaims = new HashMap<>();
    for (String claimName : requestedClaimNames(scopes)) {
      if (userClaims.containsKey(claimName)) {
        selectedClaims.put(claimName, userClaims.get(claimName));
      }
    }

    // OIDC4IDA §5.7.4: emit nothing when no requested claim matches, rather than leaking
    // verification metadata with an empty claims object.
    if (selectedClaims.isEmpty()) {
      return new HashMap<>();
    }

    Map<String, Object> verification = selectVerification(scopes, userVerifiedClaims);

    // IDA schema §5.2 / OIDC4IDA §5.7.4: verification.trust_framework is REQUIRED. Without it the
    // verification requirement cannot be met, so omit verified_claims entirely rather than emit a
    // schema-invalid verification block.
    if (!verification.containsKey("trust_framework")) {
      log.warn(
          "verified_claims omitted: stored verification has no required trust_framework"
              + " (check the tenant's verified_claims mapping configuration)");
      return new HashMap<>();
    }

    Map<String, Object> verifiedClaimsStructure = new HashMap<>();
    verifiedClaimsStructure.put("verification", verification);
    verifiedClaimsStructure.put("claims", selectedClaims);

    Map<String, Object> result = new HashMap<>();
    result.put("verified_claims", verifiedClaimsStructure);
    return result;
  }

  /**
   * Claim names requested via {@code verified_claims:<name>}. The name is taken as-is after the
   * {@code verified_claims:} prefix — the {@code claims:} segment is omitted for brevity, so {@code
   * verified_claims:given_name} (not {@code verified_claims:claims:given_name}) selects the {@code
   * given_name} verified claim. The {@code verified_claims:verification:*} namespace is excluded
   * here because it selects verification elements instead.
   */
  private static List<String> requestedClaimNames(Scopes scopes) {
    List<String> claimNames = new ArrayList<>();
    for (String scope : scopes.filterMatchedPrefix(PREFIX)) {
      if (scope.startsWith(VERIFICATION_PREFIX)) {
        continue;
      }
      claimNames.add(scope.substring(PREFIX.length()));
    }
    return claimNames;
  }

  /**
   * Builds the {@code verification} block: {@code trust_framework} is always included when present
   * (the REQUIRED floor of the verification object — non-PII and structurally inseparable from any
   * returned verified claim, so it is not subject to the §7 "only what is requested" rule), while
   * other elements requested via {@code verified_claims:verification:<element>} (e.g. {@code
   * evidence}, which can carry raw PII such as document numbers) are opt-in. Names are looked up
   * dynamically against the user's stored {@code verification} object.
   */
  private static Map<String, Object> selectVerification(
      Scopes scopes, JsonNodeWrapper userVerifiedClaims) {
    Map<String, Object> selected = new HashMap<>();
    if (!userVerifiedClaims.contains("verification")) {
      return selected;
    }
    Map<String, Object> userVerification =
        userVerifiedClaims.getValueAsJsonNode("verification").toMap();

    // trust_framework: always included (REQUIRED floor; never gated behind a scope).
    if (userVerification.containsKey("trust_framework")) {
      selected.put("trust_framework", userVerification.get("trust_framework"));
    }

    // Other verification elements: opt-in via their own scope (data minimization for PII).
    for (String scope : scopes.filterMatchedPrefix(VERIFICATION_PREFIX)) {
      String element = scope.substring(VERIFICATION_PREFIX.length());
      if (element.equals("trust_framework")) {
        continue;
      }
      if (userVerification.containsKey(element)) {
        selected.put(element, userVerification.get(element));
      }
    }
    return selected;
  }
}
