package org.idp.server.core.authentication;

import org.idp.server.core.multi_tenancy.tenant.Tenant;

public interface AuthenticationInteractionQueryRepository {

  <T> T get(Tenant tenant, AuthorizationIdentifier identifier, String key, Class<T> clazz);
}
