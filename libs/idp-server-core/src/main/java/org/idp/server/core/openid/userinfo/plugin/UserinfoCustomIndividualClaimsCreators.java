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

package org.idp.server.core.openid.userinfo.plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.core.openid.grant_management.grant.AuthorizationGrant;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfiguration;
import org.idp.server.core.openid.plugin.token.UserinfoCustomUserClaimsCreationPluginLoader;
import org.idp.server.platform.log.LoggerWrapper;

public class UserinfoCustomIndividualClaimsCreators {

  List<UserinfoCustomIndividualClaimsCreator> creators;
  LoggerWrapper log = LoggerWrapper.getLogger(UserinfoCustomIndividualClaimsCreators.class);

  public UserinfoCustomIndividualClaimsCreators() {
    this.creators = new ArrayList<>();
    creators.add(new UserinfoScopeMappingCustomClaimsCreator());
    List<UserinfoCustomIndividualClaimsCreator> loadedCreators =
        UserinfoCustomUserClaimsCreationPluginLoader.load();
    creators.addAll(loadedCreators);
  }

  public Map<String, Object> createCustomIndividualClaims(
      User user,
      AuthorizationGrant authorizationGrant,
      AuthorizationServerConfiguration authorizationServerConfiguration,
      ClientConfiguration clientConfiguration) {

    Map<String, Object> claims = new HashMap<>();
    creators.forEach(
        creator -> {
          if (creator.shouldCreate(
              user, authorizationGrant, authorizationServerConfiguration, clientConfiguration)) {

            log.info(
                "Execute UserinfoCustomIndividualClaimsCreator : " + creator.getClass().getName());
            Map<String, Object> customIndividualClaims =
                creator.create(
                    user,
                    authorizationGrant,
                    authorizationServerConfiguration,
                    clientConfiguration);
            claims.putAll(customIndividualClaims);
          }
        });

    return claims;
  }
}
