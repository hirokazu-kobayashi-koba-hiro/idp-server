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

package org.idp.server.control_plane.management.oidc.authorization.io;

import java.util.Map;
import org.idp.server.control_plane.management.oidc.authorization.handler.AuthorizationServerManagementHandler;

/**
 * Marker interface for authorization server management request objects.
 *
 * <p>Enables polymorphic handling of different request types in Handler layer:
 *
 * <ul>
 *   <li>AuthorizationServerFindRequest (get operation)
 *   <li>AuthorizationServerUpdateRequest (update operation)
 * </ul>
 *
 * @see AuthorizationServerManagementHandler
 */
public interface AuthorizationServerManagementRequest {

  /**
   * Converts request to Map for audit logging.
   *
   * @return request data as map
   */
  Map<String, Object> toMap();
}
