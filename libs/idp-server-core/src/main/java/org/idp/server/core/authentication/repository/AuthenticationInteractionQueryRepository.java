package org.idp.server.core.authentication.repository;

import org.idp.server.core.authentication.AuthorizationIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public interface AuthenticationInteractionQueryRepository {

  <T> T get(Tenant tenant, AuthorizationIdentifier identifier, String key, Class<T> clazz);
}
