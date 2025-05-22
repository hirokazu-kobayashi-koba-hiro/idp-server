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
import org.idp.server.basic.type.verifiablecredential.DocType;
import org.idp.server.basic.type.verifiablecredential.Format;
import org.idp.server.core.oidc.vc.CredentialDefinition;

public class VerifiableCredentialRequest {
  Format format;
  DocType docType;
  CredentialDefinition credentialDefinition;
  VerifiableCredentialProof proof;

  public VerifiableCredentialRequest() {}

  public VerifiableCredentialRequest(
      Format format,
      DocType docType,
      CredentialDefinition credentialDefinition,
      VerifiableCredentialProof proof) {
    this.format = format;
    this.docType = docType;
    this.credentialDefinition = credentialDefinition;
    this.proof = proof;
  }

  public Format format() {
    return format;
  }

  public boolean isFormatDefined() {
    return Objects.nonNull(format) && format.isDefined();
  }

  public DocType docType() {
    return docType;
  }

  public boolean hasDocType() {
    return Objects.nonNull(docType) && docType.exists();
  }

  public CredentialDefinition credentialDefinition() {
    return credentialDefinition;
  }

  public boolean hasCredentialDefinition() {
    return Objects.nonNull(credentialDefinition);
  }

  public VerifiableCredentialProof proof() {
    return proof;
  }

  public boolean hasProof() {
    return Objects.nonNull(proof) && proof.exists();
  }
}
