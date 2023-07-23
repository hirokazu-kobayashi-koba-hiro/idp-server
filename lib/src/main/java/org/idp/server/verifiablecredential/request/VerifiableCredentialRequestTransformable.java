package org.idp.server.verifiablecredential.request;

import java.security.PublicKey;
import java.util.List;
import java.util.Map;
import org.idp.server.basic.jose.JsonWebKey;
import org.idp.server.basic.jose.JsonWebKeyInvalidException;
import org.idp.server.basic.jose.JsonWebSignatureHeader;
import org.idp.server.basic.x509.X509CertInvalidException;
import org.idp.server.basic.x509.X509Certification;
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

  default PublicKey transformPublicKey(JsonWebSignatureHeader header)
      throws VerifiableCredentialRequestInvalidException {
    try {
      if (header.hasJwk()) {
        JsonWebKey jwk = header.jwk();
        return jwk.toPublicKey();
      }
      if (header.hasX5c()) {
        List<String> strings = header.x5c();
        X509Certification x509Certification = X509Certification.parse(strings.get(0));
        return x509Certification.toPublicKey();
      }
      return null;
    } catch (JsonWebKeyInvalidException e) {
      throw new VerifiableCredentialRequestInvalidException("invalid jwk", e);
    } catch (X509CertInvalidException e) {
      throw new VerifiableCredentialRequestInvalidException("invalid x5c", e);
    }
  }

  private String getValueOrEmpty(Map<String, Object> map, String key) {
    return (String) map.getOrDefault(key, "");
  }
}
