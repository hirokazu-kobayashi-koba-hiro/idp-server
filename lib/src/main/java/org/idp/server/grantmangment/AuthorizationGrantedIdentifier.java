package org.idp.server.grantmangment;

import java.util.Objects;

public class AuthorizationGrantedIdentifier {

  String value;

  public AuthorizationGrantedIdentifier() {}

  public AuthorizationGrantedIdentifier(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }

  public boolean exists() {
    return Objects.nonNull(value) && !value.isEmpty();
  }
}
