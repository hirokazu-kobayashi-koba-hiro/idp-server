/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.basic.type.oauth;

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
