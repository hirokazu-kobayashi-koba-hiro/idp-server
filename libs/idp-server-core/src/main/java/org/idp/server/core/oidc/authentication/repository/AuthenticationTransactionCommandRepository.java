package org.idp.server.core.oidc.authentication.repository;

import org.idp.server.core.oidc.authentication.AuthenticationTransaction;
import org.idp.server.core.oidc.authentication.AuthorizationIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public interface AuthenticationTransactionCommandRepository {

  void register(Tenant tenant, AuthenticationTransaction authenticationTransaction);

  void update(Tenant tenant, AuthenticationTransaction authenticationTransaction);

  void delete(Tenant tenant, AuthorizationIdentifier identifier);
}
