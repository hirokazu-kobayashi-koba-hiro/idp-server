package org.idp.server.core.authentication;

import java.util.Objects;

public class AuthenticationTransactionIdentifier {
  String value;

  public AuthenticationTransactionIdentifier() {}

  public AuthenticationTransactionIdentifier(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) return false;
    AuthenticationTransactionIdentifier that = (AuthenticationTransactionIdentifier) o;
    return Objects.equals(value, that.value);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(value);
  }
}
