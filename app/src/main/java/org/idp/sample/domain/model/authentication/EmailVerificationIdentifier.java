package org.idp.sample.domain.model.authentication;

import java.util.Objects;

public class EmailVerificationIdentifier {

  String value;

  public EmailVerificationIdentifier() {}

  public EmailVerificationIdentifier(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }

  public boolean exists() {
    return Objects.nonNull(value) && !value.isEmpty();
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) return false;
    EmailVerificationIdentifier that = (EmailVerificationIdentifier) o;
    return Objects.equals(value, that.value);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(value);
  }
}
