/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.adapters.datasource.authentication.config.command;

import org.idp.server.core.oidc.authentication.AuthenticationConfiguration;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public interface AuthenticationConfigCommandSqlExecutor {
  void insert(Tenant tenant, AuthenticationConfiguration configuration);

  void update(Tenant tenant, AuthenticationConfiguration configuration);

  void delete(Tenant tenant, AuthenticationConfiguration configuration);
}
