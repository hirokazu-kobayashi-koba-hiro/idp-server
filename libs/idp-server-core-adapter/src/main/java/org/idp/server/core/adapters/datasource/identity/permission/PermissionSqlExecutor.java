/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.adapters.datasource.identity.permission;

import org.idp.server.core.oidc.identity.permission.Permission;
import org.idp.server.core.oidc.identity.permission.Permissions;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public interface PermissionSqlExecutor {

  void insert(Tenant tenant, Permission permission);

  void bulkInsert(Tenant tenant, Permissions permissions);
}
