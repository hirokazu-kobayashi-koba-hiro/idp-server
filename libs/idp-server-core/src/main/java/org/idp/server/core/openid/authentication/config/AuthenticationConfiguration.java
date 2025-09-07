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

package org.idp.server.core.openid.authentication.config;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.idp.server.platform.configuration.Configurable;
import org.idp.server.platform.json.JsonReadable;

public class AuthenticationConfiguration implements JsonReadable, Configurable {
  String id;
  String type;
  Map<String, Object> attributes = new HashMap<>();
  Map<String, Object> metadata = new HashMap<>();
  Map<String, AuthenticationInteractionConfig> interactions;
  boolean enabled = true;

  public AuthenticationConfiguration() {}

  public AuthenticationConfiguration(
      String id,
      String type,
      Map<String, Object> attributes,
      Map<String, Object> metadata,
      Map<String, AuthenticationInteractionConfig> interactions) {
    this.id = id;
    this.type = type;
    this.attributes = attributes;
    this.metadata = metadata;
    this.interactions = interactions;
    this.enabled = true;
  }

  public AuthenticationConfiguration(
      String id,
      String type,
      Map<String, Object> attributes,
      Map<String, Object> metadata,
      Map<String, AuthenticationInteractionConfig> interactions,
      boolean enabled) {
    this.id = id;
    this.type = type;
    this.attributes = attributes;
    this.metadata = metadata;
    this.interactions = interactions;
    this.enabled = enabled;
  }

  public String id() {
    return id;
  }

  public Map<String, Object> attributes() {
    if (attributes == null) {
      return new HashMap<>();
    }
    return attributes;
  }

  public Map<String, Object> metadata() {
    if (metadata == null) {
      return new HashMap<>();
    }
    return metadata;
  }

  public Map<String, AuthenticationInteractionConfig> authentications() {
    if (interactions == null) {
      return new HashMap<>();
    }
    return interactions;
  }

  public AuthenticationInteractionConfig getAuthenticationConfig(String key) {
    return interactions.get(key);
  }

  public Map<String, Object> interactionsAsMap() {
    if (interactions == null) {
      return new HashMap<>();
    }
    Map<String, Object> result = new HashMap<>();
    for (Map.Entry<String, AuthenticationInteractionConfig> entry : interactions.entrySet()) {
      result.put(entry.getKey(), entry.getValue().toMap());
    }
    return result;
  }

  public UUID idAsUUID() {
    return UUID.fromString(id);
  }

  public String type() {
    return type;
  }

  public AuthenticationConfigurationIdentifier identifier() {
    return new AuthenticationConfigurationIdentifier(id);
  }

  public boolean exists() {
    return id != null && !id.isEmpty();
  }

  @Override
  public boolean isEnabled() {
    return enabled;
  }

  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    map.put("id", id);
    map.put("type", type);
    map.put("attributes", attributes);
    map.put("metadata", metadata);
    map.put("interactions", interactionsAsMap());
    map.put("enabled", enabled);
    return map;
  }
}
