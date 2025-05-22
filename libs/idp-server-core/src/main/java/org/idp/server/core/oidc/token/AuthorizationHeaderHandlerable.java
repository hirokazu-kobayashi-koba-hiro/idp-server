/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.oidc.token;

import java.util.Base64;
import org.idp.server.basic.http.BasicAuth;
import org.idp.server.basic.type.oauth.AccessTokenEntity;

public interface AuthorizationHeaderHandlerable {

  default AuthorizationHeaderType type(String authorizationHeader) {
    return AuthorizationHeaderType.of(authorizationHeader);
  }

  default boolean isBasicAuth(String authorizationHeader) {
    AuthorizationHeaderType type = type(authorizationHeader);
    return type.isBasic();
  }

  default boolean isBearer(String authorizationHeader) {
    AuthorizationHeaderType type = type(authorizationHeader);
    return type.isBearer();
  }

  default boolean isDPop(String authorizationHeader) {
    AuthorizationHeaderType type = type(authorizationHeader);
    return type.isDPoP();
  }

  default BasicAuth convertBasicAuth(String authorizationHeader) {
    if (!isBasicAuth(authorizationHeader)) {
      return new BasicAuth();
    }
    String value = authorizationHeader.substring("Basic ".length());
    byte[] decode = Base64.getUrlDecoder().decode(value);
    String decodedValue = new String(decode);
    if (!decodedValue.contains(":")) {
      return new BasicAuth();
    }
    String[] splitValues = decodedValue.split(":");
    return new BasicAuth(splitValues[0], splitValues[1]);
  }

  default AccessTokenEntity extractAccessToken(String authorizationHeader) {
    if (isBearer(authorizationHeader)) {
      String accessTokenValue =
          authorizationHeader.substring(AuthorizationHeaderType.Bearer.length());
      return new AccessTokenEntity(accessTokenValue);
    }
    if (isDPop(authorizationHeader)) {
      String accessTokenValue =
          authorizationHeader.substring(AuthorizationHeaderType.DPoP.length());
      return new AccessTokenEntity(accessTokenValue);
    }
    return new AccessTokenEntity();
  }
}
