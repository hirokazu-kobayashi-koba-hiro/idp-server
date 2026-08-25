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
 * nothing to select between. All-or-nothing for these claims is expressed by {@code denied_scopes}
 * removing the {@code claims:*} scope that releases them — the custom claims creators read the
 * grant's scopes, so {@code denied_claims} does not stop them.
 *
 * <p>A selection is never pruned, not even for a claim that ends up unreleased. {@link #narrow}
 * only ever removes elements, so a selection the creators never consult is inert; dropping one, on
 * the other hand, removes the narrowing and releases every element of a claim the scope still
 * carries.
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

  /** Reads the {@code granted_claim_values} object of an authorize request body. */
  public static GrantedClaimValues fromObject(Object value) {
    if (!(value instanceof Map<?, ?> map)) {
      return new GrantedClaimValues();
    }
    Map<String, List<Object>> parsed = new LinkedHashMap<>();
    for (Map.Entry<?, ?> entry : map.entrySet()) {
      if (entry.getValue() instanceof List<?> list) {
        parsed.put(String.valueOf(entry.getKey()), normalize(list));
      }
    }
    return new GrantedClaimValues(parsed);
  }

  /**
   * Re-reads a submitted element through the converter the stored side came through.
   *
   * <p>{@link #narrow} matches an element by equality, and the two sides reach it by different
   * routes: the selection arrives on the wire (parsed by the web layer) on the request that
   * authorizes, and from the sentinel on every request after it, while the properties it is matched
   * against always come from the repository through {@link JsonConverter}. Two mappings that
   * disagree on a number — {@code Integer} against {@code Long}, say — would make an object element
   * unequal to the one the user holds, and the element the End-User picked would silently drop out
   * of the claim. Normalizing here puts both sides on one mapping instead of relying on the web
   * layer happening to agree with it.
   */
  private static List<Object> normalize(List<?> elements) {
    if (elements.isEmpty()) {
      return List.of();
    }
    return jsonConverter.read(jsonConverter.write(elements), List.class);
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
