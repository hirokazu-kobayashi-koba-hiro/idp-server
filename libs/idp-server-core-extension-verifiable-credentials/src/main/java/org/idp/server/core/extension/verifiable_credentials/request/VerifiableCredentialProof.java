package org.idp.server.core.extension.verifiable_credentials.request;

import java.util.Objects;
import org.idp.server.basic.type.verifiablecredential.ProofType;

public class VerifiableCredentialProof {
  ProofType proofType;
  String jwt;
  String cwt;

  public VerifiableCredentialProof() {}

  public VerifiableCredentialProof(ProofType proofType, String jwt, String cwt) {
    this.proofType = proofType;
    this.jwt = jwt;
    this.cwt = cwt;
  }

  public ProofType proofType() {
    return proofType;
  }

  public boolean isProofTypeDefined() {
    return Objects.nonNull(proofType) && proofType().isDefined();
  }

  public String jwt() {
    return jwt;
  }

  public boolean hasJwt() {
    return Objects.nonNull(jwt) && !jwt.isEmpty();
  }

  public String cwt() {
    return cwt;
  }

  public boolean hasCwt() {
    return Objects.nonNull(cwt) && !cwt.isEmpty();
  }

  public boolean isJwtType() {
    return proofType.isJwt();
  }

  public boolean isCwtType() {
    return proofType.isCwt();
  }

  public boolean exists() {
    return Objects.nonNull(proofType);
  }
}
