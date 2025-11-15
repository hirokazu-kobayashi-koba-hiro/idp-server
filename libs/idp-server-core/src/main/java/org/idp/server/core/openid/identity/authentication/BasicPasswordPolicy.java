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

import org.idp.server.platform.multi_tenancy.tenant.policy.PasswordPolicyConfig;

/**
 * Basic password policy validator.
 *
 * <p>Implements password requirements based on tenant configuration and OWASP/NIST guidelines.
 *
 * @see <a href="https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html">
 *     OWASP Password Storage Cheat Sheet</a>
 * @see <a href="https://pages.nist.gov/800-63-3/sp800-63b.html">NIST SP 800-63B</a>
 */
public class BasicPasswordPolicy {

  private final PasswordPolicyConfig config;

  public BasicPasswordPolicy() {
    this.config = PasswordPolicyConfig.defaultPolicy();
  }

  public BasicPasswordPolicy(PasswordPolicyConfig config) {
    this.config = config != null ? config : PasswordPolicyConfig.defaultPolicy();
  }

  public PasswordPolicyValidationResult validate(String password) {
    if (password == null || password.isEmpty()) {
      return PasswordPolicyValidationResult.invalid("Password is required.");
    }

    // Minimum length check
    if (password.length() < config.minLength()) {
      return PasswordPolicyValidationResult.invalid(
          String.format("Password must be at least %d characters long.", config.minLength()));
    }

    // Uppercase check
    if (config.requireUppercase() && !password.matches(".*[A-Z].*")) {
      return PasswordPolicyValidationResult.invalid(
          "Password must contain at least one uppercase letter.");
    }

    // Number check
    if (config.requireNumber() && !password.matches(".*[0-9].*")) {
      return PasswordPolicyValidationResult.invalid("Password must contain at least one number.");
    }

    // Special character check
    if (config.requireSpecialChar() && !password.matches(".*[!@#$%^&*(),.?\":{}|<>].*")) {
      return PasswordPolicyValidationResult.invalid(
          "Password must contain at least one special character.");
    }

    // TODO: Future enhancements (Issue #741 Phase 2)
    // - Check password history (config.maxHistory())
    // - Check common weak passwords

    return PasswordPolicyValidationResult.valid();
  }
}
