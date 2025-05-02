package org.idp.server.core.verifiable_credential;

import org.idp.server.basic.vc.Credential;
import org.idp.server.core.oidc.configuration.ClientConfiguration;
import org.idp.server.core.oidc.configuration.ServerConfiguration;

public interface VerifiableCredentialCreator {
  VerifiableCredential create(
      Credential credential,
      ServerConfiguration serverConfiguration,
      ClientConfiguration clientConfiguration);
}
