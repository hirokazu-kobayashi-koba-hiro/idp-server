package org.idp.server.core.verifiablecredential.verifier;

import org.idp.server.core.verifiablecredential.CredentialDelegateResponse;
import org.idp.server.core.verifiablecredential.exception.VerifiableCredentialBadRequestException;
import org.idp.server.core.verifiablecredential.request.VerifiableCredentialRequestTransformable;

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
