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

package org.idp.server.core.extension.identity.verified;

import java.util.Map;
import org.idp.server.core.openid.grant_management.grant.AuthorizationGrant;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.oauth.clientauthenticator.clientcredentials.ClientCredentials;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfiguration;
import org.idp.server.core.openid.oauth.type.oauth.Scopes;
import org.idp.server.core.openid.token.plugin.AccessTokenCustomClaimsCreator;
import org.idp.server.platform.json.JsonNodeWrapper;

public class AccessTokenSelectiveVerifiedClaimsCreator implements AccessTokenCustomClaimsCreator {

  @Override
  public boolean shouldCreate(
      AuthorizationGrant authorizationGrant,
      AuthorizationServerConfiguration authorizationServerConfiguration,
      ClientConfiguration clientConfiguration,
      ClientCredentials clientCredentials) {

    if (!authorizationServerConfiguration.enabledAccessTokenSelectiveVerifiedClaims()) {
      return false;
    }

    Scopes scopes = authorizationGrant.scopes();
    if (!scopes.hasScopeMatchedPrefix(SelectiveVerifiedClaims.PREFIX)) {
      return false;
    }

    User user = authorizationGrant.user();
    if (!user.hasVerifiedClaims()) {
      return false;
    }
    return SelectiveVerifiedClaims.hasSelectableClaims(scopes, user.verifiedClaimsNodeWrapper());
  }

  @Override
  public Map<String, Object> create(
      AuthorizationGrant authorizationGrant,
      AuthorizationServerConfiguration authorizationServerConfiguration,
      ClientConfiguration clientConfiguration,
      ClientCredentials clientCredentials) {

    User user = authorizationGrant.user();
    JsonNodeWrapper userVerifiedClaims = user.verifiedClaimsNodeWrapper();
    return SelectiveVerifiedClaims.build(authorizationGrant.scopes(), userVerifiedClaims);
  }
}
