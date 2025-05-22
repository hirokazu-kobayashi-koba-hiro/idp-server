/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.adapters.datasource.oidc.configuration.client.command;

import org.idp.server.core.oidc.client.ClientIdentifier;
import org.idp.server.core.oidc.configuration.client.ClientConfiguration;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public interface ClientConfigCommandSqlExecutor {

  void insert(Tenant tenant, ClientConfiguration clientConfiguration);

  void update(Tenant tenant, ClientConfiguration clientConfiguration);

  void delete(Tenant tenant, ClientIdentifier clientIdentifier);
}
