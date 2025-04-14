package org.idp.server.core.authentication;

import org.idp.server.core.tenant.Tenant;

public interface AuthenticationTransactionQueryRepository {

  <T> T get(
      Tenant tenant, AuthenticationTransactionIdentifier identifier, String key, Class<T> clazz);
}
