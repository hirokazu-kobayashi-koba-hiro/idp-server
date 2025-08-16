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

package org.idp.server.platform.security.hook.configuration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.platform.json.JsonReadable;
import org.idp.server.platform.security.event.SecurityEventType;
import org.idp.server.platform.security.hook.SecurityEventHookType;

public class SecurityEventHookConfiguration implements JsonReadable {

  String id;
  String type;
  Map<String, Object> attributes = new HashMap<>();
  Map<String, Object> metadata = new HashMap<>();
  List<String> triggers = new ArrayList<>();
  int executionOrder = 100;
  Map<String, SecurityEventConfig> events = new HashMap<>();
  boolean enabled = false;

  public SecurityEventHookConfiguration() {}

  public SecurityEventHookConfiguration(
      String id,
      String type,
      Map<String, Object> attributes,
      Map<String, Object> metadata,
      List<String> triggers,
      int executionOrder,
      Map<String, SecurityEventConfig> events,
      boolean enabled) {
    this.id = id;
    this.type = type;
    this.attributes = attributes;
    this.metadata = metadata;
    this.triggers = triggers;
    this.executionOrder = executionOrder;
    this.events = events;
    this.enabled = enabled;
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

  public Map<String, Object> attributes() {
    if (attributes == null) {
      return new HashMap<>();
    }
    return attributes;
  }

  public boolean hasAttributes() {
    return attributes != null && !attributes.isEmpty();
  }

  public Map<String, Object> metadata() {
    if (metadata == null) {
      return new HashMap<>();
    }
    return metadata;
  }

  public boolean hasMetadata() {
    return metadata != null && !metadata.isEmpty();
  }

  public Map<String, SecurityEventConfig> events() {
    return events;
  }

  public boolean hasEvents() {
    return events != null && !events.isEmpty();
  }

  public Map<String, Object> eventsAsMap() {
    if (events == null) {
      return new HashMap<>();
    }

    Map<String, Object> map = new HashMap<>();
    for (Map.Entry<String, SecurityEventConfig> entry : events.entrySet()) {
      map.put(entry.getKey(), entry.getValue().toMap());
    }
    return map;
  }

  public SecurityEventConfig getEvent(SecurityEventType eventType) {
    if (events == null) {
      return new SecurityEventConfig();
    }
    if (events.containsKey(eventType.value())) {
      return events.get(eventType.value());
    }

    if (events.containsKey("default")) {
      return events.get("default");
    }

    return new SecurityEventConfig();
  }

  public List<String> triggers() {
    return triggers;
  }

  public boolean containsTrigger(String trigger) {
    return triggers.contains(trigger);
  }

  public boolean hasTriggers() {
    return triggers != null && !triggers.isEmpty();
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
    if (hasTriggers()) result.put("triggers", triggers);
    if (hasAttributes()) result.put("attributes", attributes);
    if (hasMetadata()) result.put("metadata", metadata);
    result.put("execution_order", executionOrder);
    if (hasEvents()) result.put("events", eventsAsMap());
    result.put("enabled", enabled);
    return result;
  }
}
