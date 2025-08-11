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

package org.idp.server.core.openid.authentication;

public enum StandardAuthenticationInteraction {
  INITIAL_REGISTRATION("initial-registration"),
  PASSWORD_AUTHENTICATION("password-authentication"),
  SMS_AUTHENTICATION_CHALLENGE("sms-authentication-challenge"),
  SMS_AUTHENTICATION("sms-authentication"),
  EMAIL_AUTHENTICATION_CHALLENGE("email-authentication-challenge"),
  EMAIL_AUTHENTICATION("email-authentication"),
  FIDO_UAF_REGISTRATION_CHALLENGE("fido-uaf-registration-challenge"),
  FIDO_UAF_REGISTRATION("fido-uaf-registration"),
  FIDO_UAF_AUTHENTICATION_CHALLENGE("fido-uaf-authentication-challenge"),
  FIDO_UAF_AUTHENTICATION("fido-uaf-authentication"),
  FIDO_UAF_CANCEL("fido-uaf-cancel"),
  FIDO_UAF_DEREGISTRATION("fido-uaf-deregistration"),
  WEBAUTHN_REGISTRATION_CHALLENGE("webauthn-registration-challenge"),
  WEBAUTHN_REGISTRATION("webauthn-registration"),
  WEBAUTHN_AUTHENTICATION_CHALLENGE("webauthn-authentication-challenge"),
  WEBAUTHN_AUTHENTICATION("webauthn-authentication"),
  WEBAUTHN_DEREGISTRATION("webauthn-deregistration"),
  AUTHENTICATION_CANCEL("authentication-cancel"),
  AUTHENTICATION_DEVICE_NOTIFICATION("authentication-device-notification"),
  AUTHENTICATION_DEVICE_DENY("authentication-device-deny"),
  AUTHENTICATION_DEVICE_BINDING_MESSAGE("authentication-device-binding-message"),
  ;

  String type;

  StandardAuthenticationInteraction(String type) {
    this.type = type;
  }

  public AuthenticationInteractionType toType() {
    return new AuthenticationInteractionType(type);
  }
}
