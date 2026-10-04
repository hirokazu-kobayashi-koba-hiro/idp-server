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

package org.idp.server.core.openid.oauth.type.oauth;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * The parameters of an authorization request that are not OAuth or OpenID Connect parameters.
 *
 * <p>Each key also carries where it came from ({@link CustomParamSource}), so a check that relies
 * on a value can refuse one the end-user could have changed. The sources are known only while the
 * request is being processed and are not stored with the authorization request; a request read back
 * from storage has none.
 */
public class CustomParams {
  Map<String, String> values;
  Map<String, CustomParamSource> sources;

  public CustomParams() {
    this(new HashMap<>());
  }

  public CustomParams(Map<String, String> values) {
    this.values = values;
    this.sources = new HashMap<>();
  }

  private CustomParams(Map<String, String> values, Map<String, CustomParamSource> sources) {
    this.values = values;
    this.sources = sources;
  }

  /**
   * @return {@code values}, every one of which came from {@code source}
   */
  public static CustomParams of(Map<String, String> values, CustomParamSource source) {
    Map<String, CustomParamSource> sources = new HashMap<>();
    values.keySet().forEach(key -> sources.put(key, source));
    return new CustomParams(values, sources);
  }

  public Map<String, String> values() {
    return values;
  }

  public boolean exists() {
    return Objects.nonNull(values) && !values.isEmpty();
  }

  public String getValueAsStringOrEmpty(String key) {
    return values.getOrDefault(key, "");
  }

  /**
   * @return where the value of {@code key} came from, or null when it is not known
   */
  public CustomParamSource sourceOf(String key) {
    return sources.get(key);
  }

  /**
   * @return whether the source of every key is known. It is not for a request read back from
   *     storage, such as a pushed authorization request at the authorization endpoint.
   */
  public boolean sourcesKnown() {
    return sources.keySet().containsAll(values.keySet());
  }

  /**
   * @return the values whose source is known not to be {@code source}, and those whose source is
   *     not known
   */
  public Map<String, String> valuesNotFrom(CustomParamSource source) {
    Map<String, String> result = new HashMap<>();
    values.forEach(
        (key, value) -> {
          if (sources.get(key) != source) {
            result.put(key, value);
          }
        });
    return result;
  }

  /**
   * These parameters with those of the request object laid over them. A key in both takes the
   * request object's value, as for the standard parameters (OpenID Connect Core 1.0 Section 6.3.3).
   */
  public CustomParams assembledWith(CustomParams requestObject) {
    Map<String, String> assembledValues = new HashMap<>(values);
    Map<String, CustomParamSource> assembledSources = new HashMap<>(sources);
    assembledValues.putAll(requestObject.values);
    assembledSources.putAll(requestObject.sources);
    return new CustomParams(assembledValues, assembledSources);
  }

  /**
   * @return these values, every one of which now counts as having come from {@code source}
   */
  public CustomParams allFrom(CustomParamSource source) {
    return of(new HashMap<>(values), source);
  }
}
