package org.idp.server.core.authentication.repository;

import org.idp.server.core.authentication.AuthenticationTransaction;
import org.idp.server.core.authentication.AuthorizationIdentifier;
import org.idp.server.core.multi_tenancy.tenant.Tenant;

public interface AuthenticationTransactionCommandRepository {

  void register(Tenant tenant, AuthenticationTransaction authenticationTransaction);

  void update(Tenant tenant, AuthenticationTransaction authenticationTransaction);

  void delete(Tenant tenant, AuthorizationIdentifier identifier);
}
