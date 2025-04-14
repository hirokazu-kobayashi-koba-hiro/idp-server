package org.idp.server.core.security.event;

public enum DefaultSecurityEventType {
  user_signup("User signed up"),
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
  login("User logged in"),
  login_with_session("User logged in with a session"),
  logout("User logged out"),
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
  member_leave("A member left the organization"),
  subscription_start("A new subscription started"),
  subscription_suspend("A subscription was suspended"),
  subscription_end("A subscription ended"),
  subscription_change("Subscription details were changed"),
  subscription_add("An additional subscription was added"),
  ;

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
