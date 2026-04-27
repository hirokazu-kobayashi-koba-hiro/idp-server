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

import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.openid.grant_management.grant.AuthorizationGrant;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfiguration;
import org.idp.server.core.openid.oauth.type.oauth.Scopes;
import org.idp.server.core.openid.userinfo.plugin.UserinfoCustomIndividualClaimsCreator;
import org.idp.server.platform.json.JsonNodeWrapper;

/**
 * Returns verified_claims in UserInfo response based on scope-based filtering.
 *
 * <p>Uses the same {@code verified_claims:*} scope prefix pattern as {@link
 * AccessTokenSelectiveVerifiedClaimsCreator}. When scopes like {@code verified_claims:given_name}
 * are present, the corresponding claims are included in the response with proper OIDC4IDA structure
 * ({@code verification} + {@code claims}).
 */
public class UserinfoSelectiveVerifiedClaimsCreator
    implements UserinfoCustomIndividualClaimsCreator {

  private static final String prefix = "verified_claims:";

  @Override
  public boolean shouldCreate(
      User user,
      AuthorizationGrant authorizationGrant,
      AuthorizationServerConfiguration authorizationServerConfiguration,
      ClientConfiguration clientConfiguration) {

    if (!authorizationServerConfiguration.enabledAccessTokenSelectiveVerifiedClaims()) {
      return false;
    }

    Scopes scopes = authorizationGrant.scopes();
    if (!scopes.hasScopeMatchedPrefix(prefix)) {
      return false;
    }

    JsonNodeWrapper userVerifiedClaims = user.verifiedClaimsNodeWrapper();
    return user.hasVerifiedClaims() && userVerifiedClaims.contains("claims");
  }

  @Override
  public Map<String, Object> create(
      User user,
      AuthorizationGrant authorizationGrant,
      AuthorizationServerConfiguration authorizationServerConfiguration,
      ClientConfiguration clientConfiguration) {

    Scopes scopes = authorizationGrant.scopes();
    Scopes filteredScopes = scopes.filterMatchedPrefix(prefix);
    JsonNodeWrapper userVerifiedClaims = user.verifiedClaimsNodeWrapper();

    Map<String, Object> verification =
        userVerifiedClaims.contains("verification")
            ? userVerifiedClaims.getValueAsJsonNode("verification").toMap()
            : new HashMap<>();
    Map<String, Object> userClaims = userVerifiedClaims.getValueAsJsonNode("claims").toMap();

    Map<String, Object> selectedClaims = new HashMap<>();
    for (String scope : filteredScopes) {
      String claimName = scope.substring(prefix.length());

      if (userClaims.containsKey(claimName)) {
        selectedClaims.put(claimName, userClaims.get(claimName));
      }
    }

    Map<String, Object> result = new HashMap<>();
    Map<String, Object> verifiedClaimsStructure = new HashMap<>();
    verifiedClaimsStructure.put("verification", verification);
    verifiedClaimsStructure.put("claims", selectedClaims);
    result.put("verified_claims", verifiedClaimsStructure);

    return result;
  }
}
