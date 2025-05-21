package org.idp.server.core.oidc.authentication.plugin;

public interface AuthenticationDependencyProvider<T> {
  Class<T> type();

  T provide();
}
