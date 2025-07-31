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

package org.idp.server.control_plane.management.security.hook.io;

import org.idp.server.platform.security.hook.SecurityEventHookType;
import org.idp.server.platform.security.hook.configuration.SecurityEventConfig;
import org.idp.server.platform.security.hook.configuration.SecurityEventHookConfiguration;
import org.idp.server.platform.security.hook.configuration.SecurityEventHookConfigurationIdentifier;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SecurityEventHookConfigurationRequest {

  String id;
  String type;
  Map<String, Object> attributes = new HashMap<>();
  List<String> triggers;
  int executionOrder;
  boolean enabled;
  Map<String, SecurityEventConfig> events;

  public SecurityEventHookConfigurationRequest() {}

  public SecurityEventHookConfigurationIdentifier identifier() {
    return new SecurityEventHookConfigurationIdentifier(id);
  }

  public String type() {
    return type;
  }

  public SecurityEventHookType hookType() {
    return new SecurityEventHookType(type);
  }

  public Map<String, SecurityEventConfig> events() {
    return events;
  }

  public SecurityEventConfig getEvent(String eventType) {
    if (events.containsKey(eventType)) {
      return events.get(eventType);
    }

    if (events.containsKey("default")) {
      return events.get("default");
    }

    return new SecurityEventConfig();
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
    result.put("events", events);
    return result;
  }

  public String id() {
    return id;
  }

  public boolean hasId() {
    return id != null && !id.isEmpty();
  }

  public SecurityEventHookConfiguration toConfiguration(String id) {

    return new SecurityEventHookConfiguration(id, type, attributes, triggers, executionOrder, events, enabled);
  }
}
