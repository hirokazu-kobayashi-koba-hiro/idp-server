package org.idp.server.authenticators.webauthn.service;

import org.idp.server.authenticators.webauthn.*;
import org.idp.server.authenticators.webauthn.service.internal.WebAuthnConfigurationService;
import org.idp.server.authenticators.webauthn.service.internal.WebAuthnCredentialService;
import org.idp.server.authenticators.webauthn.service.internal.WebAuthnSessionService;
import org.idp.server.core.UserManagementApi;
import org.idp.server.core.oauth.identity.User;
import org.idp.server.core.tenant.Tenant;

public class WebAuthnService {

  WebAuthnConfigurationService webAuthnConfigurationService;
  WebAuthnSessionService webAuthnSessionService;
  WebAuthnCredentialService webAuthnCredentialService;
  UserManagementApi userManagementApi;

  public WebAuthnService(
      WebAuthnConfigurationService webAuthnConfigurationService,
      WebAuthnSessionService webAuthnSessionService,
      WebAuthnCredentialService webAuthnCredentialService,
      UserManagementApi userManagementApi) {
    this.webAuthnConfigurationService = webAuthnConfigurationService;
    this.webAuthnSessionService = webAuthnSessionService;
    this.webAuthnCredentialService = webAuthnCredentialService;
    this.userManagementApi = userManagementApi;
  }

  public WebAuthnSession challengeRegistration(Tenant tenant) {
    webAuthnConfigurationService.get(tenant);

    return webAuthnSessionService.start();
  }

  public WebAuthnCredential verifyRegistration(Tenant tenant, User user, String request) {

    WebAuthnConfiguration configuration = webAuthnConfigurationService.get(tenant);
    WebAuthnSession session = webAuthnSessionService.get();

    WebAuthnRegistrationManager manager =
        new WebAuthnRegistrationManager(configuration, session, request, user.sub());

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
        new WebAuthnAuthenticationManager(configuration, session, request);

    String extractUserId = manager.extractUserId();
    WebAuthnCredentials webAuthnCredentials = webAuthnCredentialService.findAll(extractUserId);

    manager.verify(webAuthnCredentials);

    return userManagementApi.get(extractUserId);
  }
}
