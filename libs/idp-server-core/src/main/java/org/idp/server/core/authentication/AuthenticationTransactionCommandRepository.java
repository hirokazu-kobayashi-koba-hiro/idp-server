package org.idp.server.core.authentication;

public interface AuthenticationTransactionCommandRepository {

  <T> void register(AuthenticationTransactionIdentifier identifier, String key, T payload);

  <T> void update(AuthenticationTransactionIdentifier identifier, String key, T payload);
}
