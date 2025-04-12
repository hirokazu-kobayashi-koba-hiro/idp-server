package org.idp.server.core.basic.dependencies;

public interface ApplicationComponentProvider<T> {
  Class<T> type();

  T provide(ApplicationComponentDependencyContainer container);
}
