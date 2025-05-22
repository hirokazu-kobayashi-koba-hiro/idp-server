/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.adapters.datasource.federation.session.command;

import org.idp.server.core.oidc.federation.sso.SsoSessionIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public interface SsoSessionCommandSqlExecutor {

  <T> void insert(Tenant tenant, SsoSessionIdentifier identifier, T payload);

  void delete(Tenant tenant, SsoSessionIdentifier identifier);
}
