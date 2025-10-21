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

import java.util.List;
import java.util.Map;

/**
 * Exception thrown when a management API request is invalid.
 *
 * <p>Represents validation failures such as:
 *
 * <ul>
 *   <li>JSON schema validation errors
 *   <li>Missing required fields
 *   <li>Invalid field formats
 * </ul>
 *
 * <p>Corresponds to HTTP 400 Bad Request status.
 */
public class InvalidRequestException extends ManagementApiException {

  private final List<String> errorMessages;

  public InvalidRequestException(String message, List<String> errorMessages) {
    super(message);
    this.errorMessages = errorMessages;
  }

  public InvalidRequestException(String message) {
    this(message, List.of());
  }

  public List<String> errorMessages() {
    return errorMessages;
  }

  @Override
  public String errorCode() {
    return "invalid_request";
  }

  @Override
  public Map<String, Object> errorDetails() {
    if (errorMessages.isEmpty()) {
      return Map.of();
    }
    return Map.of("error_messages", errorMessages);
  }
}
