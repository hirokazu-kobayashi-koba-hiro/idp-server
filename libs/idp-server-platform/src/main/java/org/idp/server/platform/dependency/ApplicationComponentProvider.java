package org.idp.server.platform.dependency;

public interface ApplicationComponentProvider<T> {
  Class<T> type();

  T provide(ApplicationComponentDependencyContainer container);
}
