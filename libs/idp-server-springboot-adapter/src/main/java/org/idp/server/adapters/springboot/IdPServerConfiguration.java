package org.idp.server.adapters.springboot;

import org.idp.server.adapters.springboot.authentication.EmailAuthenticationService;
import org.idp.server.adapters.springboot.authorization.OAuthSessionService;
import org.idp.server.adapters.springboot.event.EventPublisherService;
import org.idp.server.authenticators.handler.webauthn.datasource.configuration.WebAuthnConfigurationDataSource;
import org.idp.server.authenticators.handler.webauthn.datasource.credential.WebAuthnCredentialDataSource;
import org.idp.server.authenticators.handler.webauthn.datasource.session.WebAuthnSessionDataSource;
import org.idp.server.authenticators.WebAuthnService;
import org.idp.server.authenticators.webauthn.service.internal.WebAuthnConfigurationService;
import org.idp.server.authenticators.webauthn.service.internal.WebAuthnCredentialService;
import org.idp.server.authenticators.webauthn.service.internal.WebAuthnSessionService;
import org.idp.server.core.adapters.IdpServerApplication;
import org.idp.server.core.handler.config.DatabaseConfig;
import org.idp.server.core.oauth.interaction.OAuthUserInteractionType;
import org.idp.server.core.oauth.interaction.OAuthUserInteractor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.HashMap;
import java.util.Map;

@EnableAsync
  @EnableScheduling
@Configuration
public class IdPServerConfiguration {

  @Value("${spring.datasource.url}")
  String databaseUrl;

  @Value("${spring.datasource.username}")
  String databaseUsername;

  @Value("${spring.datasource.password}")
  String databasePassword;

  @Value("${idp.configurations.encryptionKey}")
  String encryptionKey;

  @Bean
  public IdpServerApplication idpServerApplication(OAuthSessionService oAuthSessionService, EmailAuthenticationService emailAuthenticationService, EventPublisherService eventPublisherService) {

    DatabaseConfig databaseConfig =
        new DatabaseConfig(databaseUrl, databaseUsername, databasePassword);

    BCryptPasswordEncoder bCryptPasswordEncoder = new BCryptPasswordEncoder();
    PasswordEncoder passwordEncoder = new PasswordEncoder(bCryptPasswordEncoder);
    PasswordVerification passwordVerification = new PasswordVerification(bCryptPasswordEncoder);

    WebAuthnConfigurationService configurationService  = new WebAuthnConfigurationService(new WebAuthnConfigurationDataSource());
    WebAuthnSessionService sessionService = new WebAuthnSessionService(new WebAuthnSessionDataSource());
    WebAuthnCredentialService credentialService = new WebAuthnCredentialService(new WebAuthnCredentialDataSource());
    WebAuthnService webAuthnService = new WebAuthnService(configurationService, sessionService, credentialService);

    Map<OAuthUserInteractionType, OAuthUserInteractor> additionalUserInteractions = new HashMap<>();
    additionalUserInteractions.put(OAuthUserInteractionType.WEBAUTHN_REGISTRATION_CHALLENGE, webAuthnService);
    additionalUserInteractions.put(OAuthUserInteractionType.WEBAUTHN_REGISTRATION, webAuthnService);
    additionalUserInteractions.put(OAuthUserInteractionType.WEBAUTHN_AUTHENTICATION_CHALLENGE, webAuthnService);
    additionalUserInteractions.put(OAuthUserInteractionType.WEBAUTHN_AUTHENTICATION, webAuthnService);

    additionalUserInteractions.put(OAuthUserInteractionType.EMAIL_VERIFICATION_CHALLENGE, emailAuthenticationService);
    additionalUserInteractions.put(OAuthUserInteractionType.EMAIL_VERIFICATION, emailAuthenticationService);

    return new IdpServerApplication(databaseConfig, encryptionKey, oAuthSessionService, additionalUserInteractions, passwordEncoder, passwordVerification, eventPublisherService);
  }


}
