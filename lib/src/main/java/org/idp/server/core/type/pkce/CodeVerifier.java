package org.idp.server.core.type.pkce;

import java.util.Objects;

public class CodeVerifier {
  String value;

  public CodeVerifier() {}

  public CodeVerifier(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }

  public boolean exists() {
    return Objects.nonNull(value) && !value.isEmpty();
  }
}
