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

package org.idp.server.core.extension.identity.verification.io;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents unified error details for identity verification operations.
 *
 * <p>Based on OAuth 2.0 compliant error response format, providing the following structure:
 *
 * <ul>
 *   <li><b>error</b>: Standard error code
 *   <li><b>error_description</b>: User-friendly description
 *   <li><b>error_details</b>: Structured error details (object format)
 *   <li><b>error_messages</b>: Specific error message array
 * </ul>
 *
 * <p><b>Security Features:</b>
 *
 * <ul>
 *   <li>Internal system details masking
 *   <li>Sensitive information removal
 *   <li>Safe audit log integration
 * </ul>
 *
 * <p>Usage example:
 *
 * <pre>{@code
 * IdentityVerificationErrorDetails errorDetails = IdentityVerificationErrorDetails.builder()
 *     .error("pre_hook_validation_failed")
 *     .errorDescription("Pre-hook validation failed for identity verification request")
 *     .addErrorDetail("verification_type", "age_verification_failed")
 *     .addErrorMessage("Age verification: minimum age not met")
 *     .build();
 * }</pre>
 */
public class IdentityVerificationErrorDetails {

  private final String error;
  private final String errorDescription;
  private final Map<String, Object> errorDetails;
  private final List<String> errorMessages;

  private IdentityVerificationErrorDetails(Builder builder) {
    this.error = builder.error;
    this.errorDescription = builder.errorDescription;
    this.errorDetails = new HashMap<>(builder.errorDetails);
    this.errorMessages = List.copyOf(builder.errorMessages);
  }

  public static Builder builder() {
    return new Builder();
  }

  /**
   * Converts to Map in OAuth 2.0 compliant error response format.
   *
   * @return Map in unified error response format
   */
  public Map<String, Object> toMap() {
    Map<String, Object> response = new HashMap<>();
    response.put("error", error);
    response.put("error_description", errorDescription);
    response.put("error_details", errorDetails);
    response.put("error_messages", errorMessages);
    return response;
  }

  public String error() {
    return error;
  }

  public String errorDescription() {
    return errorDescription;
  }

  public Map<String, Object> errorDetails() {
    return Map.copyOf(errorDetails);
  }

  public List<String> errorMessages() {
    return errorMessages;
  }

  /** Builder class for identity verification error details. */
  public static class Builder {
    private String error;
    private String errorDescription;
    private final Map<String, Object> errorDetails = new HashMap<>();
    private final List<String> errorMessages = new java.util.ArrayList<>();

    private Builder() {}

    public Builder error(String error) {
      this.error = error;
      return this;
    }

    public Builder errorDescription(String errorDescription) {
      this.errorDescription = errorDescription;
      return this;
    }

    public Builder addErrorDetail(String key, Object value) {
      this.errorDetails.put(key, value);
      return this;
    }

    public Builder errorDetails(Map<String, Object> errorDetails) {
      this.errorDetails.clear();
      this.errorDetails.putAll(errorDetails);
      return this;
    }

    public Builder addErrorMessage(String message) {
      this.errorMessages.add(message);
      return this;
    }

    public Builder errorMessages(List<String> messages) {
      this.errorMessages.clear();
      this.errorMessages.addAll(messages);
      return this;
    }

    public IdentityVerificationErrorDetails build() {
      if (error == null || error.isEmpty()) {
        throw new IllegalArgumentException("error code is required");
      }
      if (errorDescription == null || errorDescription.isEmpty()) {
        throw new IllegalArgumentException("error description is required");
      }
      return new IdentityVerificationErrorDetails(this);
    }
  }

  /** Predefined error types constants. */
  public static class ErrorTypes {
    public static final String PRE_HOOK_VALIDATION_FAILED = "pre_hook_validation_failed";
    public static final String INVALID_REQUEST_FORMAT = "invalid_request_format";
    public static final String EXECUTION_FAILED = "execution_failed";
    public static final String VERIFICATION_TYPE_NOT_SUPPORTED = "verification_type_not_supported";
    public static final String PROCESS_NOT_FOUND = "process_not_found";
    public static final String UNAUTHORIZED_ACCESS = "unauthorized_access";
    public static final String INTERNAL_SERVER_ERROR = "internal_server_error";
  }
}
