package org.idp.server.core.authentication.factory;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.authentication.exception.AuthenticationDependencyMissionException;

public class AuthenticationDependencyContainer {

  Map<Class<?>, Object> dependencies;

  public AuthenticationDependencyContainer() {
    this.dependencies = new HashMap<>();
  }

  public void register(Class<?> type, Object instance) {
    dependencies.put(type, instance);
  }

  public <T> T resolve(Class<T> type) {
    if (!dependencies.containsKey(type)) {
      throw new AuthenticationDependencyMissionException(
          "Missing dependency for type: " + type.getName());
    }
    return type.cast(dependencies.get(type));
  }
}
