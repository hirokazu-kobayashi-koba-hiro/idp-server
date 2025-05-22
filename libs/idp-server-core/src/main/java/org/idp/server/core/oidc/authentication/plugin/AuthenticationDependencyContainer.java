/*
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package org.idp.server.core.oidc.authentication.plugin;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.oidc.authentication.exception.AuthenticationDependencyMissionException;

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
