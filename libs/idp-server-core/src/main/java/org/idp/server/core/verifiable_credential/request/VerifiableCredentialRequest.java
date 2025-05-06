package org.idp.server.core.verifiable_credential.request;

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

  public VerifiableCredentialRequest(Format format, DocType docType, CredentialDefinition credentialDefinition, VerifiableCredentialProof proof) {
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
