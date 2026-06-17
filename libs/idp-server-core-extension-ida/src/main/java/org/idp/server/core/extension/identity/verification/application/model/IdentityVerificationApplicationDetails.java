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

package org.idp.server.core.extension.identity.verification.application.model;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.core.extension.identity.verification.IdentityVerificationContext;
import org.idp.server.platform.json.JsonNodeWrapper;
import org.idp.server.platform.json.path.JsonPathWrapper;
import org.idp.server.platform.mapper.MappingRule;
import org.idp.server.platform.mapper.MappingRuleObjectMapper;

public class IdentityVerificationApplicationDetails {

  JsonNodeWrapper json;

  public IdentityVerificationApplicationDetails() {
    this.json = JsonNodeWrapper.empty();
  }

  public IdentityVerificationApplicationDetails(JsonNodeWrapper json) {
    this.json = json;
  }

  public static IdentityVerificationApplicationDetails fromJson(String json) {
    return new IdentityVerificationApplicationDetails(JsonNodeWrapper.fromString(json));
  }

  public static IdentityVerificationApplicationDetails create(
      IdentityVerificationContext applicationContext, List<MappingRule> mappingRules) {

    JsonNodeWrapper jsonNodeWrapper = JsonNodeWrapper.fromMap(applicationContext.toMap());
    JsonPathWrapper jsonPathWrapper = new JsonPathWrapper(jsonNodeWrapper.toJson());
    Map<String, Object> mappingResult =
        MappingRuleObjectMapper.execute(mappingRules, jsonPathWrapper);

    return new IdentityVerificationApplicationDetails(JsonNodeWrapper.fromMap(mappingResult));
  }

  public IdentityVerificationApplicationDetails merge(
      IdentityVerificationContext applicationContext, List<MappingRule> mappingRules) {
    return merge(applicationContext, mappingRules, false);
  }

  /**
   * Merges this process's mapping result into the current {@code application_details}.
   *
   * <p>With {@code deepMerge=false} (default policy {@code "merge"}) this is a top-level {@code
   * putAll}: a colliding parent key is replaced wholesale, so two processes writing different
   * subkeys under the same parent lose each other's data. With {@code deepMerge=true} (policy
   * {@code "deep_merge"}) nested {@link Map} values are merged recursively (scalars and arrays
   * still overwrite, null source values are skipped), so sibling subkeys under a shared parent are
   * preserved — e.g. accumulating {@code progress.<sub>} across processes. (#1637)
   */
  public IdentityVerificationApplicationDetails merge(
      IdentityVerificationContext applicationContext,
      List<MappingRule> mappingRules,
      boolean deepMerge) {
    JsonNodeWrapper jsonNodeWrapper = JsonNodeWrapper.fromMap(applicationContext.toMap());
    JsonPathWrapper jsonPathWrapper = new JsonPathWrapper(jsonNodeWrapper.toJson());
    Map<String, Object> mappingResult =
        MappingRuleObjectMapper.execute(mappingRules, jsonPathWrapper);
    Map<String, Object> mergedResult = new HashMap<>(json.toMap());
    if (deepMerge) {
      deepMerge(mergedResult, mappingResult);
    } else {
      mergedResult.putAll(mappingResult);
    }

    return new IdentityVerificationApplicationDetails(JsonNodeWrapper.fromMap(mergedResult));
  }

  /**
   * Recursively merges {@code source} into {@code target}: when both sides hold a {@link Map} for
   * the same key the children are merged recursively; otherwise (scalar, array, or type change) the
   * source value overwrites. A null source value is skipped so it never clobbers existing data — an
   * unmatched {@code from} JSONPath maps to null, and the intent of deep_merge is to accumulate.
   * Mirrors the null-skip semantics of {@code verified_claims} deep_merge. Package-private for
   * direct unit testing. (#1637)
   */
  @SuppressWarnings("unchecked")
  static void deepMerge(Map<String, Object> target, Map<String, Object> source) {
    source.forEach(
        (key, newValue) -> {
          if (newValue == null) {
            return;
          }
          Object existingValue = target.get(key);
          if (existingValue instanceof Map && newValue instanceof Map) {
            Map<String, Object> mergedChild = new HashMap<>((Map<String, Object>) existingValue);
            deepMerge(mergedChild, (Map<String, Object>) newValue);
            target.put(key, mergedChild);
          } else {
            target.put(key, newValue);
          }
        });
  }

  public String getValueOrEmptyAsString(String fieldName) {
    return json.getValueOrEmptyAsString(fieldName);
  }

  public Map<String, Object> toMap() {
    return json.toMap();
  }

  public String toJson() {
    return json.toJson();
  }
}
