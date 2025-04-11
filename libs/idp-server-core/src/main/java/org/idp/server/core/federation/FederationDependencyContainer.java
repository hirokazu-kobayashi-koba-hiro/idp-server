package org.idp.server.core.federation;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.federation.exception.SsoDependencyMissionException;

public class FederationDependencyContainer {

  Map<Class<?>, Object> dependencies;

  public FederationDependencyContainer() {
    this.dependencies = new HashMap<>();
  }

  public void register(Class<?> type, Object instance) {
    dependencies.put(type, instance);
  }

  public <T> T resolve(Class<T> type) {
    if (!dependencies.containsKey(type)) {
      throw new SsoDependencyMissionException("Missing dependency for type: " + type.getName());
    }
    return type.cast(dependencies.get(type));
  }
}
