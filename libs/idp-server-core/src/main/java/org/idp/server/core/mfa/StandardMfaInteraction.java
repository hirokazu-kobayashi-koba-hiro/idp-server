package org.idp.server.core.mfa;

public enum StandardMfaInteraction {
  PASSWORD_REGISTRATION("password-registration"),
  PASSWORD_AUTHENTICATION("password-authentication"),
  EMAIL_VERIFICATION_CHALLENGE("email-verification-challenge"),
  EMAIL_VERIFICATION("email-verification"),
  WEBAUTHN_REGISTRATION_CHALLENGE("webauthn-registration-challenge"),
  WEBAUTHN_REGISTRATION("webauthn-registration"),
  WEBAUTHN_AUTHENTICATION_CHALLENGE("webauthn-authentication-challenge"),
  WEBAUTHN_AUTHENTICATION("webauthn-authentication"),
  ;

  String type;

  StandardMfaInteraction(String type) {
    this.type = type;
  }

  public MfaInteractionType toType() {
    return new MfaInteractionType(type);
  }
}
