package org.idp.server.core.security;

public interface SecurityEventDependencyProvider<T> {
  Class<T> type();

  T provide();
}
