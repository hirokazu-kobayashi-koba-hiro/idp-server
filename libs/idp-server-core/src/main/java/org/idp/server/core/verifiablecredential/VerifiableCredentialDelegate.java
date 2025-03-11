package org.idp.server.core.verifiablecredential;

import java.util.List;
import org.idp.server.core.oauth.vc.CredentialDefinition;
import org.idp.server.core.type.oauth.Subject;
import org.idp.server.core.type.oauth.TokenIssuer;

public interface VerifiableCredentialDelegate {

  // FIXME
  CredentialDelegateResponse getCredential(
      TokenIssuer tokenIssuer, Subject subject, List<CredentialDefinition> credentialDefinitions);
}
