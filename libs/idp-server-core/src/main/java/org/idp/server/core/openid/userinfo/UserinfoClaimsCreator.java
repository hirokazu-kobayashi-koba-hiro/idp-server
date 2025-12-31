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

package org.idp.server.core.openid.userinfo;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.openid.grant_management.grant.AuthorizationGrant;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.id_token.IndividualClaimsCreatable;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfiguration;
import org.idp.server.core.openid.userinfo.plugin.UserinfoCustomIndividualClaimsCreators;

/**
 * UserinfoClaimsCreator
 *
 * <p>Creates claims for the Userinfo response by combining standard OIDC claims with custom claims.
 *
 * <p><b>Security Note:</b> Standard OIDC claims (sub, name, email, etc.) cannot be overwritten by
 * custom claims. Custom claims are added first, then standard claims override any conflicts. This
 * prevents malicious or misconfigured plugins from tampering with identity-critical claims.
 *
 * @see <a href="https://openid.net/specs/openid-connect-core-1_0.html#UserInfo">OIDC Core 1.0 -
 *     UserInfo Endpoint</a>
 */
public class UserinfoClaimsCreator implements IndividualClaimsCreatable {

  User user;
  AuthorizationGrant authorizationGrant;
  AuthorizationServerConfiguration authorizationServerConfiguration;
  ClientConfiguration clientConfiguration;
  UserinfoCustomIndividualClaimsCreators userinfoCustomIndividualClaimsCreators;

  public UserinfoClaimsCreator(
      User user,
      AuthorizationGrant authorizationGrant,
      AuthorizationServerConfiguration authorizationServerConfiguration,
      ClientConfiguration clientConfiguration,
      UserinfoCustomIndividualClaimsCreators userinfoCustomIndividualClaimsCreators) {
    this.user = user;
    this.authorizationGrant = authorizationGrant;
    this.authorizationServerConfiguration = authorizationServerConfiguration;
    this.clientConfiguration = clientConfiguration;
    this.userinfoCustomIndividualClaimsCreators = userinfoCustomIndividualClaimsCreators;
  }

  public Map<String, Object> createClaims() {

    Map<String, Object> claims = new HashMap<>();
    Map<String, Object> individualClaims =
        createIndividualClaims(user, authorizationGrant.userinfoClaims());
    Map<String, Object> customIndividualClaims =
        userinfoCustomIndividualClaimsCreators.createCustomIndividualClaims(
            user, authorizationGrant, authorizationServerConfiguration, clientConfiguration);

    // Custom claims first, then standard claims override
    // This prevents custom claims from overwriting standard OIDC claims (sub, name, email, etc.)
    claims.putAll(customIndividualClaims);
    claims.putAll(individualClaims);

    return claims;
  }
}
