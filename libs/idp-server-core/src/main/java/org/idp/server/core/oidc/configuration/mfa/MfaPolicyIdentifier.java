package org.idp.server.core.oidc.configuration.mfa;

import java.util.Objects;

public class MfaPolicyIdentifier {
  String value;

  public MfaPolicyIdentifier() {}

  public MfaPolicyIdentifier(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }

  public boolean exists() {
    return value != null && !value.isEmpty();
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) return false;
    MfaPolicyIdentifier that = (MfaPolicyIdentifier) o;
    return Objects.equals(value, that.value);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(value);
  }
}
