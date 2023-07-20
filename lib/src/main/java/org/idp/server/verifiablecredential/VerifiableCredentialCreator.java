package org.idp.server.verifiablecredential;

import org.idp.server.basic.vc.VerifiableCredential;
import org.idp.server.configuration.ClientConfiguration;
import org.idp.server.configuration.ServerConfiguration;

public interface VerifiableCredentialCreator {
  VerifiableCredentialJwt create(
      VerifiableCredential verifiableCredential,
      ServerConfiguration serverConfiguration,
      ClientConfiguration clientConfiguration);
}
