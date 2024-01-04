package org.idp.server.verifiablecredential.verifier;

import org.idp.server.verifiablecredential.CredentialDelegateResponse;
import org.idp.server.verifiablecredential.exception.VerifiableCredentialBadRequestException;
import org.idp.server.verifiablecredential.request.VerifiableCredentialRequestTransformable;

public class DeferredVerifiableCredentialVerifier
    implements VerifiableCredentialRequestTransformable {

  CredentialDelegateResponse credentialDelegateResponse;

  public DeferredVerifiableCredentialVerifier(
      CredentialDelegateResponse credentialDelegateResponse) {
    this.credentialDelegateResponse = credentialDelegateResponse;
  }

  public void verify() {
    if (credentialDelegateResponse.isPending()) {
      throw new VerifiableCredentialBadRequestException(
          "issuance_pending", "The credential issuance is still pending");
    }
  }
}
