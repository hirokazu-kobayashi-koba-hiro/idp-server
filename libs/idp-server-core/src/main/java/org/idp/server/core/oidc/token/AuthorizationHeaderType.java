package org.idp.server.core.oidc.token;

import java.util.Objects;

public enum AuthorizationHeaderType {
  Basic("Basic "),
  Bearer("Bearer "),
  DPoP("DPoP "),
  Unknown(""),
  Undefined("");

  String value;

  AuthorizationHeaderType(String value) {
    this.value = value;
  }

  public static AuthorizationHeaderType of(String authorizationHeader) {
    if (Objects.isNull(authorizationHeader) || authorizationHeader.isEmpty()) {
      return Unknown;
    }
    for (AuthorizationHeaderType type : AuthorizationHeaderType.values()) {
      if (authorizationHeader.startsWith(type.value)) {
        return type;
      }
    }
    return Undefined;
  }

  public boolean isBasic() {
    return this == Basic;
  }

  public boolean isBearer() {
    return this == Bearer;
  }

  public boolean isDPoP() {
    return this == DPoP;
  }

  public String value() {
    return value;
  }

  public int length() {
    return value.length();
  }
}
