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

package org.idp.server.control_plane.management.permission.io;

import java.util.Map;
import org.idp.server.core.openid.identity.permission.PermissionIdentifier;

/**
 * Request wrapper for permission delete operations.
 *
 * <p>Wraps PermissionIdentifier for delete operations.
 */
public record PermissionDeleteRequest(PermissionIdentifier identifier)
    implements PermissionManagementRequest {

  @Override
  public Map<String, Object> toMap() {
    return Map.of("id", identifier.value());
  }
}
