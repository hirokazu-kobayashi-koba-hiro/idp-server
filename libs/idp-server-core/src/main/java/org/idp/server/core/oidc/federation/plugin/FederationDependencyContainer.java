/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.oidc.federation.plugin;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.oidc.federation.exception.SsoDependencyMissionException;

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
