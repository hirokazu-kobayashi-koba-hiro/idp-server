package org.idp.server.core.basic.datasource;

import java.util.HashMap;
import java.util.Map;

public class DataSourceContainer {

  Map<Class<?>, Object> dependencies;

  public DataSourceContainer() {
    this.dependencies = new HashMap<>();
  }

  public void register(Class<?> type, Object instance) {
    dependencies.put(type, instance);
  }

  public <T> T resolve(Class<T> type) {
    if (!dependencies.containsKey(type)) {
      throw new DataSourceMissionException("Missing datasource for type: " + type.getName());
    }
    return type.cast(dependencies.get(type));
  }
}
