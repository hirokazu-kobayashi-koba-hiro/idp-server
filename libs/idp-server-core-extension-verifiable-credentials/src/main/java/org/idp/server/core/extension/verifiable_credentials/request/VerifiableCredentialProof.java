/*
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


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
