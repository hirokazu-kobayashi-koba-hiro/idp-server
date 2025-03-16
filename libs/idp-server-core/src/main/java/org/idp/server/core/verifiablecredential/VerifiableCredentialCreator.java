package org.idp.server.core.verifiablecredential;

import org.idp.server.core.basic.vc.Credential;
import org.idp.server.core.configuration.ClientConfiguration;
import org.idp.server.core.configuration.ServerConfiguration;

public interface VerifiableCredentialCreator {
  VerifiableCredential create(
      Credential credential,
      ServerConfiguration serverConfiguration,
      ClientConfiguration clientConfiguration);
}
