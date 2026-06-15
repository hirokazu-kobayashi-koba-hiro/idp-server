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
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfiguration;
import org.idp.server.core.openid.oauth.type.oauth.Scopes;
import org.idp.server.core.openid.userinfo.plugin.UserinfoCustomIndividualClaimsCreator;
import org.idp.server.platform.json.JsonNodeWrapper;

/**
 * Returns verified_claims in UserInfo response based on scope-based filtering.
 *
 * <p>Uses the same {@code verified_claims:*} scope prefix pattern as {@link
 * AccessTokenSelectiveVerifiedClaimsCreator} (shared via {@link SelectiveVerifiedClaims}). When
 * scopes like {@code verified_claims:given_name} are present and match a user claim, the
 * corresponding claims are included with proper OIDC4IDA structure ({@code verification} + {@code
 * claims}).
 */
public class UserinfoSelectiveVerifiedClaimsCreator
    implements UserinfoCustomIndividualClaimsCreator {

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
    if (!scopes.hasScopeMatchedPrefix(SelectiveVerifiedClaims.PREFIX)) {
      return false;
    }

    if (!user.hasVerifiedClaims()) {
      return false;
    }
    return SelectiveVerifiedClaims.hasSelectableClaims(scopes, user.verifiedClaimsNodeWrapper());
  }

  @Override
  public Map<String, Object> create(
      User user,
      AuthorizationGrant authorizationGrant,
      AuthorizationServerConfiguration authorizationServerConfiguration,
      ClientConfiguration clientConfiguration) {

    JsonNodeWrapper userVerifiedClaims = user.verifiedClaimsNodeWrapper();
    return SelectiveVerifiedClaims.build(authorizationGrant.scopes(), userVerifiedClaims);
  }
}
