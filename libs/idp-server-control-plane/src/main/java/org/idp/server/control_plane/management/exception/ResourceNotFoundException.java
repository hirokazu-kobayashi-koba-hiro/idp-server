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

package org.idp.server.control_plane.management.exception;

import java.util.Map;

/**
 * Exception thrown when a requested resource is not found.
 *
 * <p>This exception is used in Management API operations when attempting to access, update, or
 * delete a resource that does not exist (e.g., user, client, scope).
 *
 * <h2>HTTP Status Mapping</h2>
 *
 * <p>This exception typically maps to HTTP 404 Not Found.
 *
 * <h2>Usage Example</h2>
 *
 * <pre>{@code
 * User user = userQueryRepository.findById(tenant, userIdentifier);
 * if (!user.exists()) {
 *   throw new ResourceNotFoundException("User not found: " + userIdentifier.value());
 * }
 * }</pre>
 */
public class ResourceNotFoundException extends ManagementApiException {

  public ResourceNotFoundException(String message) {
    super(message);
  }

  @Override
  public String errorCode() {
    return "not_found";
  }

  @Override
  public Map<String, Object> errorDetails() {
    return Map.of();
  }
}
