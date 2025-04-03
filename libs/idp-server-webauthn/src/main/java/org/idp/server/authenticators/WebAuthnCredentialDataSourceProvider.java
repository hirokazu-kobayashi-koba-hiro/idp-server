package org.idp.server.authenticators;

import org.idp.server.authenticators.datasource.credential.WebAuthnCredentialDataSource;
import org.idp.server.authenticators.webauthn.WebAuthnCredentialRepository;
import org.idp.server.core.mfa.MfaDependencyProvider;

public class WebAuthnCredentialDataSourceProvider
    implements MfaDependencyProvider<WebAuthnCredentialRepository> {

  @Override
  public Class<WebAuthnCredentialRepository> type() {
    return WebAuthnCredentialRepository.class;
  }

  @Override
  public WebAuthnCredentialRepository provide() {
    return new WebAuthnCredentialDataSource();
  }
}
