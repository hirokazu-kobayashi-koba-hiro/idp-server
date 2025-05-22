/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
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
