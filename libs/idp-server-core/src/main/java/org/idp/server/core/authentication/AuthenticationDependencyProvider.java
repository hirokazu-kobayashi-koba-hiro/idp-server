package org.idp.server.core.authentication;

public interface AuthenticationDependencyProvider<T> {
  Class<T> type();

  T provide();
}
