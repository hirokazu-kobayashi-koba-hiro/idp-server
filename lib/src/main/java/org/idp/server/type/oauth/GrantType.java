package org.idp.server.type.oauth;

import java.util.Objects;

public enum GrantType {
  authorization_code,
  implicit,
  password,
  refresh_token,
  unknown,
  undefined;

  public static GrantType of(String value) {
    if (Objects.isNull(value) || value.isEmpty()) {
      return undefined;
    }
    for (GrantType grantType : GrantType.values()) {
      if (grantType.name().equals(value)) {
        return grantType;
      }
    }
    return unknown;
  }
}
