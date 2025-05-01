package org.idp.server.basic.dependency;

public interface ApplicationComponentProvider<T> {
  Class<T> type();

  T provide(ApplicationComponentDependencyContainer container);
}
