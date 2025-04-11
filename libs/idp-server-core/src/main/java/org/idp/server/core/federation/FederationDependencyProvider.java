package org.idp.server.core.federation;

public interface FederationDependencyProvider<T> {
  Class<T> type();

  T provide();
}
