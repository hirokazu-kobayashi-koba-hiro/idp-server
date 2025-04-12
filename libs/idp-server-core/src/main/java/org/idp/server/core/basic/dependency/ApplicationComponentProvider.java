package org.idp.server.core.basic.dependency;

public interface ApplicationComponentProvider<T> {
  Class<T> type();

  T provide(ApplicationComponentDependencyContainer container);
}
