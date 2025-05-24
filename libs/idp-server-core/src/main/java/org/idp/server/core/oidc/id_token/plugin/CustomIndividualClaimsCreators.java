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


package org.idp.server.core.oidc.id_token.plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.core.oidc.authentication.Authentication;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.oidc.configuration.client.ClientConfiguration;
import org.idp.server.core.oidc.grant.AuthorizationGrant;
import org.idp.server.core.oidc.id_token.IdTokenCustomClaims;
import org.idp.server.core.oidc.id_token.RequestedClaimsPayload;
import org.idp.server.core.oidc.identity.User;
import org.idp.server.core.oidc.plugin.token.CustomUserClaimsCreationPluginLoader;
import org.idp.server.platform.log.LoggerWrapper;

public class CustomIndividualClaimsCreators {

  List<CustomIndividualClaimsCreator> creators;
  LoggerWrapper log = LoggerWrapper.getLogger(CustomIndividualClaimsCreators.class);

  public CustomIndividualClaimsCreators() {
    this.creators = new ArrayList<>();
    creators.add(new ScopeMappingCustomClaimsCreator());
    List<CustomIndividualClaimsCreator> loadedCreators =
        CustomUserClaimsCreationPluginLoader.load();
    creators.addAll(loadedCreators);
  }

  public Map<String, Object> createCustomIndividualClaims(
      User user,
      Authentication authentication,
      AuthorizationGrant authorizationGrant,
      IdTokenCustomClaims customClaims,
      RequestedClaimsPayload requestedClaimsPayload,
      AuthorizationServerConfiguration authorizationServerConfiguration,
      ClientConfiguration clientConfiguration) {

    Map<String, Object> claims = new HashMap<>();
    creators.forEach(
        creator -> {
          if (creator.shouldCreate(
              user,
              authentication,
              authorizationGrant,
              customClaims,
              requestedClaimsPayload,
              authorizationServerConfiguration,
              clientConfiguration)) {

            log.info("Execute CustomIndividualClaimsCreator : " + creator.getClass().getName());
            Map<String, Object> customIndividualClaims =
                creator.create(
                    user,
                    authentication,
                    authorizationGrant,
                    customClaims,
                    requestedClaimsPayload,
                    authorizationServerConfiguration,
                    clientConfiguration);
            claims.putAll(customIndividualClaims);
          }
        });

    return claims;
  }
}
