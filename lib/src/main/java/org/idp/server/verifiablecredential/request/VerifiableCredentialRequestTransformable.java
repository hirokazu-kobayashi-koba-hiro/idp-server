package org.idp.server.verifiablecredential.request;

import java.util.Map;
import org.idp.server.type.verifiablecredential.ProofEntity;
import org.idp.server.type.verifiablecredential.ProofType;
import org.idp.server.verifiablecredential.exception.VerifiableCredentialRequestInvalidException;

public interface VerifiableCredentialRequestTransformable {

  default VerifiableCredentialProof transformProof(ProofEntity proofEntity)
      throws VerifiableCredentialRequestInvalidException {
    try {
      Object value = proofEntity.value();
      Map<String, Object> map = (Map<String, Object>) value;
      ProofType proofType = ProofType.of(getValueOrEmpty(map, "proof_type"));
      String jwt = getValueOrEmpty(map, "jwt");
      String cwt = getValueOrEmpty(map, "cwt");
      return new VerifiableCredentialProof(proofType, jwt, cwt);
    } catch (Exception e) {
      throw new VerifiableCredentialRequestInvalidException("invalid proof, can not pared", e);
    }
  }

  private String getValueOrEmpty(Map<String, Object> map, String key) {
    return (String) map.getOrDefault(key, "");
  }
}
