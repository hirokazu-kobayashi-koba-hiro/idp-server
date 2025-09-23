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

package org.idp.server.core.extension.identity.verification.configuration;

/**
 * Error handling strategy for identity verification operations.
 *
 * <p>Defines how the system should respond when errors occur during identity verification
 * processes, particularly in pre-hook phases such as additional parameter resolution.
 */
public enum ErrorHandlingStrategy {
  /** Fail fast on error - stop processing immediately (default, document compliant) */
  FAIL_FAST,
  /** Resilient on error - continue processing with fallback values */
  RESILIENT;

  /**
   * Create ErrorHandlingStrategy from string value.
   *
   * @param value the string representation of the strategy
   * @return the corresponding ErrorHandlingStrategy, defaults to FAIL_FAST if null or invalid
   */
  public static ErrorHandlingStrategy of(String value) {
    if (value == null) {
      return FAIL_FAST; // Default
    }
    for (ErrorHandlingStrategy strategy : ErrorHandlingStrategy.values()) {
      if (strategy.name().equalsIgnoreCase(value)) {
        return strategy;
      }
    }
    return FAIL_FAST; // Fallback to safe default
  }
}
