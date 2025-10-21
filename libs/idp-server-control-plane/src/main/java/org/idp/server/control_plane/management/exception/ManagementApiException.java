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
 * Base exception for all Management API errors.
 *
 * <p>Provides standardized error response structure:
 *
 * <pre>{@code
 * {
 *   "error": "error_code",
 *   "error_description": "Human readable description",
 *   "error_messages": [...] // Optional validation details
 * }
 * }</pre>
 *
 * <p>Follows the Handler/Service pattern where exceptions are thrown by Service/Validator layers
 * and caught by Protocol layer for error response formatting.
 */
public abstract class ManagementApiException extends RuntimeException {

  protected ManagementApiException(String message) {
    super(message);
  }

  protected ManagementApiException(String message, Throwable cause) {
    super(message, cause);
  }

  /**
   * Returns the OAuth 2.0 error code for this exception.
   *
   * @return error code (e.g., "invalid_request", "access_denied")
   */
  public abstract String errorCode();

  /**
   * Returns additional error details to include in the response.
   *
   * @return map of additional fields, empty if no details
   */
  public abstract Map<String, Object> errorDetails();

  /**
   * Returns the human-readable error description.
   *
   * @return error description from exception message
   */
  public String errorDescription() {
    return getMessage();
  }
}
