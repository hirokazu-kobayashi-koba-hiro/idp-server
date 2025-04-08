package org.idp.server.core.security;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.authentication.exception.AuthenticationDependencyMissionException;

public class SecurityEventDependencyContainer {

  Map<Class<?>, Object> dependencies;

  public SecurityEventDependencyContainer() {
    this.dependencies = new HashMap<>();
  }

  public void register(Class<?> type, Object instance) {
    dependencies.put(type, instance);
  }

  public <T> T resolve(Class<T> type) {
    if (!dependencies.containsKey(type)) {
      throw new AuthenticationDependencyMissionException(
          "Missing security event dependency for type: " + type.getName());
    }
    return type.cast(dependencies.get(type));
  }
}
