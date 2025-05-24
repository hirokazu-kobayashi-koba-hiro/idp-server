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


package org.idp.server.core.oidc.token.plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.core.oidc.clientcredentials.ClientCredentials;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.oidc.configuration.client.ClientConfiguration;
import org.idp.server.core.oidc.grant.AuthorizationGrant;
import org.idp.server.core.oidc.plugin.token.AccessTokenCustomClaimsCreationPluginLoader;
import org.idp.server.platform.log.LoggerWrapper;

public class AccessTokenCustomClaimsCreators {

  List<AccessTokenCustomClaimsCreator> creators;
  LoggerWrapper log = LoggerWrapper.getLogger(AccessTokenCustomClaimsCreators.class);

  public AccessTokenCustomClaimsCreators() {
    this.creators = new ArrayList<>();
    this.creators.add(new AccessTokenUserCustomPropertiesCreator());
    creators.addAll(AccessTokenCustomClaimsCreationPluginLoader.load());
  }

  public Map<String, Object> create(
      AuthorizationGrant authorizationGrant,
      AuthorizationServerConfiguration authorizationServerConfiguration,
      ClientConfiguration clientConfiguration,
      ClientCredentials clientCredentials) {
    Map<String, Object> customClaims = new HashMap<>();

    creators.forEach(
        creator -> {
          if (creator.shouldCreate(
              authorizationGrant,
              authorizationServerConfiguration,
              clientConfiguration,
              clientCredentials)) {
            log.info("Execute AccessTokenCustomClaimsCreators : {}", creator.getClass().getName());
            Map<String, Object> claims =
                creator.create(
                    authorizationGrant,
                    authorizationServerConfiguration,
                    clientConfiguration,
                    clientCredentials);
            customClaims.putAll(claims);
          }
        });

    return customClaims;
  }
}
