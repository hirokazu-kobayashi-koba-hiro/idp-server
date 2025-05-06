package org.idp.server.core.authentication.webauthn;

import java.util.Objects;

public class WebAuthnExecutorType {
  String value;

  public WebAuthnExecutorType() {}

  public WebAuthnExecutorType(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass())
      return false;
    WebAuthnExecutorType that = (WebAuthnExecutorType) o;
    return Objects.equals(value, that.value);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(value);
  }
}
