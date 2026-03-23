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

package org.idp.server.core.openid.identity.authentication;

/** Password policy validation result. */
public class PasswordPolicyValidationResult {

  private final boolean valid;
  private final String errorMessage;

  private PasswordPolicyValidationResult(boolean valid, String errorMessage) {
    this.valid = valid;
    this.errorMessage = errorMessage;
  }

  public static PasswordPolicyValidationResult valid() {
    return new PasswordPolicyValidationResult(true, null);
  }

  public static PasswordPolicyValidationResult invalid(String errorMessage) {
    return new PasswordPolicyValidationResult(false, errorMessage);
  }

  public boolean isValid() {
    return valid;
  }

  public boolean isInvalid() {
    return !valid;
  }

  public String errorMessage() {
    return errorMessage;
  }
}
