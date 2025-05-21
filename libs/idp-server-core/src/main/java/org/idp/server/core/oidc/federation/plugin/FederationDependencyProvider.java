package org.idp.server.core.oidc.federation.plugin;

public interface FederationDependencyProvider<T> {
  Class<T> type();

  T provide();
}
