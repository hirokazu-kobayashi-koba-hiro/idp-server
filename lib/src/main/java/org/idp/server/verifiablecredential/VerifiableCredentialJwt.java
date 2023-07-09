package org.idp.server.verifiablecredential;

import java.util.Objects;

public class VerifiableCredentialJwt {
  String value;

  public VerifiableCredentialJwt() {}

  public VerifiableCredentialJwt(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }

  public boolean exists() {
    return Objects.nonNull(value) && !value.isEmpty();
  }
}
