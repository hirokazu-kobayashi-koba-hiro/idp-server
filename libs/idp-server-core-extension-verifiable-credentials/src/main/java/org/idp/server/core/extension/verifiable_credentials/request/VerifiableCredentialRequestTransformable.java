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

import java.security.PublicKey;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.idp.server.core.extension.verifiable_credentials.exception.VerifiableCredentialRequestInvalidException;
import org.idp.server.core.oidc.type.verifiablecredential.DocType;
import org.idp.server.core.oidc.type.verifiablecredential.Format;
import org.idp.server.core.oidc.type.verifiablecredential.ProofType;
import org.idp.server.core.oidc.vc.CredentialDefinition;
import org.idp.server.platform.jose.JsonWebKey;
import org.idp.server.platform.jose.JsonWebKeyInvalidException;
import org.idp.server.platform.jose.JsonWebSignatureHeader;
import org.idp.server.platform.x509.X509CertInvalidException;
import org.idp.server.platform.x509.X509Certification;

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
