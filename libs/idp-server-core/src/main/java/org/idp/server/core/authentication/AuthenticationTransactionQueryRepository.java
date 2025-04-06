package org.idp.server.core.authentication;

public interface AuthenticationTransactionQueryRepository {

  <T> T get(AuthenticationTransactionIdentifier identifier, String key, Class<T> clazz);
}
