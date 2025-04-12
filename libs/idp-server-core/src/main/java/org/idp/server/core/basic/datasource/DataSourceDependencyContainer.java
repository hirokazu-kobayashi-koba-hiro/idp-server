package org.idp.server.core.basic.datasource;

import java.util.HashMap;
import java.util.Map;

public class DataSourceDependencyContainer {

  Map<Class<?>, Object> dependencies;

  public DataSourceDependencyContainer() {
    this.dependencies = new HashMap<>();
  }

  public void register(Class<?> type, Object instance) {
    dependencies.put(type, instance);
  }

  public <T> T resolve(Class<T> type) {
    if (!dependencies.containsKey(type)) {
      throw new DataSourceDependencyMissionException(
          "Missing dependency for type: " + type.getName());
    }
    return type.cast(dependencies.get(type));
  }
}
