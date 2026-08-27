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

package org.idp.server.core.openid.token;

import java.net.URI;
import java.util.List;
import java.util.Map;
import org.idp.server.platform.log.LoggerWrapper;

/**
 * Resolves which resource the granted scopes are for.
 *
 * <p>RFC 9068 has the audience of an access token name a resource server, and where the request
 * carries no resource indicator it has the authorization server infer a default one from the
 * scopes. The association between the two is left to the deployment, so it is configuration here:
 *
 * <pre>
 * "scope_resource_mapping": {
 *   "https://api.example.com":      ["openid", "account"],
 *   "https://payments.example.com": ["payments"]
 * }
 * </pre>
 *
 * <p>The mapping is written resource-first for the same reason {@code acr_mapping_rules} is written
 * acr-first: the key is the thing being asserted, and the list is what leads to it.
 *
 * @see <a href="https://www.rfc-editor.org/rfc/rfc9068.html#section-3">RFC 9068 Section 3</a>
 */
public class ResourceIndicatorResolver {

  private static final LoggerWrapper log = LoggerWrapper.getLogger(ResourceIndicatorResolver.class);

  /**
   * The resource indicators the granted scopes point at.
   *
   * <p>More than one means the request spans resources, which the caller rejects rather than
   * resolving: a token naming several resources lets each of them accept scopes meant for another.
   *
   * @return the matching resource indicators, empty when nothing matches or nothing is configured
   */
  public static List<String> resolve(
      Map<String, List<String>> scopeResourceMapping, List<String> scopes) {

    if (scopeResourceMapping == null || scopeResourceMapping.isEmpty() || scopes == null) {
      return List.of();
    }

    return scopeResourceMapping.entrySet().stream()
        .filter(entry -> scopes.stream().anyMatch(entry.getValue()::contains))
        .map(Map.Entry::getKey)
        .filter(ResourceIndicatorResolver::isResourceIndicator)
        .toList();
  }

  /**
   * Whether a configured key is usable as a resource indicator.
   *
   * <p>RFC 8707 requires an absolute URI without a fragment. The check matters beyond tidiness: a
   * client identifier passes for a resource indicator if nothing looks, and an audience naming the
   * client rather than the resource is the confusion this claim exists to prevent — that is what
   * {@code client_id} is for, and what an ID token's audience means.
   *
   * <p>A rejected value is dropped and logged rather than failing the request, so a mistake in one
   * mapping entry does not stop tokens being issued for the others; the audience then falls back to
   * the configured default.
   *
   * @see <a href="https://www.rfc-editor.org/rfc/rfc8707.html#section-2">RFC 8707 Section 2</a>
   */
  static boolean isResourceIndicator(String value) {
    try {
      URI uri = new URI(value);
      if (!uri.isAbsolute() || uri.getFragment() != null) {
        log.warn(
            "scope_resource_mapping key is not usable as a resource indicator, so it is ignored:"
                + " value={}. RFC 8707 requires an absolute URI without a fragment.",
            value);
        return false;
      }
      return true;
    } catch (Exception e) {
      log.warn(
          "scope_resource_mapping key is not a URI, so it is ignored: value={}, reason={}",
          value,
          e.getMessage());
      return false;
    }
  }
}
