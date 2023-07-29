package org.idp.server.verifiablecredential;

import java.util.List;
import org.idp.server.oauth.vc.CredentialDefinition;
import org.idp.server.type.oauth.Subject;
import org.idp.server.type.oauth.TokenIssuer;

public interface VerifiableCredentialDelegate {

  // FIXME
  VerifiableCredentialDelegateResponse getCredential(
      TokenIssuer tokenIssuer, Subject subject, List<CredentialDefinition> credentialDefinitions);
}
