package org.idp.server.verifiablecredential;

import org.idp.server.basic.vc.Credential;
import org.idp.server.configuration.ClientConfiguration;
import org.idp.server.configuration.ServerConfiguration;
import org.idp.server.type.verifiablecredential.Format;

public interface VerifiableCredentialCreator {
  VerifiableCredential create(
      Credential credential,
      ServerConfiguration serverConfiguration,
      ClientConfiguration clientConfiguration);
}
