package org.idp.server.adapters.springboot;

import org.idp.server.authenticators.handler.webauthn.datasource.configuration.WebAuthnConfigurationDataSource;
import org.idp.server.authenticators.handler.webauthn.datasource.credential.WebAuthnCredentialDataSource;
import org.idp.server.authenticators.handler.webauthn.datasource.session.WebAuthnSessionDataSource;
import org.idp.server.authenticators.webauthn.service.WebAuthnService;
import org.idp.server.authenticators.webauthn.service.internal.WebAuthnConfigurationService;
import org.idp.server.authenticators.webauthn.service.internal.WebAuthnCredentialService;
import org.idp.server.authenticators.webauthn.service.internal.WebAuthnSessionService;
import org.idp.server.core.adapters.IdpServerApplication;
import org.idp.server.core.UserManagementApi;
import org.idp.server.core.handler.config.DatabaseConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class IdPServerConfiguration {

  @Value("${spring.datasource.url}")
  String databaseUrl;

  @Value("${spring.datasource.username}")
  String databaseUsername;

  @Value("${spring.datasource.password}")
  String databasePassword;

  @Bean
  public IdpServerApplication idpServerApplication() {

    DatabaseConfig databaseConfig =
        new DatabaseConfig(databaseUrl, databaseUsername, databasePassword);
    return new IdpServerApplication(databaseConfig);
  }

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  public WebAuthnService webAuthnService(IdpServerApplication idpServerApplication) {
    WebAuthnConfigurationService configurationService  = new WebAuthnConfigurationService(new WebAuthnConfigurationDataSource());
    WebAuthnSessionService sessionService = new WebAuthnSessionService(new WebAuthnSessionDataSource());
    WebAuthnCredentialService credentialService = new WebAuthnCredentialService(new WebAuthnCredentialDataSource());
    UserManagementApi userManagementApi = idpServerApplication.userManagementApi();
    return new WebAuthnService(configurationService, sessionService, credentialService, userManagementApi);
  }
}
