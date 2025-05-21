package org.idp.server.core.extension.verifiable_credentials.verifier;

import org.idp.server.core.extension.verifiable_credentials.CredentialDelegateResponse;
import org.idp.server.core.extension.verifiable_credentials.exception.VerifiableCredentialBadRequestException;
import org.idp.server.core.extension.verifiable_credentials.request.VerifiableCredentialRequestTransformable;

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
