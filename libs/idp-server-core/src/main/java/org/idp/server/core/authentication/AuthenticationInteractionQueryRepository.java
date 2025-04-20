package org.idp.server.core.authentication;

import org.idp.server.core.tenant.Tenant;

public interface AuthenticationInteractionQueryRepository {

  <T> T get(
      Tenant tenant, AuthenticationTransactionIdentifier identifier, String key, Class<T> clazz);
}
