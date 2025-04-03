package org.idp.server.core.mfa.email;

import java.util.Objects;

public class EmailVerificationCodeVerifier {
  String input;
  EmailVerificationChallenge challenge;

  public EmailVerificationCodeVerifier(String input, EmailVerificationChallenge challenge) {
    this.input = input;
    this.challenge = challenge;
  }

  public void verify() {
    String verificationCode = challenge.verificationCode();
    if (!Objects.equals(verificationCode, input)) {
      throw new EmailVerificationCodeUnmatchException("verification code is incorrect");
    }
  }
}
