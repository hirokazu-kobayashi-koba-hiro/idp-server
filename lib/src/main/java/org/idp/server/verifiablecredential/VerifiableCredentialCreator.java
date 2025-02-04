package org.idp.server.verifiablecredential;

import org.idp.server.basic.vc.Credential;
import org.idp.server.configuration.ClientConfiguration;
import org.idp.server.configuration.ServerConfiguration;

public interface VerifiableCredentialCreator {
  VerifiableCredential create(
      Credential credential,
      ServerConfiguration serverConfiguration,
      ClientConfiguration clientConfiguration);
}
