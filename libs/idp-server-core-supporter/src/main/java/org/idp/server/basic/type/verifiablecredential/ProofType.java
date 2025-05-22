/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.basic.type.verifiablecredential;

import java.util.Objects;

public enum ProofType {
  jwt,
  cwt,
  unknown,
  undefined;

  public static ProofType of(String value) {
    if (Objects.isNull(value) || value.isEmpty()) {
      return undefined;
    }
    for (ProofType proofType : ProofType.values()) {
      if (proofType.name().equals(value)) {
        return proofType;
      }
    }
    return unknown;
  }

  public boolean isDefined() {
    return this != undefined;
  }

  public boolean isJwt() {
    return this == jwt;
  }

  public boolean isCwt() {
    return this == cwt;
  }
}
