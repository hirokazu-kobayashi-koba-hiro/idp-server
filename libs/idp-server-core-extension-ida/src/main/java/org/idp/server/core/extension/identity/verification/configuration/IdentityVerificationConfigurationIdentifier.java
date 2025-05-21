package org.idp.server.core.extension.identity.verification.configuration;

import java.util.Objects;

public class IdentityVerificationConfigurationIdentifier {

  String value;

  public IdentityVerificationConfigurationIdentifier() {}

  public IdentityVerificationConfigurationIdentifier(String value) {
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
    IdentityVerificationConfigurationIdentifier that =
        (IdentityVerificationConfigurationIdentifier) o;
    return Objects.equals(value, that.value);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(value);
  }
}
