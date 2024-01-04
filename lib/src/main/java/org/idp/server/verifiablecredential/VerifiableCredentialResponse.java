package org.idp.server.verifiablecredential;

import org.idp.server.type.verifiablecredential.CNonce;
import org.idp.server.type.verifiablecredential.CNonceExpiresIn;
import org.idp.server.type.verifiablecredential.Format;

public class VerifiableCredentialResponse {
  Format format;
  VerifiableCredential credentialJwt;
  CNonce cNonce;
  CNonceExpiresIn cNonceExpiresIn;
  String contents;

  public VerifiableCredentialResponse() {}

  VerifiableCredentialResponse(
      Format format,
      VerifiableCredential credentialJwt,
      CNonce cNonce,
      CNonceExpiresIn cNonceExpiresIn,
      String contents) {
    this.format = format;
    this.credentialJwt = credentialJwt;
    this.cNonce = cNonce;
    this.cNonceExpiresIn = cNonceExpiresIn;
    this.contents = contents;
  }

  public Format getFormat() {
    return format;
  }

  public VerifiableCredential credentialJwt() {
    return credentialJwt;
  }

  public CNonce cNonce() {
    return cNonce;
  }

  public CNonceExpiresIn cNonceExpiresIn() {
    return cNonceExpiresIn;
  }

  public String contents() {
    return contents;
  }
}
