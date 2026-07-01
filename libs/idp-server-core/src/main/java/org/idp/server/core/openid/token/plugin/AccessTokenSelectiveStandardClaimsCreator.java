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

import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.openid.grant_management.grant.AuthorizationGrant;
import org.idp.server.core.openid.identity.StandardClaims;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.oauth.clientauthenticator.clientcredentials.ClientCredentials;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfiguration;
import org.idp.server.core.openid.oauth.type.oauth.Scopes;

/**
 * Adds OIDC standard claims to the JWT access token via the {@code standard_claims:} scope prefix.
 *
 * <p>Standard profile claims (name, email, phone_number, …) are otherwise only available on the ID
 * Token / UserInfo (via the profile/email/… scopes). This creator lets a client opt in to surfacing
 * a specific standard claim on the access token — e.g. {@code standard_claims:email} — so a
 * resource server can read it without a UserInfo round-trip.
 *
 * <p>Sibling of the access-token selective claim creators (verified claims / user custom
 * properties): access-token-only and gated by its own {@code
 * access_token_selective_standard_claims} flag. Distinct from {@link
 * ScopeMappingCustomClaimsCreator} ({@code claims:} prefix), which maps custom_properties and
 * system claims across access token / ID token / UserInfo. The prefixes are matched by {@code
 * startsWith}, so {@code standard_claims:*} never collides with {@code claims:*}.
 *
 * <p>Note (privacy): the access token audience is the resource server and tokens are frequently
 * logged/cached, so only opt in to standard claims (especially PII) on the access token when the
 * resource server genuinely needs them.
 */
public class AccessTokenSelectiveStandardClaimsCreator implements AccessTokenCustomClaimsCreator {

  private static final String prefix = "standard_claims:";

  @Override
  public boolean shouldCreate(
      AuthorizationGrant authorizationGrant,
      AuthorizationServerConfiguration authorizationServerConfiguration,
      ClientConfiguration clientConfiguration,
      ClientCredentials clientCredentials) {

    if (!authorizationServerConfiguration.enabledAccessTokenSelectiveStandardClaims()) {
      return false;
    }

    return authorizationGrant.scopes().hasScopeMatchedPrefix(prefix);
  }

  @Override
  public Map<String, Object> create(
      AuthorizationGrant authorizationGrant,
      AuthorizationServerConfiguration authorizationServerConfiguration,
      ClientConfiguration clientConfiguration,
      ClientCredentials clientCredentials) {

    User user = authorizationGrant.user();
    Map<String, Object> claims = new HashMap<>();

    Scopes filteredClaimsScope = authorizationGrant.scopes().filterMatchedPrefix(prefix);

    for (String scope : filteredClaimsScope) {
      String claimName = scope.substring(prefix.length());
      StandardClaims.resolve(user, claimName).ifPresent(value -> claims.put(claimName, value));
    }

    return claims;
  }
}
