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

package org.idp.server.core.openid.authentication.policy;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.platform.json.JsonReadable;

/**
 * Authentication step definition in a multi-step authentication flow.
 *
 * <p>Defines a single authentication step with 1st/2nd factor distinction following Keycloak
 * pattern.
 *
 * <h2>1st Factor vs 2nd Factor</h2>
 *
 * <ul>
 *   <li><b>1st Factor (requiresUser=false)</b>: User identification phase
 *       <ul>
 *         <li>User is identified by input identifier (email, phone_number, username)
 *         <li>New user registration may be allowed
 *         <li>Examples: Email/SMS challenge, Password, WebAuthn registration
 *       </ul>
 *   <li><b>2nd Factor (requiresUser=true)</b>: Authentication verification phase
 *       <ul>
 *         <li>User must already be identified
 *         <li>Verifies the identified user
 *         <li>User cannot be changed
 *         <li>Examples: OTP verification, FIDO UAF authentication
 *       </ul>
 * </ul>
 *
 * @see <a
 *     href="https://www.keycloak.org/docs-api/latest/javadocs/org/keycloak/authentication/Authenticator.html#requiresUser()">Keycloak
 *     Authenticator.requiresUser()</a>
 */
public class AuthenticationStepDefinition implements JsonReadable {
  String method;
  int order;

  // 1st/2nd factor control
  boolean requiresUser = true; // default: 2nd factor
  boolean allowRegistration = false; // default: no registration
  String userIdentitySource; // "email", "phone_number", "username"
  String verificationSource; // verification identifier
  String registrationMode = "allowed"; // "allowed", "required", "disabled"

  public AuthenticationStepDefinition() {}

  public AuthenticationStepDefinition(String method, int order) {
    this.method = method;
    this.order = order;
  }

  public String authenticationMethod() {
    return method;
  }

  public int order() {
    return order;
  }

  /**
   * Whether this step requires user to be already identified.
   *
   * <p>Follows Keycloak SPI pattern.
   *
   * @return true if user must be identified (2nd factor), false if this step identifies user (1st
   *     factor)
   */
  public boolean requiresUser() {
    return requiresUser;
  }

  /**
   * Whether new user registration is allowed in this step.
   *
   * <p>Only applicable when requiresUser=false (1st factor).
   *
   * @return true if registration is allowed
   */
  public boolean allowRegistration() {
    return allowRegistration;
  }

  /**
   * The user field used for identification.
   *
   * <p>Examples: "email", "phone_number", "username", "webauthn_credential"
   *
   * @return identity source field name
   */
  public String userIdentitySource() {
    return userIdentitySource;
  }

  /**
   * Check if userIdentitySource is configured.
   *
   * @return true if userIdentitySource is set
   */
  public boolean hasUserIdentitySource() {
    return userIdentitySource != null && !userIdentitySource.isEmpty();
  }

  /**
   * The field used for verification in 2nd factor.
   *
   * <p>Only applicable when requiresUser=true (2nd factor).
   *
   * @return verification source field name
   */
  public String verificationSource() {
    return verificationSource;
  }

  /**
   * Check if verificationSource is configured.
   *
   * @return true if verificationSource is set
   */
  public boolean hasVerificationSource() {
    return verificationSource != null && !verificationSource.isEmpty();
  }

  /**
   * Registration mode control.
   *
   * <p>Values:
   *
   * <ul>
   *   <li>"allowed" (default): Both existing and new users allowed
   *   <li>"required": Only new users allowed (registration flow)
   *   <li>"disabled": Only existing users allowed (authentication flow)
   * </ul>
   *
   * @return registration mode
   */
  public String registrationMode() {
    return registrationMode != null ? registrationMode : "allowed";
  }

  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    map.put("method", method);
    map.put("order", order);
    map.put("requires_user", requiresUser);
    map.put("allow_registration", allowRegistration);
    map.put("registration_mode", registrationMode());
    if (hasUserIdentitySource()) {
      map.put("user_identity_source", userIdentitySource);
    }
    if (hasVerificationSource()) {
      map.put("verification_source", verificationSource);
    }
    return map;
  }
}
