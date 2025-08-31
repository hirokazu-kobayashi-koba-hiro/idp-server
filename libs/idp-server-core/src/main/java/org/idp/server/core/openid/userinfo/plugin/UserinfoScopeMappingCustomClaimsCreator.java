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

import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.openid.grant_management.grant.AuthorizationGrant;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.openid.oauth.configuration.client.ClientConfiguration;
import org.idp.server.core.openid.oauth.type.extension.CustomProperties;
import org.idp.server.core.openid.oauth.type.oauth.Scopes;

public class UserinfoScopeMappingCustomClaimsCreator
    implements UserinfoCustomIndividualClaimsCreator {

  private static final String prefix = "claims:";

  @Override
  public boolean shouldCreate(
      User user,
      AuthorizationGrant authorizationGrant,
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
      AuthorizationGrant authorizationGrant,
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

      if (claimName.equals("status")) {
        claims.put("status", user.status().name());
      }

      if (claimName.equals("ex_sub") && user.hasExternalUserId()) {
        claims.put("ex_sub", user.externalUserId());
      }

      if (claimName.equals("roles") && user.hasRoles()) {
        claims.put("roles", user.roleNameAsListString());
      }

      if (claimName.equals("permissions") && user.hasPermissions()) {
        claims.put("permissions", user.permissions());
      }

      if (claimName.equals("assigned_tenants") && user.hasAssignedTenants()) {
        claims.put("assigned_tenants", user.assignedTenants());
        claims.put("current_tenant_id", user.currentTenantIdentifier().value());
      }

      if (claimName.equals("assigned_organizations") && user.hasAssignedOrganizations()) {
        claims.put("assigned_organizations", user.assignedOrganizations());
        claims.put("current_organization_id", user.currentTenantIdentifier().value());
      }

      if (claimName.equals("authentication_devices") && user.hasAuthenticationDevices()) {
        claims.put("authentication_devices", user.authenticationDevicesListAsMap());
      }
    }

    return claims;
  }
}
