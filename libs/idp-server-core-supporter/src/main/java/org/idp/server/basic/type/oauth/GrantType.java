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

public enum GrantType {
  authorization_code("authorization_code"),
  implicit("implicit"),
  password("password"),
  client_credentials("client_credentials"),
  refresh_token("refresh_token"),
  ciba("urn:openid:params:grant-type:ciba"),
  unknown(""),
  undefined("");

  String value;

  GrantType(String value) {
    this.value = value;
  }

  public static GrantType of(String value) {
    if (Objects.isNull(value) || value.isEmpty()) {
      return undefined;
    }
    for (GrantType grantType : GrantType.values()) {
      if (grantType.value.equals(value)) {
        return grantType;
      }
    }
    return unknown;
  }

  public String value() {
    return value;
  }

  public boolean isClientCredentials() {
    return this == client_credentials;
  }
}
