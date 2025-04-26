package org.idp.server.core.identity.trustframework;

import java.util.Objects;

public class IdentityVerificationApplicationIdentifier {
  String value;

  public IdentityVerificationApplicationIdentifier() {}

  public IdentityVerificationApplicationIdentifier(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) return false;
    IdentityVerificationApplicationIdentifier that = (IdentityVerificationApplicationIdentifier) o;
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
