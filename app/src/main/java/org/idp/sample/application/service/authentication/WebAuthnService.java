package org.idp.sample.application.service.authentication;

import com.webauthn4j.WebAuthnManager;
import org.idp.sample.application.service.authentication.internal.WebAuthnConfigurationService;
import org.idp.sample.application.service.authentication.internal.WebAuthnCredentialService;
import org.idp.sample.application.service.authentication.internal.WebAuthnSessionService;
import org.idp.sample.application.service.user.internal.UserService;
import org.idp.sample.domain.model.tenant.Tenant;
import org.idp.sample.subdomain.webauthn.*;
import org.idp.server.oauth.identity.User;
import org.springframework.stereotype.Service;

@Service
public class WebAuthnService {

  WebAuthnConfigurationService webAuthnConfigurationService;
  WebAuthnManager webAuthnManager;
  WebAuthnSessionService webAuthnSessionService;
  WebAuthnCredentialService webAuthnCredentialService;
  UserService userService;

  public WebAuthnService(
      WebAuthnConfigurationService webAuthnConfigurationService,
      WebAuthnSessionService webAuthnSessionService,
      WebAuthnCredentialService webAuthnCredentialService,
      UserService userService) {
    this.webAuthnConfigurationService = webAuthnConfigurationService;
    this.webAuthnManager = WebAuthnManager.createNonStrictWebAuthnManager();
    this.webAuthnSessionService = webAuthnSessionService;
    this.webAuthnCredentialService = webAuthnCredentialService;
    this.userService = userService;
  }

  public WebAuthnSession challengeRegistration(Tenant tenant) {
    webAuthnConfigurationService.get(tenant);

    return webAuthnSessionService.start();
  }

  public WebAuthnCredential verifyRegistration(Tenant tenant, User user, String request) {

    WebAuthnConfiguration configuration = webAuthnConfigurationService.get(tenant);
    WebAuthnSession session = webAuthnSessionService.get();

    WebAuthnRegistrationManager manager =
        new WebAuthnRegistrationManager(
            webAuthnManager, configuration, session, request, user.sub());

    WebAuthnCredential webAuthnCredential = manager.verifyAndCreateCredential();

    // FIXME change timing registration
    webAuthnCredentialService.register(webAuthnCredential);

    return webAuthnCredential;
  }

  public WebAuthnSession challengeAuthentication(Tenant tenant) {
    webAuthnConfigurationService.get(tenant);

    return webAuthnSessionService.start();
  }

  public User verifyAuthentication(Tenant tenant, String request) {

    WebAuthnConfiguration configuration = webAuthnConfigurationService.get(tenant);
    WebAuthnSession session = webAuthnSessionService.get();

    WebAuthnAuthenticationManager manager =
        new WebAuthnAuthenticationManager(webAuthnManager, configuration, session, request);

    String extractUserId = manager.extractUserId();
    WebAuthnCredentials webAuthnCredentials = webAuthnCredentialService.findAll(extractUserId);

    manager.verify(webAuthnCredentials);

    return userService.get(extractUserId);
  }
}
