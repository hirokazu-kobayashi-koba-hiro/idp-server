package org.idp.server.domain.model.authentication;

import java.util.Objects;

public class EmailVerificationChallenge {

  EmailVerificationIdentifier identifier;
  OneTimePassword oneTimePassword;

  public EmailVerificationChallenge(
      EmailVerificationIdentifier identifier, OneTimePassword oneTimePassword) {
    this.identifier = identifier;
    this.oneTimePassword = oneTimePassword;
  }

  public EmailVerificationIdentifier identifier() {
    return identifier;
  }

  public String verificationCode() {
    return oneTimePassword.value();
  }

  public boolean exists() {
    return Objects.nonNull(identifier) && identifier.exists();
  }
}
