package org.idp.server.type.verifiablecredential;

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
}
