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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.idp.server.platform.json.JsonReadable;
import org.idp.server.platform.security.hook.configuration.SecurityEventConfig;
import org.idp.server.platform.security.hook.configuration.SecurityEventHookConfiguration;

public class SecurityEventHookConfigurationRequest implements JsonReadable {

  String id;
  String type;
  Map<String, Object> attributes = new HashMap<>();
  Map<String, Object> metadata = new HashMap<>();
  List<String> triggers;
  int executionOrder;
  Map<String, SecurityEventConfig> events;
  boolean enabled;
  boolean storeExecutionPayload = true;

  public SecurityEventHookConfigurationRequest() {}

  public String id() {
    return id;
  }

  public boolean hasId() {
    return id != null && !id.isEmpty();
  }

  public SecurityEventHookConfiguration toConfiguration(String id) {

    return new SecurityEventHookConfiguration(
        id,
        type,
        attributes,
        metadata,
        triggers,
        executionOrder,
        events,
        enabled,
        storeExecutionPayload);
  }
}
