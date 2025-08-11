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

import java.util.HashMap;
import java.util.Map;
import org.idp.server.control_plane.base.ConfigRegistrationContext;
import org.idp.server.control_plane.management.tenant.io.TenantManagementResponse;
import org.idp.server.control_plane.management.tenant.io.TenantManagementStatus;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfiguration;
import org.idp.server.platform.multi_tenancy.organization.Organization;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class TenantManagementRegistrationContext implements ConfigRegistrationContext {

  Tenant adminTenant;
  Tenant newTenant;
  AuthorizationServerConfiguration authorizationServerConfiguration;
  Organization organization;
  User user;
  boolean dryRun;

  public TenantManagementRegistrationContext(
      Tenant adminTenant,
      Tenant newTenant,
      AuthorizationServerConfiguration authorizationServerConfiguration,
      Organization organization,
      User user,
      boolean dryRun) {
    this.adminTenant = adminTenant;
    this.newTenant = newTenant;
    this.authorizationServerConfiguration = authorizationServerConfiguration;
    this.organization = organization;
    this.user = user;
    this.dryRun = dryRun;
  }

  public Tenant adminTenant() {
    return adminTenant;
  }

  public Tenant newTenant() {
    return newTenant;
  }

  public AuthorizationServerConfiguration authorizationServerConfiguration() {
    return authorizationServerConfiguration;
  }

  public Organization organization() {
    return organization;
  }

  public User user() {
    return user;
  }

  @Override
  public String type() {
    return "tenant";
  }

  @Override
  public Map<String, Object> payload() {
    return authorizationServerConfiguration.toMap();
  }

  @Override
  public boolean isDryRun() {
    return dryRun;
  }

  public TenantManagementResponse toResponse() {
    Map<String, Object> contents = new HashMap<>();
    contents.put("result", newTenant.toMap());
    contents.put("dry_run", dryRun);
    return new TenantManagementResponse(TenantManagementStatus.CREATED, contents);
  }
}
