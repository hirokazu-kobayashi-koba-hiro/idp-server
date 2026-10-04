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

package org.idp.server.core.openid.authentication;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.openid.oauth.type.oauth.CustomParamSource;
import org.idp.server.core.openid.oauth.type.oauth.CustomParams;

/**
 * The custom parameters of the authorization request an authentication transaction serves, each
 * with the source it can be trusted as (Issue #1907).
 *
 * <p>Kept on the transaction because the conditions that read them are evaluated in later requests
 * — authentication steps, the authorize call, a reused session — where the authorization request is
 * read back from storage and the sources are no longer known.
 *
 * <p>The source recorded is the one the value can be trusted as, not only the channel it came by. A
 * value nothing protected counts as {@link CustomParamSource#QUERY} whatever the channel: one from
 * an unsigned request object, or from a pushed authorization request of a client that did not
 * authenticate.
 */
public class AuthenticationCustomParams {

  Map<String, String> values;
  Map<String, CustomParamSource> sources;

  public AuthenticationCustomParams() {
    this(new HashMap<>(), new HashMap<>());
  }

  AuthenticationCustomParams(Map<String, String> values, Map<String, CustomParamSource> sources) {
    this.values = values;
    this.sources = sources;
  }

  /**
   * @param customParams the custom parameters of the authorization request
   * @param pushed whether the request was pushed (RFC 9126); its values are then all {@code
   *     pushed}, since the request is read back from storage
   * @param clientAuthenticated whether the client authenticates, which is what protects a pushed
   *     request
   * @param requestObjectSigned whether the request object, if any, was signed
   */
  public static AuthenticationCustomParams of(
      CustomParams customParams,
      boolean pushed,
      boolean clientAuthenticated,
      boolean requestObjectSigned) {
    Map<String, String> values = new HashMap<>(customParams.values());
    Map<String, CustomParamSource> sources = new HashMap<>();
    values
        .keySet()
        .forEach(
            key -> {
              CustomParamSource source =
                  pushed ? CustomParamSource.PUSHED : customParams.sourceOf(key);
              sources.put(key, trustedAs(source, clientAuthenticated, requestObjectSigned));
            });
    return new AuthenticationCustomParams(values, sources);
  }

  private static CustomParamSource trustedAs(
      CustomParamSource source, boolean clientAuthenticated, boolean requestObjectSigned) {
    if (source == CustomParamSource.PUSHED && clientAuthenticated) {
      return CustomParamSource.PUSHED;
    }
    if (source == CustomParamSource.REQUEST_OBJECT && requestObjectSigned) {
      return CustomParamSource.REQUEST_OBJECT;
    }
    return CustomParamSource.QUERY;
  }

  /**
   * @param map as written by {@link #toMap()}: {@code {key: {"value": ..., "source": ...}}}
   */
  public static AuthenticationCustomParams fromMap(Map<String, Object> map) {
    Map<String, String> values = new HashMap<>();
    Map<String, CustomParamSource> sources = new HashMap<>();
    if (map == null) {
      return new AuthenticationCustomParams(values, sources);
    }
    map.forEach(
        (key, entry) -> {
          if (entry instanceof Map<?, ?> fields && fields.get("value") instanceof String value) {
            values.put(key, value);
            CustomParamSource source =
                fields.get("source") instanceof String name ? CustomParamSource.of(name) : null;
            sources.put(key, source != null ? source : CustomParamSource.QUERY);
          }
        });
    return new AuthenticationCustomParams(values, sources);
  }

  /**
   * @return the values whose source is one of {@code trustedSources}
   */
  public Map<String, String> valuesFrom(Collection<CustomParamSource> trustedSources) {
    Map<String, String> result = new HashMap<>();
    values.forEach(
        (key, value) -> {
          if (trustedSources.contains(sources.get(key))) {
            result.put(key, value);
          }
        });
    return result;
  }

  public CustomParamSource sourceOf(String key) {
    return sources.get(key);
  }

  public boolean exists() {
    return !values.isEmpty();
  }

  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    values.forEach(
        (key, value) -> map.put(key, Map.of("value", value, "source", sources.get(key).value())));
    return map;
  }
}
