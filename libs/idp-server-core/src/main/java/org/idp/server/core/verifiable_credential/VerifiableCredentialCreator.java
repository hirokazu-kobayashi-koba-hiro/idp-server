package org.idp.server.core.verifiable_credential;

import org.idp.server.basic.vc.Credential;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.oidc.configuration.client.ClientConfiguration;

public interface VerifiableCredentialCreator {
  VerifiableCredential create(
      Credential credential,
      AuthorizationServerConfiguration authorizationServerConfiguration,
      ClientConfiguration clientConfiguration);
}
