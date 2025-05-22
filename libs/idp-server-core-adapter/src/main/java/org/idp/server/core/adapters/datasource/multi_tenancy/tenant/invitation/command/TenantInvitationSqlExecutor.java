/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.adapters.datasource.multi_tenancy.tenant.invitation.command;

import org.idp.server.control_plane.management.tenant.invitation.operation.TenantInvitation;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public interface TenantInvitationSqlExecutor {
  void insert(Tenant tenant, TenantInvitation tenantInvitation);

  void delete(Tenant tenant, TenantInvitation tenantInvitation);

  void update(Tenant tenant, TenantInvitation tenantInvitation);
}
