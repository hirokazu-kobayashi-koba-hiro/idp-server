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

package org.idp.server.core.openid.oauth.type.extension;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.platform.json.JsonConverter;

/**
 * The elements of an array claim the End-User allowed on the consent screen (#1816).
 *
 * <p>{@code denied_claims} can only drop a claim whole. A custom property holding several things
 * the user owns — accounts, cards, contracts — was therefore all-or-nothing: consenting to {@code
 * claims:accounts} released every account. This carries the subset the user picked.
 *
 * <h2>Narrowing only</h2>
 *
 * <p>{@link #narrow} intersects with what the user actually has, so a value that is not already in
 * the property cannot be introduced by the request. Consent can only take away. Without that, a
 * caller could put an arbitrary value into a token claim by naming it here.
 *
 * <p>An entry naming a claim that is absent, or whose value is not an array, is ignored: there is
 * nothing to select between, and {@code denied_claims} already expresses all-or-nothing for it.
 *
 * <p>Selecting none of the elements removes the property, which makes the claim absent rather than
 * empty — the same result as denying it whole, and consistent with omitting a claim that has no
 * value (OIDC Core §5.3.2, #1699).
 *
 * <h2>It is the decision, not the result</h2>
 *
 * <p>The selection is persisted with the grant and applied when claims are built, rather than
 * applied once to the user the grant holds. UserInfo loads the user from the repository at request
 * time rather than reading the grant's snapshot, so only a decision that travels with the grant
 * reaches every channel. It rides inside the existing claim-name TEXT column as a single sentinel
 * token {@code gcv:<base64url(JSON)>}, the same way the OIDC4IDA verified_claims request does
 * ({@link org.idp.server.core.openid.grant_management.grant.RequestedVerifiedClaims}, #1628), so no
 * schema change is needed. Claim emission matches fixed known claim names rather than enumerating
 * the token set, so code that predates this change ignores an unrecognized {@code gcv:} token —
 * keeping rolling deploys safe.
 */
public class GrantedClaimValues {

  static final String SENTINEL_PREFIX = "gcv:";
  private static final JsonConverter jsonConverter = JsonConverter.defaultInstance();

  Map<String, List<Object>> values;

  public GrantedClaimValues() {
    this.values = Map.of();
  }

  public GrantedClaimValues(Map<String, List<Object>> values) {
    this.values = values;
  }

  /** True when {@code token} is the selection sentinel rather than a plain claim name. */
  public static boolean isSentinel(String token) {
    return token != null && token.startsWith(SENTINEL_PREFIX);
  }

  /** Restores a selection from its serialized sentinel token. */
  public static GrantedClaimValues fromSentinel(String sentinelToken) {
    if (!isSentinel(sentinelToken)) {
      return new GrantedClaimValues();
    }
    String base64 = sentinelToken.substring(SENTINEL_PREFIX.length());
    String json = new String(Base64.getUrlDecoder().decode(base64), StandardCharsets.UTF_8);
    // Parsed the same way as the request body, so an element read back from storage equals the
    // element parsed from the user's properties — both come through the same JSON mapping.
    return fromObject(jsonConverter.read(json, Map.class));
  }

  /** The serialized sentinel token, or an empty string when nothing was selected. */
  public String toSentinelToken() {
    if (!exists()) {
      return "";
    }
    String json = jsonConverter.write(values);
    return SENTINEL_PREFIX
        + Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(json.getBytes(StandardCharsets.UTF_8));
  }

  /**
   * A copy without the selections for claims the End-User denied whole, since a claim that is not
   * released has nothing left to select between.
   */
  public GrantedClaimValues removeClaims(DeniedClaims deniedClaims) {
    if (!exists() || deniedClaims == null || deniedClaims.isEmpty()) {
      return this;
    }
    Map<String, List<Object>> kept = new LinkedHashMap<>();
    values.forEach(
        (claimName, selected) -> {
          if (!deniedClaims.contains(claimName)) {
            kept.put(claimName, selected);
          }
        });
    return new GrantedClaimValues(kept);
  }

  /** Reads the {@code granted_claim_values} object of an authorize request body. */
  public static GrantedClaimValues fromObject(Object value) {
    if (!(value instanceof Map<?, ?> map)) {
      return new GrantedClaimValues();
    }
    Map<String, List<Object>> parsed = new LinkedHashMap<>();
    for (Map.Entry<?, ?> entry : map.entrySet()) {
      if (entry.getValue() instanceof List<?> list) {
        parsed.put(String.valueOf(entry.getKey()), List.copyOf(list));
      }
    }
    return new GrantedClaimValues(parsed);
  }

  public boolean exists() {
    return !values.isEmpty();
  }

  public Map<String, List<Object>> values() {
    return values;
  }

  /**
   * Applies the selection to the properties a claim would be built from.
   *
   * @return the properties with each named array reduced to the selected elements, and any array
   *     reduced to nothing removed entirely
   */
  public Map<String, Object> narrow(Map<String, Object> customProperties) {
    if (!exists() || customProperties == null || customProperties.isEmpty()) {
      return customProperties;
    }

    Map<String, Object> narrowed = new HashMap<>(customProperties);
    for (Map.Entry<String, List<Object>> selection : values.entrySet()) {
      Object current = narrowed.get(selection.getKey());
      if (!(current instanceof List<?> owned)) {
        continue;
      }
      List<Object> kept = new ArrayList<>();
      for (Object element : owned) {
        if (selection.getValue().contains(element)) {
          kept.add(element);
        }
      }
      if (kept.isEmpty()) {
        narrowed.remove(selection.getKey());
      } else {
        narrowed.put(selection.getKey(), kept);
      }
    }
    return narrowed;
  }
}
