package org.idp.server.verifiablecredential;

import org.idp.server.configuration.ClientConfiguration;
import org.idp.server.configuration.ServerConfiguration;
import org.idp.server.oauth.identity.VerifiableCredential;

public interface VerifiableCredentialCreator {
  VerifiableCredentialJwt create(
      VerifiableCredential verifiableCredential,
      ServerConfiguration serverConfiguration,
      ClientConfiguration clientConfiguration);
}
