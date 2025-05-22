/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.basic.oauth;

public enum OAuthAuthorizationType {
  CLIENT_CREDENTIALS("client_credentials"),
  RESOURCE_OWNER_PASSWORD_CREDENTIALS("password");

  String type;

  OAuthAuthorizationType(String type) {
    this.type = type;
  }

  public static OAuthAuthorizationType of(String type) {
    for (OAuthAuthorizationType oAuthAuthorizationType : OAuthAuthorizationType.values()) {
      if (oAuthAuthorizationType.type.equals(type)) {
        return oAuthAuthorizationType;
      }
    }
    throw new UnsupportedOperationException("Unsupported OAuth authorization type: " + type);
  }

  public String type() {
    return type;
  }
}
