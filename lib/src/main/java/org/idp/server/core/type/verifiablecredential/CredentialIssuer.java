package org.idp.server.core.type.verifiablecredential;

import java.util.Objects;

public class CredentialIssuer {
  String value;

  public CredentialIssuer() {}

  public CredentialIssuer(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    CredentialIssuer that = (CredentialIssuer) o;
    return Objects.equals(value, that.value);
  }

  @Override
  public int hashCode() {
    return Objects.hash(value);
  }

  public boolean exists() {
    return Objects.nonNull(value) && !value.isEmpty();
  }
}
