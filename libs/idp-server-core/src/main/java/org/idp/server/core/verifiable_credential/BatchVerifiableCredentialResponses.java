package org.idp.server.core.verifiable_credential;

import java.util.List;
import org.idp.server.basic.type.verifiablecredential.CNonce;
import org.idp.server.basic.type.verifiablecredential.CNonceExpiresIn;

public class BatchVerifiableCredentialResponses {
  List<BatchVerifiableCredentialResponse> responses;
  CNonce cNonce;
  CNonceExpiresIn cNonceExpiresIn;
  String contents;

  public BatchVerifiableCredentialResponses() {}

  public BatchVerifiableCredentialResponses(
      List<BatchVerifiableCredentialResponse> responses,
      CNonce cNonce,
      CNonceExpiresIn cNonceExpiresIn,
      String contents) {
    this.responses = responses;
    this.cNonce = cNonce;
    this.cNonceExpiresIn = cNonceExpiresIn;
    this.contents = contents;
  }

  public List<BatchVerifiableCredentialResponse> responses() {
    return responses;
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
