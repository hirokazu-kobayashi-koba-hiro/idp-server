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

/**
 * Shared logic for scope-based selective {@code verified_claims} output, used by both the access
 * token and UserInfo creators.
 *
 * <p>Builds the OIDC4IDA {@code verified_claims} structure ({@code verification} + {@code claims}).
 * Both blocks are selected per scope, mirroring the {@code verified_claims} structure:
 *
 * <ul>
 *   <li>{@code verified_claims:<name>} selects a verified claim (e.g. {@code
 *       verified_claims:given_name}).
 *   <li>{@code verified_claims:verification:<element>} selects a verification element (e.g. {@code
 *       verified_claims:verification:trust_framework}, {@code
 *       verified_claims:verification:evidence}).
 * </ul>
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
 * <p>If no requested claim matches a user claim, nothing is emitted. This follows OIDC4IDA §5.7.4
 * (omit {@code verified_claims} when no verified claim can be returned) and avoids leaking {@code
 * verification} metadata without any actual verified claim.
 */
final class SelectiveVerifiedClaims {

  static final String PREFIX = "verified_claims:";
  static final String VERIFICATION_PREFIX = "verified_claims:verification:";

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

    Map<String, Object> verifiedClaimsStructure = new HashMap<>();
    verifiedClaimsStructure.put("verification", selectVerification(scopes, userVerifiedClaims));
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
   * Verification elements requested via {@code verified_claims:verification:<element>}, looked up
   * dynamically against the user's stored {@code verification} object. Returns an empty map when no
   * verification element is requested or present.
   */
  private static Map<String, Object> selectVerification(
      Scopes scopes, JsonNodeWrapper userVerifiedClaims) {
    Map<String, Object> selected = new HashMap<>();
    if (!userVerifiedClaims.contains("verification")) {
      return selected;
    }
    Map<String, Object> userVerification =
        userVerifiedClaims.getValueAsJsonNode("verification").toMap();
    for (String scope : scopes.filterMatchedPrefix(VERIFICATION_PREFIX)) {
      String element = scope.substring(VERIFICATION_PREFIX.length());
      if (userVerification.containsKey(element)) {
        selected.put(element, userVerification.get(element));
      }
    }
    return selected;
  }
}
