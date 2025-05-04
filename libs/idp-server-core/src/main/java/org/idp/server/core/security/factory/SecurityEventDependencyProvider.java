package org.idp.server.core.security.factory;

public interface SecurityEventDependencyProvider<T> {
  Class<T> type();

  T provide();
}
