package org.idp.server.basic.vc;

import org.idp.server.verifiablecredential.VerifiableCredentialJwt;

public class VerifiableCredentialContext {
  VerifiableCredentialFormat format;
  VerifiableCredential verifiableCredential;
  VerifiableCredentialJsonLinkedData verifiableCredentialJsonLinkedData;
  VerifiableCredentialJwt verifiableCredentialJwt;

  public VerifiableCredentialContext(
      VerifiableCredentialFormat format, VerifiableCredential verifiableCredential) {
    this.format = format;
    this.verifiableCredential = verifiableCredential;
  }

  public VerifiableCredentialContext(
      VerifiableCredentialFormat format,
      VerifiableCredentialJsonLinkedData verifiableCredentialJsonLinkedData) {
    this.format = format;
    this.verifiableCredentialJsonLinkedData = verifiableCredentialJsonLinkedData;
  }

  public VerifiableCredentialContext(
      VerifiableCredentialFormat format, VerifiableCredentialJwt verifiableCredentialJwt) {
    this.format = format;
    this.verifiableCredentialJwt = verifiableCredentialJwt;
  }

  public VerifiableCredentialFormat format() {
    return format;
  }

  public VerifiableCredential verifiableCredential() {
    return verifiableCredential;
  }

  public VerifiableCredentialJsonLinkedData verifiableCredentialJsonLinkedData() {
    return verifiableCredentialJsonLinkedData;
  }

  public VerifiableCredentialJwt verifiableCredentialJwt() {
    return verifiableCredentialJwt;
  }

  public String serializeVerifiableCredential() {
    return verifiableCredentialJsonLinkedData.toJson();
  }
}
