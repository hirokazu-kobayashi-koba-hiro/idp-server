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

package org.idp.server.core.openid.oauth.configuration.client;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.platform.json.JsonReadable;

public class AvailableFederation implements JsonReadable {
  String id;
  String type;
  String ssoProvider;
  boolean autoSelected = false;

  public AvailableFederation() {}

  public String id() {
    return id;
  }

  public String type() {
    return type;
  }

  public String ssoProvider() {
    return ssoProvider;
  }

  public boolean autoSelected() {
    return autoSelected;
  }

  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    map.put("id", id);
    map.put("type", type);
    map.put("sso_provider", ssoProvider);
    map.put("auto_selected", autoSelected);
    return map;
  }
}
