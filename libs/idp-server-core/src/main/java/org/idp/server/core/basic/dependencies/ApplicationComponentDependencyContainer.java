package org.idp.server.core.basic.dependencies;

import java.util.HashMap;
import java.util.Map;

public class ApplicationComponentDependencyContainer {

  Map<Class<?>, Object> dependencies;

  public ApplicationComponentDependencyContainer() {
    this.dependencies = new HashMap<>();
  }

  public void register(Class<?> type, Object instance) {
    dependencies.put(type, instance);
  }

  public <T> T resolve(Class<T> type) {
    if (!dependencies.containsKey(type)) {
      throw new ApplicationComponentDependencyMissionException(
          "Missing dependency for type: " + type.getName());
    }
    return type.cast(dependencies.get(type));
  }
}
