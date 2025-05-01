package org.idp.server.core.authentication;

public enum StandardAuthenticationInteraction {
  PASSWORD_REGISTRATION("password-registration"),
  PASSWORD_AUTHENTICATION("password-authentication"),
  EMAIL_VERIFICATION_CHALLENGE("email-verification-challenge"),
  EMAIL_VERIFICATION("email-verification"),
  FIDO_UAF_REGISTRATION_CHALLENGE("fido-uaf-registration-challenge"),
  FIDO_UAF_REGISTRATION("fido-uaf-registration"),
  FIDO_UAF_AUTHENTICATION_CHALLENGE("fido-uaf-authentication-challenge"),
  FIDO_UAF_AUTHENTICATION("fido-uaf-authentication"),
  WEBAUTHN_REGISTRATION_CHALLENGE("webauthn-registration-challenge"),
  WEBAUTHN_REGISTRATION("webauthn-registration"),
  WEBAUTHN_AUTHENTICATION_CHALLENGE("webauthn-authentication-challenge"),
  WEBAUTHN_AUTHENTICATION("webauthn-authentication"),
  AUTHENTICATION_DEVICE_NOTIFICATION("authentication-device-notification");

  String type;

  StandardAuthenticationInteraction(String type) {
    this.type = type;
  }

  public AuthenticationInteractionType toType() {
    return new AuthenticationInteractionType(type);
  }
}
