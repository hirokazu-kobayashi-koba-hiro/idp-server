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

package org.idp.server.platform.exception;

/**
 * Exception thrown when malicious input is detected in request parameters.
 *
 * <p>This exception is used for security-related input validation failures such as:
 *
 * <ul>
 *   <li>Path traversal attempts (../)
 *   <li>URL scheme injection (http://, javascript:)
 *   <li>CRLF injection (\r\n)
 *   <li>Script/command injection characters
 * </ul>
 *
 * <p>The exception handler should:
 *
 * <ul>
 *   <li>Log attack details at WARN level (server-side only)
 *   <li>Return generic error message to client (no details)
 *   <li>Return HTTP 400 Bad Request
 * </ul>
 */
public class MaliciousInputException extends RuntimeException {

  private final String attackType;
  private final String inputValue;

  public MaliciousInputException(String attackType, String inputValue) {
    super(String.format("%s detected: %s", attackType, inputValue));
    this.attackType = attackType;
    this.inputValue = inputValue;
  }

  public MaliciousInputException(String attackType, String inputValue, String message) {
    super(message);
    this.attackType = attackType;
    this.inputValue = inputValue;
  }

  public String attackType() {
    return attackType;
  }

  public String inputValue() {
    return inputValue;
  }

  public static MaliciousInputException pathTraversal(String value) {
    return new MaliciousInputException("path_traversal", value);
  }

  public static MaliciousInputException urlSchemeInjection(String value) {
    return new MaliciousInputException("url_scheme_injection", value);
  }

  public static MaliciousInputException crlfInjection(String value) {
    return new MaliciousInputException("crlf_injection", value);
  }

  public static MaliciousInputException unsafeCharacters(String value) {
    return new MaliciousInputException("unsafe_characters", value);
  }
}
