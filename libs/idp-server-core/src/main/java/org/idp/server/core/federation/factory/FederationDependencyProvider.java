package org.idp.server.core.federation.factory;

public interface FederationDependencyProvider<T> {
  Class<T> type();

  T provide();
}
