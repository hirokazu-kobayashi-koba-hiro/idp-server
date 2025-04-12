package org.idp.server.core.basic.protcol;

import java.util.HashMap;
import java.util.Map;

public class ProtocolContainer {

  Map<Class<?>, Object> dependencies;

  public ProtocolContainer() {
    this.dependencies = new HashMap<>();
  }

  public void register(Class<?> type, Object instance) {
    dependencies.put(type, instance);
  }

  public <T> T resolve(Class<T> type) {
    if (!dependencies.containsKey(type)) {
      throw new ProtocolMissionException("Missing protocol for type: " + type.getName());
    }
    return type.cast(dependencies.get(type));
  }
}
