package org.idp.server.core.adapters.datasource.authentication.interaction.command;

import org.idp.server.core.authentication.AuthorizationIdentifier;
import org.idp.server.core.multi_tenancy.tenant.Tenant;

public interface AuthenticationInteractionCommandSqlExecutor {

  <T> void insert(Tenant tenant, AuthorizationIdentifier identifier, String key, T payload);

  <T> void update(Tenant tenant, AuthorizationIdentifier identifier, String key, T payload);
}
