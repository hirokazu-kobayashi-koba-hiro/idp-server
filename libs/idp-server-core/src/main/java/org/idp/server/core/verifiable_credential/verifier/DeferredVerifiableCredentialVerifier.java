package org.idp.server.core.verifiable_credential.verifier;

import org.idp.server.core.verifiable_credential.CredentialDelegateResponse;
import org.idp.server.core.verifiable_credential.exception.VerifiableCredentialBadRequestException;
import org.idp.server.core.verifiable_credential.request.VerifiableCredentialRequestTransformable;

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
