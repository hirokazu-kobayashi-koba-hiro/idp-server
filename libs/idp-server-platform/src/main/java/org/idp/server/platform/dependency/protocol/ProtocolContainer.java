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

package org.idp.server.platform.dependency.protocol;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ProtocolContainer {

  Map<Class<?>, Set<Object>> dependencies;

  public ProtocolContainer() {
    this.dependencies = new HashMap<>();
  }

  public void register(Class<?> type, Object instance) {
    if (dependencies.containsKey(type)) {
      Set<Object> objects = dependencies.get(type);
      objects.add(instance);
      dependencies.put(type, objects);
    } else {
      Set<Object> objects = new HashSet<>();
      objects.add(instance);
      dependencies.put(type, objects);
    }
  }

  @SuppressWarnings("unchecked")
  public <T> Set<T> resolveAll(Class<T> type) {
    if (!dependencies.containsKey(type)) {
      return Set.of();
    }
    Set<Object> set = dependencies.get(type);
    Set<T> typedSet = new HashSet<>();
    for (Object obj : set) {
      typedSet.add((T) obj);
    }
    return typedSet;
  }
}
