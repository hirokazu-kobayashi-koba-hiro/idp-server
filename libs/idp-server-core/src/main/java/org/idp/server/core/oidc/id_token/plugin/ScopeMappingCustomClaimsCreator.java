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

import java.util.HashMap;
import java.util.Map;
import org.idp.server.basic.type.extension.CustomProperties;
import org.idp.server.basic.type.oauth.Scopes;
import org.idp.server.core.oidc.authentication.Authentication;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.oidc.configuration.client.ClientConfiguration;
import org.idp.server.core.oidc.grant.AuthorizationGrant;
import org.idp.server.core.oidc.id_token.IdTokenCustomClaims;
import org.idp.server.core.oidc.id_token.RequestedClaimsPayload;
import org.idp.server.core.oidc.identity.User;

public class ScopeMappingCustomClaimsCreator implements CustomIndividualClaimsCreator {

  private static final String prefix = "claims:";

  @Override
  public boolean shouldCreate(
      User user,
      Authentication authentication,
      AuthorizationGrant authorizationGrant,
      IdTokenCustomClaims customClaims,
      RequestedClaimsPayload requestedClaimsPayload,
      AuthorizationServerConfiguration authorizationServerConfiguration,
      ClientConfiguration clientConfiguration) {

    if (authorizationServerConfiguration.isIdTokenStrictMode()) {
      return false;
    }

    if (!authorizationServerConfiguration.enabledCustomClaimsScopeMapping()) {
      return false;
    }

    return authorizationGrant.scopes().hasScopeMatchedPrefix(prefix);
  }

  @Override
  public Map<String, Object> create(
      User user,
      Authentication authentication,
      AuthorizationGrant authorizationGrant,
      IdTokenCustomClaims customClaims,
      RequestedClaimsPayload requestedClaimsPayload,
      AuthorizationServerConfiguration authorizationServerConfiguration,
      ClientConfiguration clientConfiguration) {

    Map<String, Object> claims = new HashMap<>();

    Scopes scopes = authorizationGrant.scopes();
    Scopes filteredClaimsScope = scopes.filterMatchedPrefix(prefix);
    CustomProperties customProperties = user.customProperties();

    for (String scope : filteredClaimsScope) {
      String claimName = scope.substring(prefix.length());

      if (customProperties.contains(claimName)) {
        claims.put(claimName, customProperties.getValue(claimName));
      }

      if (claimName.equals("roles") && user.hasRoles()) {
        claims.put("roles", user.roleNameAsListString());
      }

      if (claimName.equals("permissions") && user.hasPermissions()) {
        claims.put("permissions", user.permissions());
      }

      if (claimName.equals("assigned_tenant") && user.hasAssignedTenants()) {
        claims.put("assigned_tenants", user.assignedTenants());
        claims.put("current_tenant_id", user.currentTenantIdentifier().value());
      }

      if (claimName.equals("assigned_organization") && user.hasAssignedOrganizations()) {
        claims.put("assigned_organization", user.assignedOrganizations());
        claims.put("current_organization_id", user.currentTenantIdentifier().value());
      }
    }

    return claims;
  }
}
