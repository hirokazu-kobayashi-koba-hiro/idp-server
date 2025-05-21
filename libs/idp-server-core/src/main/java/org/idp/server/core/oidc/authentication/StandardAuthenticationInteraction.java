package org.idp.server.core.oidc.authentication;

public enum StandardAuthenticationInteraction {
  PASSWORD_REGISTRATION("password-registration"),
  PASSWORD_AUTHENTICATION("password-authentication"),
  SMS_AUTHENTICATION_REGISTRATION_CHALLENGE("sms-authentication-registration-challenge"),
  SMS_AUTHENTICATION_REGISTRATION("sms-authentication-registration"),
  SMS_AUTHENTICATION_CHALLENGE("sms-authentication-challenge"),
  SMS_AUTHENTICATION("sms-authentication"),
  EMAIL_AUTHENTICATION_CHALLENGE("email-authentication-challenge"),
  EMAIL_AUTHENTICATION("email-authentication"),
  FIDO_UAF_REGISTRATION_CHALLENGE("fido-uaf-registration-challenge"),
  FIDO_UAF_REGISTRATION("fido-uaf-registration"),
  FIDO_UAF_AUTHENTICATION_CHALLENGE("fido-uaf-authentication-challenge"),
  FIDO_UAF_AUTHENTICATION("fido-uaf-authentication"),
  WEBAUTHN_REGISTRATION_CHALLENGE("webauthn-registration-challenge"),
  WEBAUTHN_REGISTRATION("webauthn-registration"),
  WEBAUTHN_AUTHENTICATION_CHALLENGE("webauthn-authentication-challenge"),
  WEBAUTHN_AUTHENTICATION("webauthn-authentication"),
  AUTHENTICATION_DEVICE_NOTIFICATION("authentication-device-notification"),
  AUTHENTICATION_DEVICE_DENY("authentication-device-deny"),
  ;

  String type;

  StandardAuthenticationInteraction(String type) {
    this.type = type;
  }

  public AuthenticationInteractionType toType() {
    return new AuthenticationInteractionType(type);
  }
}
