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

package org.idp.server.core.openid.token.verifier;

import java.util.List;
import java.util.Map;
import org.idp.server.core.openid.oauth.type.oauth.Scopes;
import org.idp.server.core.openid.token.ResourceIndicatorResolver;
import org.idp.server.core.openid.token.exception.TokenBadRequestException;

public class ClientCredentialsGrantVerifier {

  Scopes scopes;
  Map<String, List<String>> scopeResourceMapping;

  public ClientCredentialsGrantVerifier(
      Scopes scopes, Map<String, List<String>> scopeResourceMapping) {
    this.scopes = scopes;
    this.scopeResourceMapping = scopeResourceMapping;
  }

  public void verify() {
    throwExceptionIfInvalidScope();
    throwExceptionIfScopesSpanResources();
  }

  void throwExceptionIfInvalidScope() {
    if (!scopes.exists()) {
      throw new TokenBadRequestException(
          "invalid_scope", "token request does not contains valid scope");
    }
  }

  /**
   * RFC 9068 Section 3.
   *
   * <p>If the values in the "scope" parameter refer to different default resource indicator values,
   * the authorization server SHOULD reject the request with "invalid_scope".
   *
   * <p>This grant takes its scope at the token endpoint rather than through an authorization
   * request, so this is where its scope is decided and where the check belongs. An access token can
   * only name one resource, and Section 2.2.3 requires every scope string it carries to have
   * meaning for the resources its audience names.
   *
   * @see <a href="https://www.rfc-editor.org/rfc/rfc9068.html#section-3">RFC 9068 Section 3</a>
   */
  void throwExceptionIfScopesSpanResources() {
    List<String> resources =
        ResourceIndicatorResolver.resolve(scopeResourceMapping, scopes.toStringList());

    if (resources.size() > 1) {
      throw new TokenBadRequestException(
          "invalid_scope", "requested scopes belong to different resources: " + resources);
    }
  }
}
