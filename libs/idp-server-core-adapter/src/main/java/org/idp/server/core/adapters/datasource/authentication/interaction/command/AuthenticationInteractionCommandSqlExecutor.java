package org.idp.server.core.adapters.datasource.authentication.interaction.command;

import org.idp.server.core.oidc.authentication.AuthorizationIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public interface AuthenticationInteractionCommandSqlExecutor {

  <T> void insert(Tenant tenant, AuthorizationIdentifier identifier, String key, T payload);

  <T> void update(Tenant tenant, AuthorizationIdentifier identifier, String key, T payload);
}
