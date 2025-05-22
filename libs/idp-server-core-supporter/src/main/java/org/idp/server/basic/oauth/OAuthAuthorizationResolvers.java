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


package org.idp.server.basic.oauth;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.platform.exception.UnSupportedException;

public class OAuthAuthorizationResolvers {

  Map<OAuthAuthorizationType, OAuthAuthorizationResolver> resolvers;

  public OAuthAuthorizationResolvers() {
    this.resolvers = new HashMap<>();
    resolvers.put(
        OAuthAuthorizationType.CLIENT_CREDENTIALS, new ClientCredentialsAuthorizationResolver());
    resolvers.put(
        OAuthAuthorizationType.RESOURCE_OWNER_PASSWORD_CREDENTIALS,
        new ResourceOwnerPasswordCredentialsAuthorizationResolver());
  }

  public OAuthAuthorizationResolver get(OAuthAuthorizationType type) {
    OAuthAuthorizationResolver resolver = resolvers.get(type);

    if (resolver == null) {
      throw new UnSupportedException("Unsupported OAuth authorization type: " + type.name());
    }

    return resolver;
  }
}
