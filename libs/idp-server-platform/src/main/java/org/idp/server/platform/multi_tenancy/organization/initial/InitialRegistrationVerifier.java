/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package org.idp.server.platform.multi_tenancy.organization.initial;

import org.idp.server.platform.multi_tenancy.organization.OrganizationName;
import org.idp.server.platform.multi_tenancy.tenant.ServerDomain;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.multi_tenancy.tenant.TenantName;

public class InitialRegistrationVerifier {
  Tenant tenant;
  OrganizationName organizationName;
  ServerDomain serverDomain;
  TenantName tenantName;
  String serverConfig;

  public InitialRegistrationVerifier(
      Tenant tenant,
      OrganizationName organizationName,
      ServerDomain serverDomain,
      TenantName tenantName,
      String serverConfig) {
    this.tenant = tenant;
    this.organizationName = organizationName;
    this.serverDomain = serverDomain;
    this.tenantName = tenantName;
    this.serverConfig = serverConfig;
  }

  public void verify() {
    if (!tenant.isAdmin()) {
      throw new InitialRegistrationForbiddenException(
          "tenant is not admin. admin tenant only allowed initial registration request");
    }
  }
}
