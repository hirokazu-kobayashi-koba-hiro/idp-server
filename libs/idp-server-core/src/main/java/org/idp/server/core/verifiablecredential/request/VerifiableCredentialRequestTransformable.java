package org.idp.server.core.verifiablecredential.request;

import java.security.PublicKey;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.idp.server.basic.jose.JsonWebKey;
import org.idp.server.basic.jose.JsonWebKeyInvalidException;
import org.idp.server.basic.jose.JsonWebSignatureHeader;
import org.idp.server.basic.x509.X509CertInvalidException;
import org.idp.server.basic.x509.X509Certification;
import org.idp.server.core.oidc.vc.CredentialDefinition;
import org.idp.server.basic.type.verifiablecredential.DocType;
import org.idp.server.basic.type.verifiablecredential.Format;
import org.idp.server.basic.type.verifiablecredential.ProofType;
import org.idp.server.core.verifiablecredential.exception.VerifiableCredentialRequestInvalidException;

public interface VerifiableCredentialRequestTransformable {

  default BatchCredentialRequests transformBatchRequest(Object object)
      throws VerifiableCredentialRequestInvalidException {
    try {
      List<Object> list = (List<Object>) object;
      List<VerifiableCredentialRequest> verifiableCredentialRequests =
          list.stream()
              .map(
                  value -> {
                    try {
                      return transformRequest(value);
                    } catch (VerifiableCredentialRequestInvalidException e) {
                      throw new RuntimeException(e);
                    }
                  })
              .toList();
      return new BatchCredentialRequests(verifiableCredentialRequests);
    } catch (Exception e) {
      throw new VerifiableCredentialRequestInvalidException(
          "invalid batch credential request, can not pared", e);
    }
  }

  default VerifiableCredentialRequest transformRequest(Object object)
      throws VerifiableCredentialRequestInvalidException {
    try {
      if (Objects.isNull(object)) {
        return new VerifiableCredentialRequest();
      }
      Map<String, Object> map = (Map<String, Object>) object;
      Format format = Format.of((String) map.get("format"));
      DocType docType = new DocType((String) map.get("doc_type"));
      CredentialDefinition credentialDefinition =
          new CredentialDefinition((Map<String, Object>) map.get("credential_definition"));
      VerifiableCredentialProof proof = transformProof(map.get("proof"));
      return new VerifiableCredentialRequest(format, docType, credentialDefinition, proof);
    } catch (Exception e) {
      throw new VerifiableCredentialRequestInvalidException(
          "invalid verifiable credential request, can not parsed", e);
    }
  }

  default VerifiableCredentialProof transformProof(Object proofEntity)
      throws VerifiableCredentialRequestInvalidException {
    try {
      if (Objects.isNull(proofEntity)) {
        return new VerifiableCredentialProof();
      }
      Map<String, Object> map = (Map<String, Object>) proofEntity;
      ProofType proofType = ProofType.of(getValueOrEmpty(map, "proof_type"));
      String jwt = getValueOrEmpty(map, "jwt");
      String cwt = getValueOrEmpty(map, "cwt");
      return new VerifiableCredentialProof(proofType, jwt, cwt);
    } catch (Exception e) {
      throw new VerifiableCredentialRequestInvalidException("invalid proof, can not parsed", e);
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
