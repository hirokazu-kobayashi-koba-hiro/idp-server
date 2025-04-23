package org.idp.server.core.authentication;

import org.idp.server.core.tenant.Tenant;

public interface AuthenticationInteractionCommandRepository {

  <T> void register(Tenant tenant, AuthorizationIdentifier identifier, String key, T payload);

  <T> void update(Tenant tenant, AuthorizationIdentifier identifier, String key, T payload);
}
