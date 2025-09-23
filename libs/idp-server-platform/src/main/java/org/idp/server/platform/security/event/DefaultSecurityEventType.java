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

package org.idp.server.platform.security.event;

public enum DefaultSecurityEventType {
  user_signup("User signed up"),
  user_signup_failure("User signed up failure"),
  user_signup_conflict("User signed up conflict"),
  user_disabled("User disabled"),
  user_enabled("User enabled"),
  user_deletion("User deleted"),
  password_success("User successfully authenticated with a password"),
  password_failure("User failed authentication with a password"),
  fido_uaf_registration_challenge_success("User challenge registration fido-uaf"),
  fido_uaf_registration_challenge_failure("User challenge failed registration fido-uaf"),
  fido_uaf_registration_success("User successfully registration fido-uaf"),
  fido_uaf_reset_success("User successfully reset fido-uaf"),
  fido_uaf_registration_failure("User failed registration fido-uaf"),
  fido_uaf_authentication_challenge_success(
      "User challenge authentication using fido-uaf is success"),
  fido_uaf_authentication_challenge_failure("User challenge authentication using fido-uaf is "),
  fido_uaf_authentication_success("User successfully authenticated using fido-uaf"),
  fido_uaf_authentication_failure("User failed authentication using fido-uaf"),
  fido_uaf_deregistration_success("User successfully deregistration fido-uaf"),
  fido_uaf_deregistration_failure("User failed deregistration fido-uaf"),
  fido_uaf_cancel_success("User successfully cancel fido-uaf"),
  fido_uaf_cancel_failure("User failed to cancel fido-uaf"),
  webauthn_registration_challenge_success("User successfully challenge registration WebAuthn"),
  webauthn_registration_challenge_failure("User failed challenge registration WebAuthn"),
  webauthn_registration_success("User successfully registration WebAuthn"),
  webauthn_registration_failure("User failed registration WebAuthn"),
  webauthn_authentication_challenge_success(
      "User successfully challenge authentication using WebAuthn"),
  webauthn_authentication_challenge_failure("User failed challenge authentication using WebAuthn"),
  webauthn_authentication_success("User successfully authenticated using WebAuthn"),
  webauthn_authentication_failure("User failed authentication using WebAuthn"),
  email_verification_request_success("User request verified their email is success"),
  email_verification_request_failure("User request verified their email is failed"),
  email_verification_success("User successfully verified their email"),
  email_verification_failure("User failed email verification"),
  sms_verification_challenge_success("User request verified their phone number is success"),
  sms_verification_challenge_failure("User request verified their phone number is success"),
  sms_verification_success("User successfully verified their phone number"),
  sms_verification_failure("User failed phone number verification"),
  legacy_authentication_success("User legacy ID authentication verified"),
  legacy_authentication_failure("User failed legacy ID authentication verified"),
  external_token_authentication_success("User request verified their token is success"),
  external_token_authentication_failure("User request verified their token is failed"),
  federation_request("Federation request"),
  federation_success("Federation success"),
  federation_failure("Federation failed"),
  authorize_failure("Authorize failure"),
  oauth_authorize("OAuth Authorize success"),
  oauth_authorize_with_session("OAuth Authorize success with a session"),
  oauth_deny("OAuth Deny success"),
  issue_token_success("Issue token success"),
  issue_token_failure("Issue token failure"),
  refresh_token_success("Refresh token success"),
  refresh_token_failure("Refresh token failure"),
  login_success("User logged in"),
  logout("User logged out"),
  userinfo_success("Userinfo success"),
  userinfo_failure("Userinfo failure"),
  inspect_token_success("Inspect token success"),
  inspect_token_failure("Inspect token failure"),
  inspect_token_expired("Inspect token expired"),
  revoke_token_success("Revoke token success"),
  revoke_token_failure("Revoke token failure"),
  authentication_device_notification_success("User successfully received a device notification"),
  authentication_device_notification_cancel("User canceled a device notification"),
  authentication_device_notification_failure("User failed to receive a device notification"),
  authentication_device_notification_no_action_success(
      "Authentication device notification is no action"),
  authentication_device_deny_success("User successfully denied a device authentication"),
  authentication_device_deny_failure("User failed to deny a device authentication"),
  authentication_cancel_success("User successfully cancel a authentication"),
  authentication_cancel_failure("User failed to cancel a authentication"),
  authentication_device_allow_success("User successfully allowed a device notification"),
  authentication_device_allow_failure("User failed to allow a device notification"),
  authentication_device_binding_message_success("User successfully binding a device notification"),
  authentication_device_binding_message_failure("User failed to bind a device notification"),
  authentication_device_registration_success("User successfully registered a device"),
  authentication_device_registration_failure("User failed to register a device"),
  authentication_device_deregistration_success("User successfully deregistered a device"),
  authentication_device_deregistration_failure("User failed to deregister a device"),
  authentication_device_registration_challenge_success(
      "User successfully challenge a device registration"),
  backchannel_authentication_request_success(
      "User successfully authenticated with a backchannel authentication"),
  backchannel_authentication_request_failure(
      "User failed authentication with a backchannel authentication"),
  backchannel_authentication_authorize("User authorized with a backchannel authentication"),
  backchannel_authentication_deny("User denied with a backchannel authentication"),
  password_reset("User reset their password"),
  password_change("User changed their password"),
  identity_verification_application_apply("identity verification application was applied"),
  identity_verification_application_failure("identity verification application was failed"),
  identity_verification_application_cancel("identity verification application was canceled"),
  identity_verification_application_delete("identity verification application was deleted"),
  identity_verification_application_findList("identity verification application was foundList"),
  identity_verification_application_approved("identity verification application was approved"),
  identity_verification_application_rejected("identity verification application was rejected"),
  identity_verification_application_cancelled("identity verification application was cancelled"),
  identity_verification_result_findList("identity verification result was foundList"),
  server_create("Server instance was created"),
  server_get("Server details were retrieved"),
  server_edit("Server configuration was updated"),
  server_delete("Server instance was deleted"),
  application_create("Server instance was created"),
  application_get("Server details were retrieved"),
  application_edit("Server configuration was updated"),
  application_delete("Server instance was deleted"),
  user_create("New user account was created"),
  user_get("User details were retrieved"),
  user_edit("User details were updated"),
  user_lock("User account was locked"),
  user_delete("User account was deleted"),
  member_invite("A member was invited to the organization"),
  member_join("A member joined the organization"),
  member_leave("A member left the organization");

  String description;

  DefaultSecurityEventType(String description) {
    this.description = description;
  }

  public SecurityEventType toEventType() {
    return new SecurityEventType(name());
  }

  public SecurityEventDescription toEventDescription() {
    return new SecurityEventDescription(description);
  }

  public boolean isIdentifyUserEventType() {
    return this == user_signup || this == password_success;
  }
}
