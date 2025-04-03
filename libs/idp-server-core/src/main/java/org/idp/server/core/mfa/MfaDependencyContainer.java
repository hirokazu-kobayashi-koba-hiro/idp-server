package org.idp.server.core.mfa;

import java.util.HashMap;
import java.util.Map;

public class MfaDependencyContainer {

  Map<Class<?>, Object> dependencies;

  public MfaDependencyContainer() {
    this.dependencies = new HashMap<>();
  }

  public void register(Class<?> type, Object instance) {
    dependencies.put(type, instance);
  }

  public <T> T resolve(Class<T> type) {
    if (!dependencies.containsKey(type)) {
      throw new MfaDependencyMissionException("Missing dependency for type: " + type.getName());
    }
    return type.cast(dependencies.get(type));
  }


}
