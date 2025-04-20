package org.idp.server.core.adapters.datasource.authentication.interaction.command;

import org.idp.server.core.authentication.AuthenticationTransactionIdentifier;
import org.idp.server.core.tenant.Tenant;

public interface AuthenticationInteractionCommandSqlExecutor {

  <T> void insert(
      Tenant tenant, AuthenticationTransactionIdentifier identifier, String key, T payload);

  <T> void update(
      Tenant tenant, AuthenticationTransactionIdentifier identifier, String key, T payload);
}
