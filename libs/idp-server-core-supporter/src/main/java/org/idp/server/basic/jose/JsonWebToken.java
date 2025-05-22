/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.basic.jose;

import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import java.text.ParseException;
import java.util.Objects;

public class JsonWebToken {
  PlainJWT value;

  public JsonWebToken() {}

  public JsonWebToken(PlainJWT value) {
    this.value = value;
  }

  public static JsonWebToken parse(String jose) throws JoseInvalidException {
    try {
      PlainJWT plainJWT = PlainJWT.parse(jose);
      return new JsonWebToken(plainJWT);
    } catch (ParseException e) {
      throw new JoseInvalidException(e.getMessage(), e);
    }
  }

  public String serialize() {
    return value.serialize();
  }

  PlainJWT value() {
    return value;
  }

  public boolean exists() {
    return Objects.nonNull(value);
  }

  public JsonWebTokenClaims claims() {
    try {
      JWTClaimsSet jwtClaimsSet = value.getJWTClaimsSet();
      return new JsonWebTokenClaims(jwtClaimsSet);
    } catch (ParseException e) {
      throw new RuntimeException(e);
    }
  }
}
