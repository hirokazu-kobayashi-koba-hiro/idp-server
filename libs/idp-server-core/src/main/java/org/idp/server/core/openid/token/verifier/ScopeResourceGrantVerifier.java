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

/**
 * Refuses a token request whose scopes belong to more than one resource.
 *
 * <p>RFC 9068 Section 3: if the values in the "scope" parameter refer to different default resource
 * indicator values, the authorization server SHOULD reject the request with "invalid_scope".
 *
 * <p>For the grants that carry their own scope — client credentials, resource owner password
 * credentials, and the JWT bearer assertion grant — the token request is where the scope is
 * decided, so it is where this belongs. Grants whose scope was decided by an authorization request
 * are checked there instead, and a refresh reuses a grant that was already checked.
 *
 * <p>Shared rather than repeated per grant: an access token can name only one resource, and a grant
 * that skipped the check would issue one whose scopes do not all have meaning for the resource its
 * audience names (Section 2.2.3).
 *
 * @see <a href="https://www.rfc-editor.org/rfc/rfc9068.html#section-3">RFC 9068 Section 3</a>
 */
public class ScopeResourceGrantVerifier {

  Scopes scopes;
  Map<String, List<String>> scopeResourceMapping;

  public ScopeResourceGrantVerifier(Scopes scopes, Map<String, List<String>> scopeResourceMapping) {
    this.scopes = scopes;
    this.scopeResourceMapping = scopeResourceMapping;
  }

  public void verify() {
    List<String> resources =
        ResourceIndicatorResolver.resolve(scopeResourceMapping, scopes.toStringList());

    if (resources.size() > 1) {
      throw new TokenBadRequestException(
          "invalid_scope", "requested scopes belong to different resources: " + resources);
    }
  }
}
