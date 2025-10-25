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

package org.idp.server.control_plane.management.federation.io;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.openid.federation.FederationConfigurationIdentifier;

/**
 * Request wrapper for federation configuration update operations.
 *
 * @param identifier the federation configuration identifier
 * @param request the federation configuration request
 */
public record FederationConfigUpdateRequest(
    FederationConfigurationIdentifier identifier, FederationConfigRequest request)
    implements FederationConfigManagementRequest {

  @Override
  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    map.put("id", identifier.value());
    map.put("configuration", request.toMap());
    return map;
  }
}
