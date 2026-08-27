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
    new ScopeResourceGrantVerifier(scopes, scopeResourceMapping).verify();
  }

  void throwExceptionIfInvalidScope() {
    if (!scopes.exists()) {
      throw new TokenBadRequestException(
          "invalid_scope", "token request does not contains valid scope");
    }
  }
}
