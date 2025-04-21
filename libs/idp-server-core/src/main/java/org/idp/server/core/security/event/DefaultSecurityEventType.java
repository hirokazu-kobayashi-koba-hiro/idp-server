package org.idp.server.core.security.event;

public enum DefaultSecurityEventType {
  user_signup("User signed up"),
  user_signup_failure("User signed up failure"),
  user_signup_conflict("User signed up conflict"),
  user_disabled("User disabled"),
  user_enabled("User enabled"),
  user_deletion("User deleted"),
  password_success("User successfully authenticated with a password"),
  password_failure("User failed authentication with a password"),
  webauthn_registration_challenge("User challenge registration WebAuthn"),
  webauthn_registration_success("User successfully registration WebAuthn"),
  webauthn_registration_failure("User failed registration WebAuthn"),
  webauthn_authentication_challenge("User challenge authentication using WebAuthn"),
  webauthn_authentication_success("User successfully authenticated using WebAuthn"),
  webauthn_authentication_failure("User failed authentication using WebAuthn"),
  email_verification_request("User request verified their email"),
  email_verification_success("User successfully verified their email"),
  email_verification_failure("User failed email verification"),
  legacy_authentication_success("User legacy ID authentication verified"),
  legacy_authentication_failure("User failed legacy ID authentication verified"),
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
  authentication_device_notification_success("User successfully received a device notification"),
  authentication_device_notification_cancel("User canceled a device notification"),
  authentication_device_notification_failure("User failed to receive a device notification"),
  backchannel_authentication_request_success(
      "User successfully authenticated with a backchannel authentication"),
  backchannel_authentication_request_failure(
      "User failed authentication with a backchannel authentication"),
  backchannel_authentication_authorize("User authorized with a backchannel authentication"),
  backchannel_authentication_deny("User denied with a backchannel authentication"),
  password_reset("User reset their password"),
  password_change("User changed their password"),
  application_create("Application was created"),
  application_get("Application details were retrieved"),
  application_edit("Application was updated"),
  application_delete("Application was deleted"),
  server_create("Server instance was created"),
  server_get("Server details were retrieved"),
  server_edit("Server configuration was updated"),
  server_delete("Server instance was deleted"),
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
}
