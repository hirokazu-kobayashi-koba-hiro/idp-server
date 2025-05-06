package org.idp.server.authenticators.webauthn4j;

import org.idp.server.authenticators.webauthn4j.datasource.credential.WebAuthn4jCredentialDataSource;
import org.idp.server.core.authentication.factory.AuthenticationDependencyProvider;

public class WebAuthn4jCredentialDataSourceProvider implements AuthenticationDependencyProvider<WebAuthn4jCredentialRepository> {

  @Override
  public Class<WebAuthn4jCredentialRepository> type() {
    return WebAuthn4jCredentialRepository.class;
  }

  @Override
  public WebAuthn4jCredentialRepository provide() {
    return new WebAuthn4jCredentialDataSource();
  }
}
