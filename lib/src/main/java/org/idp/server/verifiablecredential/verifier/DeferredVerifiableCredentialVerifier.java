package org.idp.server.verifiablecredential.verifier;

import org.idp.server.verifiablecredential.VerifiableCredentialDelegateResponse;
import org.idp.server.verifiablecredential.exception.VerifiableCredentialBadRequestException;
import org.idp.server.verifiablecredential.request.VerifiableCredentialRequestTransformable;

public class DeferredVerifiableCredentialVerifier
    implements VerifiableCredentialRequestTransformable {

  VerifiableCredentialDelegateResponse verifiableCredentialDelegateResponse;

  public DeferredVerifiableCredentialVerifier(
      VerifiableCredentialDelegateResponse verifiableCredentialDelegateResponse) {
    this.verifiableCredentialDelegateResponse = verifiableCredentialDelegateResponse;
  }

  public void verify() {
    if (verifiableCredentialDelegateResponse.isPending()) {
      throw new VerifiableCredentialBadRequestException(
          "issuance_pending", "The credential issuance is still pending");
    }
  }
}
