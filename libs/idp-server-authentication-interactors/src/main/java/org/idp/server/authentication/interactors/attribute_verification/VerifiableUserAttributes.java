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

package org.idp.server.authentication.interactors.attribute_verification;

import java.util.Map;
import java.util.Set;
import org.idp.server.core.openid.identity.User;

/**
 * The user attributes an attribute verification may compare against (Issue #1907).
 *
 * <p>A third allow list, separate from the one policy conditions read ({@code
 * PolicyEvaluationUserContextCreator}) and the one sent to external APIs ({@code
 * ExternalRequestUserContextCreator}). Its risk is different from both: the value never leaves the
 * process and is never shown, but every comparison tells the caller whether a guess was right. So
 * it holds only what an end-user can reasonably be asked to recall, and not {@code
 * verified_claims}, credentials or anything the end-user is not expected to know.
 *
 * <p>Fail-safe like the others: a name not listed here resolves to nothing.
 */
public class VerifiableUserAttributes {

  static final Set<String> NAMES =
      Set.of(
          "birthdate",
          "phone_number",
          "email",
          "name",
          "given_name",
          "family_name",
          "address.postal_code");

  static final String CUSTOM_PROPERTIES_PREFIX = "custom_properties.";

  User user;

  public VerifiableUserAttributes(User user) {
    this.user = user;
  }

  /** Whether {@code name} is one an attribute verification may be configured to compare against. */
  public static boolean isVerifiable(String name) {
    if (name == null) {
      return false;
    }
    if (NAMES.contains(name)) {
      return true;
    }
    return name.startsWith(CUSTOM_PROPERTIES_PREFIX)
        && name.length() > CUSTOM_PROPERTIES_PREFIX.length();
  }

  /**
   * @return the registered value, or null when the user has none or the name is not verifiable.
   *     Only scalar custom properties resolve; a nested object or list does not.
   */
  public String valueOf(String name) {
    if (user == null || !user.exists() || !isVerifiable(name)) {
      return null;
    }
    return switch (name) {
      case "birthdate" -> user.hasBirthdate() ? user.birthdate() : null;
      case "phone_number" -> user.hasPhoneNumber() ? user.phoneNumber() : null;
      case "email" -> user.hasEmail() ? user.email() : null;
      case "name" -> user.hasName() ? user.name() : null;
      case "given_name" -> user.hasGivenName() ? user.givenName() : null;
      case "family_name" -> user.hasFamilyName() ? user.familyName() : null;
      case "address.postal_code" ->
          user.hasAddress() && user.address().hasPostalCode() ? user.address().postalCode() : null;
      default -> customProperty(name.substring(CUSTOM_PROPERTIES_PREFIX.length()));
    };
  }

  private String customProperty(String key) {
    if (!user.hasCustomProperties()) {
      return null;
    }
    Map<String, Object> customProperties = user.customPropertiesValue();
    Object value = customProperties.get(key);
    if (value instanceof String || value instanceof Number || value instanceof Boolean) {
      return String.valueOf(value);
    }
    return null;
  }
}
