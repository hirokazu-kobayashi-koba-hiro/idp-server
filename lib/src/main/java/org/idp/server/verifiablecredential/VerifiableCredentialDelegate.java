package org.idp.server.verifiablecredential;

import org.idp.server.oauth.rar.CredentialDefinition;
import org.idp.server.type.oauth.Subject;
import org.idp.server.type.oauth.TokenIssuer;

public interface VerifiableCredentialDelegate {

  Credential getCredential(
      TokenIssuer tokenIssuer, Subject subject, CredentialDefinition credentialDefinition);
}
