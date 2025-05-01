package org.idp.server.core.authentication;

import org.idp.server.core.multi_tenancy.tenant.Tenant;

public interface AuthenticationTransactionCommandRepository {

  void register(Tenant tenant, AuthenticationTransaction authenticationTransaction);

  void update(Tenant tenant, AuthenticationTransaction authenticationTransaction);

  void delete(Tenant tenant, AuthorizationIdentifier identifier);
}
