package org.idp.server.core.oidc.authentication.repository;

import org.idp.server.core.oidc.authentication.AuthorizationIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public interface AuthenticationInteractionCommandRepository {

  <T> void register(Tenant tenant, AuthorizationIdentifier identifier, String key, T payload);

  <T> void update(Tenant tenant, AuthorizationIdentifier identifier, String key, T payload);
}
