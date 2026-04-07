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

package org.idp.server.core.openid.token.plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.core.openid.grant_management.grant.AuthorizationGrant;
import org.idp.server.core.openid.oauth.clientauthenticator.clientcredentials.ClientCredentials;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfiguration;
import org.idp.server.core.openid.plugin.token.AccessTokenCustomClaimsCreationPluginLoader;
import org.idp.server.platform.dependency.ApplicationComponentDependencyContainer;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class AccessTokenCustomClaimsCreators {

  List<AccessTokenCustomClaimsCreator> creators;
  LoggerWrapper log = LoggerWrapper.getLogger(AccessTokenCustomClaimsCreators.class);

  public AccessTokenCustomClaimsCreators() {
    this.creators = new ArrayList<>();
    this.creators.add(new ScopeMappingCustomClaimsCreator());
    creators.addAll(AccessTokenCustomClaimsCreationPluginLoader.load());
  }

  public AccessTokenCustomClaimsCreators(ApplicationComponentDependencyContainer container) {
    this.creators = new ArrayList<>();
    this.creators.add(new ScopeMappingCustomClaimsCreator());
    creators.addAll(AccessTokenCustomClaimsCreationPluginLoader.load());
    creators.addAll(AccessTokenCustomClaimsCreationPluginLoader.loadWithFactory(container));
  }

  public Map<String, Object> create(
      Tenant tenant,
      AuthorizationGrant authorizationGrant,
      AuthorizationServerConfiguration authorizationServerConfiguration,
      ClientConfiguration clientConfiguration,
      ClientCredentials clientCredentials) {
    Map<String, Object> customClaims = new HashMap<>();

    creators.forEach(
        creator -> {
          if (creator.shouldCreate(
              tenant,
              authorizationGrant,
              authorizationServerConfiguration,
              clientConfiguration,
              clientCredentials)) {
            log.debug("Execute AccessTokenCustomClaimsCreators : {}", creator.getClass().getName());
            Map<String, Object> claims =
                creator.create(
                    tenant,
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
