/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.basic.jose;

import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.util.Base64;
import java.util.List;
import java.util.Objects;

public class JsonWebSignatureHeader {
  JWSHeader jwsHeader;

  public JsonWebSignatureHeader() {}

  public JsonWebSignatureHeader(JWSHeader jwsHeader) {
    this.jwsHeader = jwsHeader;
  }

  public String alg() {
    return jwsHeader.getAlgorithm().getName();
  }

  public boolean hasAlg() {
    return Objects.nonNull(jwsHeader.getAlgorithm());
  }

  public String type() {
    return jwsHeader.getType().getType();
  }

  public boolean hasType() {
    return Objects.nonNull(jwsHeader.getType());
  }

  public String kid() {
    return jwsHeader.getKeyID();
  }

  public boolean hasKid() {
    return Objects.nonNull(jwsHeader.getKeyID());
  }

  public JsonWebKey jwk() {
    return new JsonWebKey(jwsHeader.getJWK());
  }

  public boolean hasJwk() {
    return Objects.nonNull(jwsHeader.getJWK());
  }

  public List<String> x5c() {
    return jwsHeader.getX509CertChain().stream().map(Base64::toString).toList();
  }

  public boolean hasX5c() {
    return Objects.nonNull(jwsHeader.getX509CertChain());
  }

  public boolean exists() {
    return Objects.nonNull(jwsHeader);
  }
}
