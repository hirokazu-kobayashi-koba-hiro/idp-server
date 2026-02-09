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

/**
 * Default security event types used throughout the IdP server.
 *
 * <p>Each event type has a human-readable description and a processing mode flag ({@link
 * #synchronous}). When {@code synchronous} is {@code true}, the event is processed in the same
 * thread/transaction as the caller via {@code SecurityEventPublisher.publishSync()}, so that log
 * persistence, statistics updates, and hook execution complete before the HTTP response is
 * returned.
 *
 * <h3>Synchronous processing criteria</h3>
 *
 * An event should be marked as synchronous when <em>both</em> of the following conditions are met:
 *
 * <ol>
 *   <li>The API consumer is an <strong>administrator</strong> (Management API) or the operation is
 *       <strong>irreversible</strong> for the end-user (e.g. account deletion, identity
 *       verification approval/rejection).
 *   <li>The caller needs confirmation that event-side processing (audit log, webhook, etc.) has
 *       completed before the response is sent.
 * </ol>
 *
 * <p>All other events are processed asynchronously (fire-and-forget) via Spring's
 * {@code @Async @EventListener}.
 *
 * <h3>Discardable events</h3>
 *
 * <p>Events marked as {@link #discardable} may be silently dropped when the async thread pool is
 * saturated, instead of being queued for retry. These are read-only queries, intermediate challenge
 * steps, or informational events whose loss does not impact security audit or business logic. The
 * subsequent success/failure event captures the final outcome.
 *
 * @see org.idp.server.platform.security.SecurityEventPublisher
 * @see org.idp.server.platform.security.SecurityEvent
 */
public enum DefaultSecurityEventType {

  // User lifecycle
  user_signup("User account was created via self-registration"),
  user_signup_failure("Self-registration failed"),
  user_signup_conflict("Self-registration rejected due to duplicate account"),
  user_self_delete("User deleted their own account (irreversible)", true),

  // Password authentication
  password_success("Password authentication succeeded at login"),
  password_failure("Password authentication failed at login"),

  // FIDO UAF (biometric) registration
  fido_uaf_registration_challenge_success(
      "FIDO UAF registration challenge was issued", false, true),
  fido_uaf_registration_challenge_failure("FIDO UAF registration challenge failed to issue"),
  fido_uaf_registration_success("FIDO UAF biometric credential was registered"),
  fido_uaf_registration_failure("FIDO UAF biometric credential registration failed"),
  fido_uaf_reset_success("FIDO UAF credentials were reset"),

  // FIDO UAF authentication
  fido_uaf_authentication_challenge_success(
      "FIDO UAF authentication challenge was issued", false, true),
  fido_uaf_authentication_challenge_failure("FIDO UAF authentication challenge failed to issue"),
  fido_uaf_authentication_success("FIDO UAF biometric authentication succeeded at login"),
  fido_uaf_authentication_failure("FIDO UAF biometric authentication failed at login"),

  // FIDO UAF deregistration / cancel
  fido_uaf_deregistration_success("FIDO UAF credential was deregistered"),
  fido_uaf_deregistration_failure("FIDO UAF credential deregistration failed"),
  fido_uaf_cancel_success("FIDO UAF operation was cancelled"),
  fido_uaf_cancel_failure("FIDO UAF operation cancellation failed"),

  // FIDO2 (security key) registration
  fido2_registration_challenge_success("FIDO2 registration challenge was issued", false, true),
  fido2_registration_challenge_failure("FIDO2 registration challenge failed to issue"),
  fido2_registration_success("FIDO2 security key was registered"),
  fido2_registration_failure("FIDO2 security key registration failed"),
  fido2_reset_success("FIDO2 credentials were reset"),

  // FIDO2 authentication
  fido2_authentication_challenge_success("FIDO2 authentication challenge was issued", false, true),
  fido2_authentication_challenge_failure("FIDO2 authentication challenge failed to issue"),
  fido2_authentication_success("FIDO2 security key authentication succeeded at login"),
  fido2_authentication_failure("FIDO2 security key authentication failed at login"),

  // FIDO2 deregistration
  fido2_deregistration_success("FIDO2 security key was deregistered"),
  fido2_deregistration_failure("FIDO2 security key deregistration failed"),

  // Email verification
  email_verification_request_success("Email verification code was sent", false, true),
  email_verification_request_failure("Email verification code failed to send"),
  email_verification_success("Email verification code was verified"),
  email_verification_failure("Email verification code was rejected"),

  // SMS verification
  sms_verification_challenge_success("SMS verification code was sent", false, true),
  sms_verification_challenge_failure("SMS verification code failed to send"),
  sms_verification_success("SMS verification code was verified"),
  sms_verification_failure("SMS verification code was rejected"),

  // Legacy / external authentication
  legacy_authentication_success("Legacy authentication succeeded"),
  legacy_authentication_failure("Legacy authentication failed"),
  external_token_authentication_success("External token authentication succeeded"),
  external_token_authentication_failure("External token authentication failed"),

  // Federation (social login)
  federation_request("Redirect to external IdP was initiated"),
  federation_success("Callback from external IdP was processed successfully"),
  federation_failure("Callback from external IdP processing failed"),

  // OAuth authorization
  authorize_failure("Authorization request failed"),
  oauth_authorize("Authorization code was issued after user consent"),
  oauth_authorize_with_session("Authorization was granted using existing session"),
  oauth_authorize_with_session_expired("Authorization failed because session had expired"),
  oauth_authorize_with_session_acr_mismatch(
      "Authorization failed because session ACR did not match requested acr_values"),
  oauth_authorize_with_session_policy_mismatch(
      "Authorization failed because session did not satisfy authentication policy"),
  oauth_deny("User denied the authorization request"),

  // Token
  issue_token_success("Access token was issued"),
  issue_token_failure("Access token issuance failed"),
  refresh_token_success("Access token was refreshed"),
  refresh_token_failure("Access token refresh failed"),
  login_success("User session was created"),
  logout("User session was terminated"),
  userinfo_success("UserInfo was retrieved with access token", false, true),
  userinfo_failure("UserInfo retrieval failed"),
  inspect_token_success("Token introspection confirmed token is active", false, true),
  inspect_token_failure("Token introspection failed"),
  inspect_token_expired("Token introspection found token expired", false, true),
  revoke_token_success("Token was revoked"),
  revoke_token_failure("Token revocation failed"),

  // CIBA device notification
  authentication_device_notification_success("Push notification was delivered to user device"),
  authentication_device_notification_cancel("Push notification to user device was cancelled"),
  authentication_device_notification_failure("Push notification to user device failed"),
  authentication_device_notification_no_action_success(
      "Push notification completed with no user action required"),

  // CIBA device interaction
  authentication_device_deny_success("User denied authentication on their device"),
  authentication_device_deny_failure("User denial on device failed to process"),
  authentication_cancel_success("Authentication was cancelled"),
  authentication_cancel_failure("Authentication cancellation failed"),
  authentication_device_allow_success("User approved authentication on their device"),
  authentication_device_allow_failure("User approval on device failed to process"),
  authentication_device_binding_message_success("Binding message was verified on device"),
  authentication_device_binding_message_failure("Binding message verification failed on device"),

  // Authentication device registration
  authentication_device_registration_success("Authentication device was registered"),
  authentication_device_registration_failure("Authentication device registration failed"),
  authentication_device_deregistration_success("Authentication device was deregistered"),
  authentication_device_deregistration_failure("Authentication device deregistration failed"),
  authentication_device_registration_challenge_success(
      "Authentication device registration challenge was issued", false, true),
  authentication_device_log("Client application sent device log", false, true),

  // CIBA backchannel authentication
  backchannel_authentication_request_success("CIBA authentication request was accepted"),
  backchannel_authentication_request_failure("CIBA authentication request was rejected"),
  backchannel_authentication_authorize("User authorized the CIBA authentication request"),
  backchannel_authentication_deny("User denied the CIBA authentication request"),

  // End-user password management
  password_reset("Password was reset via recovery flow"),
  password_reset_success("Password reset succeeded"),
  password_reset_failure("Password reset failed"),
  password_change("Administrator changed user password", true),
  password_change_success("User changed their own password"),
  password_change_failure("User password change failed"),

  // Identity verification (eKYC)
  identity_verification_application_apply("Identity verification application was submitted"),
  identity_verification_application_failure("Identity verification application processing failed"),
  identity_verification_application_cancel("Identity verification application was cancelled"),
  identity_verification_application_delete("Identity verification application was deleted"),
  identity_verification_application_findList(
      "Identity verification applications were listed", false, true),
  identity_verification_application_approved(
      "Identity verification was approved, user trust level elevated (irreversible)", true),
  identity_verification_application_rejected(
      "Identity verification was rejected (irreversible)", true),
  identity_verification_application_cancelled(
      "Identity verification was cancelled by external service"),
  identity_verification_result_findList("Identity verification results were listed", false, true),

  // Management API - user
  user_create("User account was created by administrator", true),
  user_get("User details were retrieved by administrator", false, true),
  user_edit("User details were updated by administrator", true),
  user_lock("User account was locked"),
  user_delete("User account was deleted by administrator (irreversible)", true);

  String description;
  boolean synchronous;
  boolean discardable;

  DefaultSecurityEventType(String description) {
    this.description = description;
    this.synchronous = false;
    this.discardable = false;
  }

  DefaultSecurityEventType(String description, boolean synchronous) {
    this.description = description;
    this.synchronous = synchronous;
    this.discardable = false;
  }

  DefaultSecurityEventType(String description, boolean synchronous, boolean discardable) {
    this.description = description;
    this.synchronous = synchronous;
    this.discardable = discardable;
  }

  public SecurityEventType toEventType() {
    return new SecurityEventType(name());
  }

  public SecurityEventDescription toEventDescription() {
    return new SecurityEventDescription(description);
  }

  public boolean isSynchronous() {
    return synchronous;
  }

  /**
   * Check if this event can be silently discarded when the async thread pool is saturated.
   *
   * <p>Discardable events are read-only queries, intermediate challenge steps, or informational
   * events. Their loss does not affect security audit completeness or business logic because the
   * subsequent success/failure event captures the final outcome.
   *
   * @return true if this event may be dropped under back-pressure
   */
  public boolean isDiscardable() {
    return discardable;
  }

  public boolean isIdentifyUserEventType() {
    return this == user_signup || this == password_success;
  }

  /**
   * Check if this event type should count as active user activity.
   *
   * <p>Used for DAU/MAU tracking. Events that indicate successful user authentication or login
   * should return true.
   *
   * @return true if this event type indicates active user activity
   */
  public boolean isActiveUserEvent() {
    return this == login_success
        || this == issue_token_success
        || this == refresh_token_success
        || this == inspect_token_success;
  }

  /**
   * Find DefaultSecurityEventType by event type value.
   *
   * @param value the event type string value
   * @return the matching DefaultSecurityEventType, or null if not found
   */
  public static DefaultSecurityEventType findByValue(String value) {
    for (DefaultSecurityEventType type : values()) {
      if (type.name().equals(value)) {
        return type;
      }
    }
    return null;
  }
}
