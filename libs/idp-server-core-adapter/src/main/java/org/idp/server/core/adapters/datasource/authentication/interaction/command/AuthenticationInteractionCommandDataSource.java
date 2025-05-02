package org.idp.server.core.adapters.datasource.authentication.interaction.command;

import org.idp.server.basic.json.JsonConverter;
import org.idp.server.core.authentication.AuthorizationIdentifier;
import org.idp.server.core.authentication.repository.AuthenticationInteractionCommandRepository;
import org.idp.server.core.multi_tenancy.tenant.Tenant;

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
