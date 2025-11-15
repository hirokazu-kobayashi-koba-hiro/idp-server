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

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.tenant.policy.PasswordPolicyConfig;

/**
 * Password policy validator.
 *
 * <p>Implements password requirements based on tenant configuration and OWASP/NIST guidelines.
 *
 * @see <a href="https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html">
 *     OWASP Password Storage Cheat Sheet</a>
 * @see <a href="https://pages.nist.gov/800-63-3/sp800-63b.html">NIST SP 800-63B</a>
 */
public class PasswordPolicyValidator {

  private static final LoggerWrapper log = LoggerWrapper.getLogger(PasswordPolicyValidator.class);
  private final PasswordPolicyConfig config;

  public PasswordPolicyValidator() {
    this.config = PasswordPolicyConfig.defaultPolicy();
  }

  public PasswordPolicyValidator(PasswordPolicyConfig config) {
    this.config = config != null ? config : PasswordPolicyConfig.defaultPolicy();
  }

  public PasswordPolicyValidationResult validate(String password) {
    log.debug(
        "Validating password: length={}, minLength={}, maxLength={}, requireUppercase={}, requireLowercase={}, requireNumber={}, requireSpecialChar={}, hasCustomRegex={}",
        password != null ? password.length() : 0,
        config.minLength(),
        config.maxLength(),
        config.requireUppercase(),
        config.requireLowercase(),
        config.requireNumber(),
        config.requireSpecialChar(),
        config.customRegex() != null && !config.customRegex().isEmpty());

    if (password == null || password.isEmpty()) {
      log.debug("Password validation failed: password is null or empty");
      return PasswordPolicyValidationResult.invalid("Password is required.");
    }

    // Whitespace-only password check
    if (password.trim().isEmpty()) {
      log.debug("Password validation failed: password contains only whitespace");
      return PasswordPolicyValidationResult.invalid("Password is required.");
    }

    // Minimum length check
    if (password.length() < config.minLength()) {
      log.debug(
          "Password validation failed: length {} < minLength {}",
          password.length(),
          config.minLength());
      return PasswordPolicyValidationResult.invalid(
          String.format("Password must be at least %d characters long.", config.minLength()));
    }

    // Maximum length check (BCrypt limitation: 72 bytes)
    if (password.length() > config.maxLength()) {
      log.debug(
          "Password validation failed: length {} > maxLength {}",
          password.length(),
          config.maxLength());
      return PasswordPolicyValidationResult.invalid(
          String.format("Password must not exceed %d characters.", config.maxLength()));
    }

    // Uppercase check
    if (config.requireUppercase() && !password.matches(".*[A-Z].*")) {
      log.debug("Password validation failed: no uppercase letter found");
      return PasswordPolicyValidationResult.invalid(
          "Password must contain at least one uppercase letter.");
    }

    // Lowercase check
    if (config.requireLowercase() && !password.matches(".*[a-z].*")) {
      log.debug("Password validation failed: no lowercase letter found");
      return PasswordPolicyValidationResult.invalid(
          "Password must contain at least one lowercase letter.");
    }

    // Number check
    if (config.requireNumber() && !password.matches(".*[0-9].*")) {
      log.debug("Password validation failed: no number found");
      return PasswordPolicyValidationResult.invalid("Password must contain at least one number.");
    }

    // Special character check
    if (config.requireSpecialChar() && !password.matches(".*[!@#$%^&*(),.?\":{}|<>].*")) {
      log.debug("Password validation failed: no special character found");
      return PasswordPolicyValidationResult.invalid(
          "Password must contain at least one special character.");
    }

    // Custom regex check
    if (config.customRegex() != null && !config.customRegex().isEmpty()) {
      try {
        Pattern pattern = Pattern.compile(config.customRegex());
        if (!pattern.matcher(password).matches()) {
          log.debug("Password validation failed: custom regex pattern not matched");
          String errorMessage = config.customRegexErrorMessage();
          if (errorMessage == null || errorMessage.isEmpty()) {
            errorMessage = "Password does not match the required pattern.";
          }
          return PasswordPolicyValidationResult.invalid(errorMessage);
        }
      } catch (PatternSyntaxException e) {
        // Invalid regex pattern in configuration - treat as validation failure
        log.warn(
            "Password policy configuration error: invalid regex pattern: {}",
            config.customRegex(),
            e);
        return PasswordPolicyValidationResult.invalid(
            "Password policy configuration error: invalid regex pattern.");
      }
    }

    // TODO: Future enhancements (Issue #741 Phase 2)
    // - Check password history (config.maxHistory())
    // - Check common weak passwords

    log.debug("Password validation succeeded");
    return PasswordPolicyValidationResult.valid();
  }
}
