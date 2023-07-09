package org.idp.server.verifiablecredential;

import org.idp.server.configuration.ClientConfiguration;
import org.idp.server.configuration.ServerConfiguration;

public class VerifiableCredentialJwtCreator {
  Credential credential;
  ServerConfiguration serverConfiguration;
  ClientConfiguration clientConfiguration;

  public VerifiableCredentialJwtCreator(
      Credential credential,
      ServerConfiguration serverConfiguration,
      ClientConfiguration clientConfiguration) {
    this.credential = credential;
    this.serverConfiguration = serverConfiguration;
    this.clientConfiguration = clientConfiguration;
  }

  public VerifiableCredentialJwt create() {

    return new VerifiableCredentialJwt();
  }
}
