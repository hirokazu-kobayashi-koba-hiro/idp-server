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

package org.idp.server.platform.security.hook;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SecurityEventHookConfiguration {

  String id;
  String type;
  List<String> triggers;
  int executionOrder;
  boolean enabled;
  Map<String, Object> payload;

  public SecurityEventHookConfiguration() {}

  public SecurityEventHookConfiguration(
      String id,
      String type,
      List<String> triggers,
      int executionOrder,
      boolean enabled,
      Map<String, Object> payload) {
    this.id = id;
    this.type = type;
    this.triggers = triggers;
    this.executionOrder = executionOrder;
    this.enabled = enabled;
    this.payload = payload;
  }

  public SecurityEventHookConfigurationIdentifier identifier() {
    return new SecurityEventHookConfigurationIdentifier(id);
  }

  public String type() {
    return type;
  }

  public SecurityEventHookType hookType() {
    return new SecurityEventHookType(type);
  }

  public Map<String, Object> payload() {
    return payload;
  }

  public List<String> triggers() {
    return triggers;
  }

  public boolean hasTrigger(String trigger) {
    return triggers.contains(trigger);
  }

  public boolean exists() {
    return id != null && !id.isEmpty();
  }

  public int executionOrder() {
    return executionOrder;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public Map<String, Object> toMap() {
    Map<String, Object> result = new HashMap<>();
    result.put("id", id);
    result.put("type", type);
    result.put("triggers", triggers);
    result.put("execution_order", executionOrder);
    result.put("enabled", enabled);
    result.put("payload", payload);
    return result;
  }
}
