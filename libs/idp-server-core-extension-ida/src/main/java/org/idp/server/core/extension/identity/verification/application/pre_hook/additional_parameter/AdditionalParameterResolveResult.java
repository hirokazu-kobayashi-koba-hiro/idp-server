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

package org.idp.server.core.extension.identity.verification.application.pre_hook.additional_parameter;

import java.util.Map;
import org.idp.server.core.extension.identity.verification.io.IdentityVerificationErrorDetails;

/**
 * Result class for additional parameter resolution.
 *
 * <p>Represents the outcome of an additional parameter resolver operation, supporting both success
 * and error cases with appropriate error handling strategies.
 */
public class AdditionalParameterResolveResult {

  private final boolean success;
  private final Map<String, Object> data;
  private final IdentityVerificationErrorDetails errorDetails;
  private final boolean failFast;

  private AdditionalParameterResolveResult(
      boolean success,
      Map<String, Object> data,
      IdentityVerificationErrorDetails errorDetails,
      boolean failFast) {
    this.success = success;
    this.data = data;
    this.errorDetails = errorDetails;
    this.failFast = failFast;
  }

  /**
   * Create a successful result.
   *
   * @param data the resolved parameter data
   * @return successful result
   */
  public static AdditionalParameterResolveResult success(Map<String, Object> data) {
    return new AdditionalParameterResolveResult(true, data, null, false);
  }

  /**
   * Create a resilient error result (processing continues with fallback data).
   *
   * @param errorDetails error details with fallback data
   * @return resilient error result
   */
  public static AdditionalParameterResolveResult resilientError(
      IdentityVerificationErrorDetails errorDetails, Map<String, Object> fallbackData) {
    return new AdditionalParameterResolveResult(false, fallbackData, errorDetails, false);
  }

  /**
   * Create a fail-fast error result (processing should stop immediately).
   *
   * @param errorDetails error details
   * @return fail-fast error result
   */
  public static AdditionalParameterResolveResult failFastError(
      IdentityVerificationErrorDetails errorDetails) {
    return new AdditionalParameterResolveResult(false, null, errorDetails, true);
  }

  /**
   * Check if the result is successful.
   *
   * @return true if successful
   */
  public boolean isSuccess() {
    return success;
  }

  /**
   * Check if this is a fail-fast error that should stop processing.
   *
   * @return true if fail-fast error
   */
  public boolean isFailFast() {
    return !success && failFast;
  }

  /**
   * Check if this is a resilient error that allows processing to continue.
   *
   * @return true if resilient error
   */
  public boolean isResilientError() {
    return !success && !failFast;
  }

  /**
   * Get the resolved data (or fallback data in case of resilient error).
   *
   * @return parameter data
   */
  public Map<String, Object> getData() {
    return data;
  }

  /**
   * Get error details (null for successful results).
   *
   * @return error details
   */
  public IdentityVerificationErrorDetails getErrorDetails() {
    return errorDetails;
  }
}
