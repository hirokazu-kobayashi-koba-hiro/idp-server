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

package org.idp.server.control_plane.management.tenant;

import org.idp.server.control_plane.management.onboarding.io.TenantRegistrationRequest;
import org.idp.server.control_plane.management.tenant.io.TenantRequest;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfiguration;
import org.idp.server.platform.json.JsonConverter;
import org.idp.server.platform.multi_tenancy.organization.AssignedTenant;
import org.idp.server.platform.multi_tenancy.organization.Organization;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantAttributes;
import org.idp.server.platform.multi_tenancy.tenant.TenantType;

public class TenantManagementRegistrationContextCreator {
  Tenant adminTenant;
  TenantRequest request;
  Organization organization;
  User user;
  boolean dryRun;
  JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();

  public TenantManagementRegistrationContextCreator(
      Tenant adminTenant,
      TenantRequest request,
      Organization organization,
      User user,
      boolean dryRun) {
    this.adminTenant = adminTenant;
    this.request = request;
    this.organization = organization;
    this.user = user;
    this.dryRun = dryRun;
  }

  public TenantManagementRegistrationContext create() {

    TenantRegistrationRequest tenantRequest =
        jsonConverter.read(request.get("tenant"), TenantRegistrationRequest.class);
    AuthorizationServerConfiguration authorizationServerConfiguration =
        jsonConverter.read(
            request.get("authorization_server"), AuthorizationServerConfiguration.class);

    TenantAttributes attributes =
        tenantRequest.attributes() != null
            ? new TenantAttributes(tenantRequest.attributes())
            : new TenantAttributes();

    Tenant tenant =
        new Tenant(
            tenantRequest.tenantIdentifier(),
            tenantRequest.tenantName(),
            TenantType.PUBLIC,
            tenantRequest.tenantDomain(),
            tenantRequest.authorizationProvider(),
            tenantRequest.databaseType(),
            attributes);
    AssignedTenant assignedTenant =
        new AssignedTenant(tenant.identifierValue(), tenant.name().value(), tenant.type().name());
    Organization assigned = organization.updateWithTenant(assignedTenant);
    user.addAssignedTenant(tenant.identifier());

    return new TenantManagementRegistrationContext(
        adminTenant, tenant, authorizationServerConfiguration, assigned, user, dryRun);
  }
}
