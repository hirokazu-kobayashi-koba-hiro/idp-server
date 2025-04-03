package org.idp.server.authenticators;

import org.idp.server.authenticators.datasource.configuration.WebAuthnConfigurationDataSource;
import org.idp.server.authenticators.webauthn.WebAuthnConfigurationRepository;
import org.idp.server.core.mfa.MfaDependencyProvider;

public class WebAuthnConfigurationDataSourceProvider implements MfaDependencyProvider<WebAuthnConfigurationRepository> {

    @Override
    public Class<WebAuthnConfigurationRepository> type() {
        return WebAuthnConfigurationRepository.class;
    }

    @Override
    public WebAuthnConfigurationRepository provide() {
        return new WebAuthnConfigurationDataSource();
    }
}
