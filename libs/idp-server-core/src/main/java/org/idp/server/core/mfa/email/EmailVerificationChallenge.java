package org.idp.server.core.mfa.email;

import java.io.Serializable;
import java.util.Objects;
import org.idp.server.core.basic.json.JsonReadable;

public class EmailVerificationChallenge implements Serializable, JsonReadable {

  String verificationCode;

  public EmailVerificationChallenge() {}

  public EmailVerificationChallenge(String verificationCode) {
    this.verificationCode = verificationCode;
  }

  public boolean match(String input) {

    return Objects.equals(verificationCode, input);
  }
}
