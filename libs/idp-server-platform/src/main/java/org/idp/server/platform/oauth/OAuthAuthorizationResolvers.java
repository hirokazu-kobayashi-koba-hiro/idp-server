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

package org.idp.server.platform.oauth;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.platform.exception.UnSupportedException;

public class OAuthAuthorizationResolvers {

  Map<String, OAuthAuthorizationResolver> resolvers = new HashMap<>();

  public OAuthAuthorizationResolvers() {
    defaultResolvers();
  }

  public OAuthAuthorizationResolvers(Map<String, OAuthAuthorizationResolver> additionalResolvers) {
    defaultResolvers();
    this.resolvers.putAll(additionalResolvers);
  }

  private void defaultResolvers() {
    ClientCredentialsAuthorizationResolver clientCredentials =
        new ClientCredentialsAuthorizationResolver();
    resolvers.put(clientCredentials.type(), clientCredentials);
    ResourceOwnerPasswordCredentialsAuthorizationResolver password =
        new ResourceOwnerPasswordCredentialsAuthorizationResolver();
    resolvers.put(password.type(), password);
  }

  public OAuthAuthorizationResolver get(String type) {
    OAuthAuthorizationResolver resolver = resolvers.get(type);

    if (resolver == null) {
      throw new UnSupportedException("Unsupported OAuth authorization type: " + type);
    }

    return resolver;
  }
}
