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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
 */
public class GrantedClaimValues {

  Map<String, List<Object>> values;

  public GrantedClaimValues() {
    this.values = Map.of();
  }

  public GrantedClaimValues(Map<String, List<Object>> values) {
    this.values = values;
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
