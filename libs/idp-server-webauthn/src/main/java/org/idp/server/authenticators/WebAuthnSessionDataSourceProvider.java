package org.idp.server.authenticators;

import org.idp.server.authenticators.datasource.session.WebAuthnSessionDataSource;
import org.idp.server.authenticators.webauthn.WebAuthnSessionRepository;
import org.idp.server.core.mfa.MfaDependencyProvider;

public class WebAuthnSessionDataSourceProvider
    implements MfaDependencyProvider<WebAuthnSessionRepository> {

  @Override
  public Class<WebAuthnSessionRepository> type() {
    return WebAuthnSessionRepository.class;
  }

  @Override
  public WebAuthnSessionRepository provide() {
    return new WebAuthnSessionDataSource();
  }
}
