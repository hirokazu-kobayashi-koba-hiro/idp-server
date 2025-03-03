package org.idp.server.core.type.oauth;

import java.util.Objects;

public enum ClientAssertionType {
  jwt_bearer("urn:ietf:params:oauth:client-assertion-type:jwt-bearer"),
  unknown(""),
  undefined("");

  String value;

  ClientAssertionType(String value) {
    this.value = value;
  }

  public static ClientAssertionType of(String value) {
    if (Objects.isNull(value) || value.isEmpty()) {
      return undefined;
    }
    for (ClientAssertionType clientAssertionType : ClientAssertionType.values()) {
      if (clientAssertionType.value.equals(value)) {
        return clientAssertionType;
      }
    }
    return unknown;
  }

  public boolean isDefined() {
    return this != undefined;
  }
}
