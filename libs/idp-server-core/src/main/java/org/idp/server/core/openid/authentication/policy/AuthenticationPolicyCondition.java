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

package org.idp.server.core.openid.authentication.policy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.core.openid.oauth.type.oauth.RequestedClientId;
import org.idp.server.core.openid.oauth.type.oauth.Scopes;
import org.idp.server.core.openid.oauth.type.oidc.AcrValues;
import org.idp.server.platform.json.JsonReadable;

/**
 * Represents matching conditions for an authentication policy.
 *
 * <p>This class defines a set of conditions—client IDs, ACR values, and scopes—that determine
 * whether a particular authentication request matches a given policy.
 *
 * <h3>Matching rules</h3>
 *
 * <ul>
 *   <li>If a condition list (e.g., {@code clientIds}, {@code acrValues}, {@code scopes}) is
 *       <strong>empty</strong>, that condition is ignored (treated as always matched).
 *   <li>If a condition list is <strong>non-empty</strong>, the incoming request must contain
 *       <strong>at least one overlapping value</strong> for that condition.
 *   <li>All specified conditions are combined with logical AND — all must match for the policy to
 *       apply.
 * </ul>
 *
 * <p>Example:
 *
 * <pre>{@code
 * AuthenticationPolicyCondition condition =
 *     new AuthenticationPolicyCondition(
 *         List.of("my-client"),          // clientIds
 *         List.of("urn:mfa"),            // acrValues
 *         List.of("openid", "profile")); // scopes
 *
 * boolean matches = condition.allMatch(
 *     new RequestedClientId("my-client"),
 *     new AcrValues("urn:mfa"),
 *     new Scopes("openid email"));
 *
 * // Result: true (clientId and acrValue match, and at least one scope overlaps)
 * }</pre>
 */
public class AuthenticationPolicyCondition implements JsonReadable {
  List<String> clientIds = new ArrayList<>();
  List<String> acrValues = new ArrayList<>();
  List<String> scopes = new ArrayList<>();

  public AuthenticationPolicyCondition() {}

  public AuthenticationPolicyCondition(
      List<String> clientIds, List<String> acrValues, List<String> scopes) {
    this.clientIds = clientIds;
    this.acrValues = acrValues;
    this.scopes = scopes;
  }

  /**
   * Determines whether all configured conditions match the given request context.
   *
   * @param requestedClientId client ID from the authentication request
   * @param acrValues ACR (Authentication Context Class Reference) values requested
   * @param scopes OAuth/OIDC scopes requested
   * @return {@code true} if all applicable conditions match; {@code false} otherwise
   */
  public boolean allMatch(RequestedClientId requestedClientId, AcrValues acrValues, Scopes scopes) {
    // 1. Client ID must match if condition specifies any
    if (!this.clientIds.isEmpty() && !this.clientIds.contains(requestedClientId.value())) {
      return false;
    }

    // 2. ACR values must have at least one overlap if condition specifies any
    if (!this.acrValues.isEmpty()) {
      if (acrValues == null || !acrValues.exists()) {
        return false;
      }
      boolean anyAcrMatch = this.acrValues.stream().anyMatch(acrValues::contains);
      if (!anyAcrMatch) {
        return false;
      }
    }

    // 3. Scopes must have at least one overlap if condition specifies any
    if (!this.scopes.isEmpty()) {
      if (scopes == null || !scopes.exists()) {
        return false;
      }
      boolean anyScopeMatch = this.scopes.stream().anyMatch(scopes::contains);
      if (!anyScopeMatch) {
        return false;
      }
    }

    // All specified conditions matched
    return true;
  }

  /**
   * @return list of client IDs required by this condition (may be empty)
   */
  public List<String> clientIds() {
    return clientIds;
  }

  /**
   * @return list of ACR values required by this condition (may be empty)
   */
  public List<String> acrValues() {
    return acrValues;
  }

  /**
   * @return list of scopes required by this condition (may be empty)
   */
  public List<String> scopes() {
    return scopes;
  }

  /** Converts the condition to a serializable map representation. */
  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    map.put("client_ids", clientIds);
    map.put("acr_values", acrValues);
    map.put("scopes", scopes);
    return map;
  }
}
