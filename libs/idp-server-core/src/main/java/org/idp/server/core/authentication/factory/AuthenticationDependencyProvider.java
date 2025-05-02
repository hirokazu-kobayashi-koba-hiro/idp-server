package org.idp.server.core.authentication.factory;

public interface AuthenticationDependencyProvider<T> {
  Class<T> type();

  T provide();
}
