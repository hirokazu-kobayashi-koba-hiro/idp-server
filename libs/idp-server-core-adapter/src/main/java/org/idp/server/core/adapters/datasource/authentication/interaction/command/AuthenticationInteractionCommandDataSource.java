/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.adapters.datasource.authentication.interaction.command;

import org.idp.server.basic.json.JsonConverter;
import org.idp.server.core.oidc.authentication.AuthorizationIdentifier;
import org.idp.server.core.oidc.authentication.repository.AuthenticationInteractionCommandRepository;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class AuthenticationInteractionCommandDataSource
    implements AuthenticationInteractionCommandRepository {

  AuthenticationInteractionCommandSqlExecutors executors;
  JsonConverter jsonConverter;

  public AuthenticationInteractionCommandDataSource() {
    this.executors = new AuthenticationInteractionCommandSqlExecutors();
    this.jsonConverter = JsonConverter.snakeCaseInstance();
  }

  @Override
  public <T> void register(
      Tenant tenant, AuthorizationIdentifier identifier, String type, T payload) {
    AuthenticationInteractionCommandSqlExecutor executor = executors.get(tenant.databaseType());
    executor.insert(tenant, identifier, type, payload);
  }

  @Override
  public <T> void update(
      Tenant tenant, AuthorizationIdentifier identifier, String type, T payload) {
    AuthenticationInteractionCommandSqlExecutor executor = executors.get(tenant.databaseType());
    executor.update(tenant, identifier, type, payload);
  }
}
