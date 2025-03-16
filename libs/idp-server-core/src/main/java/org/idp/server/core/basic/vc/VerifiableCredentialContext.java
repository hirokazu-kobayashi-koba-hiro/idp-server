package org.idp.server.core.basic.vc;

import org.idp.server.core.verifiablecredential.VerifiableCredential;

public class VerifiableCredentialContext {
  VerifiableCredentialFormat format;
  Credential credential;
  VerifiableCredentialJsonLinkedData verifiableCredentialJsonLinkedData;
  VerifiableCredential verifiableCredential;

  public VerifiableCredentialContext(VerifiableCredentialFormat format, Credential credential) {
    this.format = format;
    this.credential = credential;
  }

  public VerifiableCredentialContext(
      VerifiableCredentialFormat format,
      VerifiableCredentialJsonLinkedData verifiableCredentialJsonLinkedData) {
    this.format = format;
    this.verifiableCredentialJsonLinkedData = verifiableCredentialJsonLinkedData;
  }

  public VerifiableCredentialContext(
      VerifiableCredentialFormat format, VerifiableCredential verifiableCredential) {
    this.format = format;
    this.verifiableCredential = verifiableCredential;
  }

  public VerifiableCredentialFormat format() {
    return format;
  }

  public Credential verifiableCredential() {
    return credential;
  }

  public VerifiableCredentialJsonLinkedData verifiableCredentialJsonLinkedData() {
    return verifiableCredentialJsonLinkedData;
  }

  public VerifiableCredential verifiableCredentialJwt() {
    return verifiableCredential;
  }

  public String serializeVerifiableCredential() {
    return verifiableCredentialJsonLinkedData.toJson();
  }
}
