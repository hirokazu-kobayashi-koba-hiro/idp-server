package org.idp.server.core.extension.identity.verification.result;

import java.util.Objects;

public class IdentityVerificationResultIdentifier {

  String value;

  public IdentityVerificationResultIdentifier() {}

  public IdentityVerificationResultIdentifier(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) return false;
    IdentityVerificationResultIdentifier that = (IdentityVerificationResultIdentifier) o;
    return Objects.equals(value, that.value);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(value);
  }

  public boolean exists() {
    return value != null && !value.isEmpty();
  }
}
