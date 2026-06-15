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
import org.idp.server.core.openid.oauth.type.oauth.Scopes;
import org.idp.server.platform.json.JsonNodeWrapper;

/**
 * Shared logic for scope-based selective {@code verified_claims} output ({@code verified_claims:*}
 * scope prefix), used by both the access token and UserInfo creators.
 *
 * <p>Builds the OIDC4IDA {@code verified_claims} structure ({@code verification} + {@code claims}),
 * selecting only the claims requested via {@code verified_claims:<name>} scopes that the user
 * actually has. If no requested claim matches a user claim, nothing is emitted — this avoids
 * leaking {@code verification} metadata (e.g. {@code trust_framework}, {@code evidence}) with an
 * empty {@code claims} object.
 */
final class SelectiveVerifiedClaims {

  static final String PREFIX = "verified_claims:";

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
    for (String scope : scopes.filterMatchedPrefix(PREFIX)) {
      if (userClaims.containsKey(scope.substring(PREFIX.length()))) {
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
    for (String scope : scopes.filterMatchedPrefix(PREFIX)) {
      String claimName = scope.substring(PREFIX.length());
      if (userClaims.containsKey(claimName)) {
        selectedClaims.put(claimName, userClaims.get(claimName));
      }
    }

    // No requested claim matched: emit nothing rather than leaking verification with empty claims.
    if (selectedClaims.isEmpty()) {
      return new HashMap<>();
    }

    Map<String, Object> verification =
        userVerifiedClaims.contains("verification")
            ? userVerifiedClaims.getValueAsJsonNode("verification").toMap()
            : new HashMap<>();

    Map<String, Object> verifiedClaimsStructure = new HashMap<>();
    verifiedClaimsStructure.put("verification", verification);
    verifiedClaimsStructure.put("claims", selectedClaims);

    Map<String, Object> result = new HashMap<>();
    result.put("verified_claims", verifiedClaimsStructure);
    return result;
  }
}
