package org.idp.server.core.mfa;

import java.util.Objects;

public class MfaTransactionIdentifier {
  String value;

  public MfaTransactionIdentifier() {}

  public MfaTransactionIdentifier(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) return false;
    MfaTransactionIdentifier that = (MfaTransactionIdentifier) o;
    return Objects.equals(value, that.value);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(value);
  }
}
