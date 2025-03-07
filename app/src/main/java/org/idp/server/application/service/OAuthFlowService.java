package org.idp.server.application.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.idp.server.application.service.authentication.EmailAuthenticationService;
import org.idp.server.application.service.authentication.WebAuthnService;
import org.idp.server.application.service.authorization.OAuthSessionService;
import org.idp.server.application.service.federation.FederationService;
import org.idp.server.application.service.tenant.TenantService;
import org.idp.server.application.service.user.UserAuthenticationService;
import org.idp.server.application.service.user.UserRegistrationService;
import org.idp.server.core.IdpServerApplication;
import org.idp.server.core.api.OAuthApi;
import org.idp.server.core.basic.date.SystemDateTime;
import org.idp.server.core.handler.federation.io.FederationRequest;
import org.idp.server.core.handler.federation.io.FederationRequestResponse;
import org.idp.server.core.handler.oauth.io.*;
import org.idp.server.core.oauth.OAuthSession;
import org.idp.server.core.oauth.authentication.Authentication;
import org.idp.server.core.oauth.identity.User;
import org.idp.server.core.oauth.interaction.UserInteraction;
import org.idp.server.core.oauth.request.AuthorizationRequest;
import org.idp.server.core.oauth.request.AuthorizationRequestIdentifier;
import org.idp.server.core.sharedsignal.DefaultEventType;
import org.idp.server.core.sharedsignal.Event;
import org.idp.server.core.sharedsignal.OAuthFlowEventCreator;
import org.idp.server.core.type.extension.OAuthDenyReason;
import org.idp.server.core.type.extension.Pairs;
import org.idp.server.domain.model.authentication.EmailVerificationChallenge;
import org.idp.server.domain.model.authentication.EmailVerificationChallengeNotFoundException;
import org.idp.server.domain.model.tenant.Tenant;
import org.idp.server.domain.model.tenant.TenantIdentifier;
import org.idp.server.domain.model.user.UserRegistration;
import org.idp.server.subdomain.webauthn.WebAuthnCredential;
import org.idp.server.subdomain.webauthn.WebAuthnSession;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class OAuthFlowService {

  OAuthApi oAuthApi;
  OAuthSessionService oAuthSessionService;
  UserRegistrationService userRegistrationService;
  UserAuthenticationService userAuthenticationService;
  TenantService tenantService;
  WebAuthnService webAuthnService;
  EmailAuthenticationService emailAuthenticationService;
  FederationService federationService;
  ApplicationEventPublisher eventPublisher;

  public OAuthFlowService(
      IdpServerApplication idpServerApplication,
      OAuthSessionService oAuthSessionService,
      UserRegistrationService userRegistrationService,
      UserAuthenticationService userAuthenticationService,
      TenantService tenantService,
      WebAuthnService webAuthnService,
      EmailAuthenticationService emailAuthenticationService,
      FederationService federationService,
      ApplicationEventPublisher eventPublisher) {
    this.oAuthApi = idpServerApplication.oAuthApi();
    oAuthApi.setOAuthRequestDelegate(oAuthSessionService);
    this.oAuthSessionService = oAuthSessionService;
    this.userRegistrationService = userRegistrationService;
    this.userAuthenticationService = userAuthenticationService;
    this.tenantService = tenantService;
    this.webAuthnService = webAuthnService;
    this.emailAuthenticationService = emailAuthenticationService;
    this.federationService = federationService;
    this.eventPublisher = eventPublisher;
  }

  public Pairs<Tenant, OAuthRequestResponse> request(
      TenantIdentifier tenantIdentifier, Map<String, String[]> params) {

    Tenant tenant = tenantService.get(tenantIdentifier);
    OAuthRequest oAuthRequest = new OAuthRequest(params, tenant.issuer());

    OAuthRequestResponse request = oAuthApi.request(oAuthRequest);
    return new Pairs<>(tenant, request);
  }

  public OAuthViewDataResponse getViewData(
      TenantIdentifier tenantIdentifier, String oauthRequestIdentifier) {

    Tenant tenant = tenantService.get(tenantIdentifier);
    OAuthViewDataRequest oAuthViewDataRequest =
        new OAuthViewDataRequest(oauthRequestIdentifier, tenant.issuer());

    return oAuthApi.getViewData(oAuthViewDataRequest);
  }

  public FederationRequestResponse requestFederation(
      TenantIdentifier tenantIdentifier,
      String oauthRequestIdentifier,
      String federationIdentifier) {

    Tenant tenant = tenantService.get(tenantIdentifier);
    AuthorizationRequest authorizationRequest =
        oAuthApi.get(new AuthorizationRequestIdentifier(oauthRequestIdentifier));

    return federationService.request(new FederationRequest(federationIdentifier));
  }

  public User requestSignup(
      TenantIdentifier tenantIdentifier,
      String oauthRequestIdentifier,
      UserRegistration userRegistration) {

    Tenant tenant = tenantService.get(tenantIdentifier);
    AuthorizationRequest authorizationRequest =
        oAuthApi.get(new AuthorizationRequestIdentifier(oauthRequestIdentifier));

    User user = userRegistrationService.create(tenant, userRegistration);

    OAuthSession oAuthSession =
        new OAuthSession(
            authorizationRequest.sessionKey(),
            user,
            new Authentication(),
            SystemDateTime.now().plusSeconds(3600));
    oAuthSessionService.registerSession(oAuthSession);

    publishEvent(authorizationRequest, user, DefaultEventType.signup);

    return user;
  }

  public void challengeEmailVerification(
      TenantIdentifier tenantIdentifier, String oauthRequestIdentifier) {
    Tenant tenant = tenantService.get(tenantIdentifier);
    AuthorizationRequest authorizationRequest =
        oAuthApi.get(new AuthorizationRequestIdentifier(oauthRequestIdentifier));
    OAuthSession oAuthSession = oAuthSessionService.findSession(authorizationRequest.sessionKey());

    EmailVerificationChallenge emailVerificationChallenge =
        emailAuthenticationService.challenge(oAuthSession.user());

    HashMap<String, Object> attributes = new HashMap<>();
    attributes.put("emailVerificationChallenge", emailVerificationChallenge);
    OAuthSession updatedSession = oAuthSession.addAttribute(attributes);

    oAuthSessionService.updateSession(updatedSession);
  }

  public void verifyEmail(
      TenantIdentifier tenantIdentifier, String oauthRequestIdentifier, String verificationCode) {
    Tenant tenant = tenantService.get(tenantIdentifier);

    AuthorizationRequest authorizationRequest =
        oAuthApi.get(new AuthorizationRequestIdentifier(oauthRequestIdentifier));

    OAuthSession oAuthSession = oAuthSessionService.findSession(authorizationRequest.sessionKey());
    if (!oAuthSession.hasAttribute("emailVerificationChallenge")) {
      throw new EmailVerificationChallengeNotFoundException(
          "emailVerificationChallenge is not found");
    }
    EmailVerificationChallenge emailVerificationChallenge =
        (EmailVerificationChallenge) oAuthSession.getAttribute("emailVerificationChallenge");

    emailAuthenticationService.verify(verificationCode, emailVerificationChallenge);

    OAuthSession updatedSession =
        oAuthSession.didEmailAuthentication(authorizationRequest.sessionKey());
    oAuthSessionService.updateSession(updatedSession);

    publishEvent(
        authorizationRequest, oAuthSession.user(), DefaultEventType.email_verification_success);
  }

  public WebAuthnSession challengeWebAuthnRegistration(
      TenantIdentifier tenantIdentifier, String oauthRequestIdentifier) {
    Tenant tenant = tenantService.get(tenantIdentifier);
    AuthorizationRequest authorizationRequest =
        oAuthApi.get(new AuthorizationRequestIdentifier(oauthRequestIdentifier));
    OAuthSession oAuthSession = oAuthSessionService.findSession(authorizationRequest.sessionKey());

    WebAuthnSession webAuthnSession = webAuthnService.challengeRegistration(tenant);

    return webAuthnSession;
  }

  public void verifyWebAuthnRegistration(
      TenantIdentifier tenantIdentifier, String oauthRequestIdentifier, String request) {

    Tenant tenant = tenantService.get(tenantIdentifier);
    AuthorizationRequest authorizationRequest =
        oAuthApi.get(new AuthorizationRequestIdentifier(oauthRequestIdentifier));
    OAuthSession oAuthSession = oAuthSessionService.findSession(authorizationRequest.sessionKey());

    WebAuthnCredential webAuthnCredential =
        webAuthnService.verifyRegistration(tenant, oAuthSession.user(), request);

    oAuthSessionService.updateSession(oAuthSession);

    publishEvent(
        authorizationRequest, oAuthSession.user(), DefaultEventType.webauthn_registration_success);
  }

  public void authenticateWithPassword(
      TenantIdentifier tenantIdentifier,
      String oauthRequestIdentifier,
      String username,
      String password) {

    Tenant tenant = tenantService.get(tenantIdentifier);
    AuthorizationRequest authorizationRequest =
        oAuthApi.get(new AuthorizationRequestIdentifier(oauthRequestIdentifier));

    User user = userAuthenticationService.authenticateWithPassword(tenant, username, password);
    OAuthSession session = oAuthSessionService.findSession(authorizationRequest.sessionKey());
    OAuthSession updatedSession =
        session.didAuthenticationPassword(authorizationRequest.sessionKey(), user);

    oAuthSessionService.updateSession(updatedSession);

    publishEvent(authorizationRequest, user, DefaultEventType.password_success);
  }

  public WebAuthnSession challengeWebAuthnAuthentication(
      TenantIdentifier tenantIdentifier, String oauthRequestIdentifier) {
    Tenant tenant = tenantService.get(tenantIdentifier);
    AuthorizationRequest authorizationRequest =
        oAuthApi.get(new AuthorizationRequestIdentifier(oauthRequestIdentifier));
    OAuthSession oAuthSession = oAuthSessionService.findSession(authorizationRequest.sessionKey());

    WebAuthnSession webAuthnSession = webAuthnService.challengeAuthentication(tenant);

    return webAuthnSession;
  }

  public void verifyWebAuthnAuthentication(
      TenantIdentifier tenantIdentifier, String oauthRequestIdentifier, String request) {

    Tenant tenant = tenantService.get(tenantIdentifier);
    AuthorizationRequest authorizationRequest =
        oAuthApi.get(new AuthorizationRequestIdentifier(oauthRequestIdentifier));
    OAuthSession oAuthSession = oAuthSessionService.findSession(authorizationRequest.sessionKey());

    User user = webAuthnService.verifyAuthentication(tenant, request);
    OAuthSession didWebAuthnAuthenticationSession =
        oAuthSession.didWebAuthnAuthentication(authorizationRequest.sessionKey(), user);

    oAuthSessionService.updateSession(didWebAuthnAuthenticationSession);

    publishEvent(authorizationRequest, user, DefaultEventType.webauthn_success);
  }

  public OAuthAuthorizeResponse authorize(
      TenantIdentifier tenantIdentifier, String oauthRequestIdentifier, String action) {

    Tenant tenant = tenantService.get(tenantIdentifier);
    AuthorizationRequest authorizationRequest =
        oAuthApi.get(new AuthorizationRequestIdentifier(oauthRequestIdentifier));
    OAuthSession session = oAuthSessionService.findSession(authorizationRequest.sessionKey());

    OAuthAuthorizeRequest oAuthAuthorizeRequest =
        new OAuthAuthorizeRequest(
            oauthRequestIdentifier, tenant.issuer(), session.user(), session.authentication());

    if (authorizationRequest.isPromptCreate() || action.equals("signup")) {
      userRegistrationService.register(tenant, session.user());
    }

    OAuthAuthorizeResponse authorize = oAuthApi.authorize(oAuthAuthorizeRequest);

    publishEvent(authorizationRequest, session.user(), DefaultEventType.login);

    return authorize;
  }

  // FIXME this is bad code
  public OAuthAuthorizeResponse authorizeWithSession(
      TenantIdentifier tenantIdentifier, String oauthRequestIdentifier) {

    Tenant tenant = tenantService.get(tenantIdentifier);
    AuthorizationRequest authorizationRequest =
        oAuthApi.get(new AuthorizationRequestIdentifier(oauthRequestIdentifier));
    OAuthSession session = oAuthSessionService.findSession(authorizationRequest.sessionKey());

    UserInteraction userInteraction = interact(tenant, "", "", session);
    OAuthAuthorizeRequest authAuthorizeRequest =
        new OAuthAuthorizeRequest(
            oauthRequestIdentifier,
            tenant.issuer(),
            userInteraction.user(),
            userInteraction.authentication());

    OAuthAuthorizeResponse authorize = oAuthApi.authorize(authAuthorizeRequest);

    publishEvent(authorizationRequest, session.user(), DefaultEventType.login_with_session);

    return authorize;
  }

  public OAuthDenyResponse deny(TenantIdentifier tenantIdentifier, String oauthRequestIdentifier) {

    Tenant tenant = tenantService.get(tenantIdentifier);
    OAuthDenyRequest denyRequest =
        new OAuthDenyRequest(
            oauthRequestIdentifier, tenant.issuer(), OAuthDenyReason.access_denied);

    return oAuthApi.deny(denyRequest);
  }

  public OAuthLogoutResponse logout(
      TenantIdentifier tenantIdentifier, Map<String, String[]> params) {

    Tenant tenant = tenantService.get(tenantIdentifier);
    OAuthLogoutRequest oAuthLogoutRequest = new OAuthLogoutRequest(params, tenant.issuer());

    return oAuthApi.logout(oAuthLogoutRequest);
  }

  private void publishEvent(
      AuthorizationRequest authorizationRequest, User user, DefaultEventType login_with_session) {
    OAuthFlowEventCreator eventCreator =
        new OAuthFlowEventCreator(authorizationRequest, user, login_with_session);
    Event event = eventCreator.create();
    eventPublisher.publishEvent(event);
  }

  private UserInteraction interact(
      Tenant tenant, String username, String password, OAuthSession session) {

    if (Objects.nonNull(session) && !session.isExpire(SystemDateTime.now())) {
      Authentication authentication = session.authentication();
      User user = session.user();
      return new UserInteraction(user, authentication);
    }

    User authenticated =
        userAuthenticationService.authenticateWithPassword(tenant, username, password);
    Authentication authentication =
        new Authentication()
            .setTime(SystemDateTime.now())
            .addMethods(List.of("pwd"))
            .addAcrValues(List.of("urn:mace:incommon:iap:silver"));
    return new UserInteraction(authenticated, authentication);
  }
}
