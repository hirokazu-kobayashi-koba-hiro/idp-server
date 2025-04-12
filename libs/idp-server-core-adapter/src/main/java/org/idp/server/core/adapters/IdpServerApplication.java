package org.idp.server.core.adapters;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.*;
import org.idp.server.core.adapters.datasource.token.OAuthTokenDataSource;
import org.idp.server.core.adapters.httpclient.ciba.NotificationClient;
import org.idp.server.core.adapters.httpclient.oauth.RequestObjectHttpClient;
import org.idp.server.core.admin.*;
import org.idp.server.core.authentication.*;
import org.idp.server.core.authentication.webauthn.WebAuthnExecutorLoader;
import org.idp.server.core.authentication.webauthn.WebAuthnExecutors;
import org.idp.server.core.basic.datasource.DataSourceDependencyContainer;
import org.idp.server.core.basic.datasource.DataSourceDependencyContainerLoader;
import org.idp.server.core.basic.sql.DatabaseConfig;
import org.idp.server.core.basic.sql.TransactionInterceptor;
import org.idp.server.core.basic.sql.TransactionManager;
import org.idp.server.core.ciba.CibaFlowApi;
import org.idp.server.core.ciba.CibaProtocol;
import org.idp.server.core.ciba.CibaProtocolImpl;
import org.idp.server.core.ciba.gateway.ClientNotificationGateway;
import org.idp.server.core.ciba.repository.BackchannelAuthenticationRequestRepository;
import org.idp.server.core.ciba.repository.CibaGrantRepository;
import org.idp.server.core.configuration.ClientConfigurationRepository;
import org.idp.server.core.configuration.ServerConfigurationRepository;
import org.idp.server.core.configuration.handler.ClientConfigurationHandler;
import org.idp.server.core.discovery.*;
import org.idp.server.core.federation.FederationDependencyContainer;
import org.idp.server.core.federation.FederationDependencyContainerLoader;
import org.idp.server.core.federation.FederationInteractorLoader;
import org.idp.server.core.federation.FederationInteractors;
import org.idp.server.core.federation.oidc.OidcSsoExecutorLoader;
import org.idp.server.core.federation.oidc.OidcSsoExecutors;
import org.idp.server.core.grantmangment.AuthorizationGrantedRepository;
import org.idp.server.core.notification.EmailSenderLoader;
import org.idp.server.core.notification.EmailSenders;
import org.idp.server.core.oauth.OAuthFlowApi;
import org.idp.server.core.oauth.OAuthProtocol;
import org.idp.server.core.oauth.OAuthProtocolImpl;
import org.idp.server.core.oauth.OAuthRequestDelegate;
import org.idp.server.core.oauth.gateway.RequestObjectGateway;
import org.idp.server.core.oauth.identity.PasswordEncodeDelegation;
import org.idp.server.core.oauth.identity.PasswordVerificationDelegation;
import org.idp.server.core.oauth.identity.UserRegistrationService;
import org.idp.server.core.oauth.identity.UserRepository;
import org.idp.server.core.oauth.identity.permission.PermissionCommandRepository;
import org.idp.server.core.oauth.identity.role.RoleCommandRepository;
import org.idp.server.core.oauth.repository.AuthorizationCodeGrantRepository;
import org.idp.server.core.oauth.repository.AuthorizationRequestRepository;
import org.idp.server.core.organization.OrganizationRepository;
import org.idp.server.core.security.SecurityEventApi;
import org.idp.server.core.security.SecurityEventHooks;
import org.idp.server.core.security.SecurityEventPublisher;
import org.idp.server.core.security.event.OAuthFlowEventPublisher;
import org.idp.server.core.security.event.SecurityEventRepository;
import org.idp.server.core.security.hook.SecurityEventHookConfigurationQueryRepository;
import org.idp.server.core.security.hook.SecurityEventHooksLoader;
import org.idp.server.core.tenant.TenantRepository;
import org.idp.server.core.token.*;
import org.idp.server.core.token.repository.OAuthTokenRepository;
import org.idp.server.core.userinfo.UserinfoApi;
import org.idp.server.core.userinfo.UserinfoProtocol;
import org.idp.server.core.userinfo.UserinfoProtocolImpl;

/** IdpServerApplication */
public class IdpServerApplication {

  IdpServerStarterApi idpServerStarterApi;
  OAuthFlowApi oAuthFlowApi;
  TokenApi tokenApi;
  OidcMetaDataApi oidcMetaDataApi;
  UserinfoApi userinfoApi;
  CibaFlowApi cibaFlowApi;
  SecurityEventApi securityEventApi;
  OnboardingApi onboardingApi;
  ServerManagementApi serverManagementApi;
  ClientManagementApi clientManagementApi;
  UserManagementApi userManagementApi;
  OperatorAuthenticationApi operatorAuthenticationApi;

  public IdpServerApplication(
      DatabaseConfig databaseConfig,
      String encryptionKey,
      OAuthRequestDelegate oAuthRequestDelegate,
      PasswordEncodeDelegation passwordEncodeDelegation,
      PasswordVerificationDelegation passwordVerificationDelegation,
      SecurityEventPublisher securityEventPublisher) {

    TransactionManager.setConnectionConfig(
        databaseConfig.url(), databaseConfig.username(), databaseConfig.password());

    DataSourceDependencyContainer dataSourceDependencyContainer =
        DataSourceDependencyContainerLoader.load();

    AuthorizationRequestRepository authorizationRequestRepository =
        dataSourceDependencyContainer.resolve(AuthorizationRequestRepository.class);
    AuthorizationCodeGrantRepository authorizationCodeGrantRepository =
        dataSourceDependencyContainer.resolve(AuthorizationCodeGrantRepository.class);
    AuthorizationGrantedRepository authorizationGrantedRepository =
        dataSourceDependencyContainer.resolve(AuthorizationGrantedRepository.class);

    // TODO fix dependencies
    OAuthTokenRepository oAuthTokenRepository = new OAuthTokenDataSource(encryptionKey);

    ServerConfigurationRepository serverConfigurationRepository =
        dataSourceDependencyContainer.resolve(ServerConfigurationRepository.class);
    ClientConfigurationRepository clientConfigurationRepository =
        dataSourceDependencyContainer.resolve(ClientConfigurationRepository.class);
    SecurityEventRepository securityEventRepository =
        dataSourceDependencyContainer.resolve(SecurityEventRepository.class);
    UserRepository userRepository = dataSourceDependencyContainer.resolve(UserRepository.class);
    OrganizationRepository organizationRepository =
        dataSourceDependencyContainer.resolve(OrganizationRepository.class);
    TenantRepository tenantRepository =
        dataSourceDependencyContainer.resolve(TenantRepository.class);
    RoleCommandRepository roleCommandRepository =
        dataSourceDependencyContainer.resolve(RoleCommandRepository.class);
    PermissionCommandRepository permissionCommandRepository =
        dataSourceDependencyContainer.resolve(PermissionCommandRepository.class);
    SecurityEventHookConfigurationQueryRepository hookQueryRepository =
        dataSourceDependencyContainer.resolve(SecurityEventHookConfigurationQueryRepository.class);
    BackchannelAuthenticationRequestRepository backchannelAuthenticationRepository =
        dataSourceDependencyContainer.resolve(BackchannelAuthenticationRequestRepository.class);
    CibaGrantRepository cibaGrantRepository =
        dataSourceDependencyContainer.resolve(CibaGrantRepository.class);

    RequestObjectGateway requestObjectGateway = new RequestObjectHttpClient();

    OAuthProtocol oAuthProtocol =
        new OAuthProtocolImpl(
            authorizationRequestRepository,
            serverConfigurationRepository,
            clientConfigurationRepository,
            requestObjectGateway,
            authorizationGrantedRepository,
            authorizationCodeGrantRepository,
            oAuthTokenRepository,
            oAuthRequestDelegate);

    UserRegistrationService userRegistrationService = new UserRegistrationService(userRepository);

    TokenIntrospectionProtocol tokenIntrospectionProtocol =
        new TokenIntrospectionProtocolImpl(oAuthTokenRepository);

    TokenRevocationProtocol tokenRevocationProtocol =
        new TokenRevocationProtocolImpl(
            oAuthTokenRepository, serverConfigurationRepository, clientConfigurationRepository);

    UserinfoProtocol userinfoProtocol =
        new UserinfoProtocolImpl(
            oAuthTokenRepository, serverConfigurationRepository, clientConfigurationRepository);

    DiscoveryProtocol discoveryProtocol = new DiscoveryProtocolImpl(serverConfigurationRepository);
    JwksProtocol jwksProtocol = new JwksProtocolImpl(serverConfigurationRepository);

    ClientNotificationGateway notificationGateway = new NotificationClient();
    CibaProtocol cibaProtocol =
        new CibaProtocolImpl(
            backchannelAuthenticationRepository,
            cibaGrantRepository,
            authorizationGrantedRepository,
            oAuthTokenRepository,
            serverConfigurationRepository,
            clientConfigurationRepository,
            notificationGateway);

    TokenProtocol tokenProtocol =
        new TokenProtocolImpl(
            authorizationRequestRepository,
            authorizationCodeGrantRepository,
            authorizationGrantedRepository,
            backchannelAuthenticationRepository,
            cibaGrantRepository,
            oAuthTokenRepository,
            serverConfigurationRepository,
            clientConfigurationRepository);

    SecurityEventHooks securityEventHooks = SecurityEventHooksLoader.load();

    // create mfa instance
    AuthenticationDependencyContainer authenticationDependencyContainer =
        AuthenticationDependencyContainerLoader.load();
    authenticationDependencyContainer.register(
        PasswordEncodeDelegation.class, passwordEncodeDelegation);
    authenticationDependencyContainer.register(
        PasswordVerificationDelegation.class, passwordVerificationDelegation);
    EmailSenders emailSenders = EmailSenderLoader.load();
    authenticationDependencyContainer.register(EmailSenders.class, emailSenders);
    WebAuthnExecutors webAuthnExecutors =
        WebAuthnExecutorLoader.load(authenticationDependencyContainer);
    authenticationDependencyContainer.register(WebAuthnExecutors.class, webAuthnExecutors);

    Map<AuthenticationInteractionType, AuthenticationInteractor> loadedInteractors =
        AuthenticationInteractorLoader.load(authenticationDependencyContainer);
    HashMap<AuthenticationInteractionType, AuthenticationInteractor> interactors =
        new HashMap<>(loadedInteractors);

    AuthenticationInteractors authenticationInteractors =
        new AuthenticationInteractors(interactors);

    this.idpServerStarterApi =
        TransactionInterceptor.createProxy(
            new IdpServerStarterEntryService(
                organizationRepository,
                tenantRepository,
                userRepository,
                permissionCommandRepository,
                roleCommandRepository,
                serverConfigurationRepository,
                passwordEncodeDelegation),
            IdpServerStarterApi.class);

    OAuthFlowEventPublisher oAuthFLowEventPublisher =
        new OAuthFlowEventPublisher(securityEventPublisher);

    OidcSsoExecutors oidcSsoExecutors = OidcSsoExecutorLoader.load();
    FederationDependencyContainer federationDependencyContainer =
        FederationDependencyContainerLoader.load();
    federationDependencyContainer.register(OidcSsoExecutors.class, oidcSsoExecutors);
    FederationInteractors federationInteractors =
        FederationInteractorLoader.load(federationDependencyContainer);

    this.oAuthFlowApi =
        TransactionInterceptor.createProxy(
            new OAuthFlowEntryService(
                oAuthProtocol,
                oAuthRequestDelegate,
                authenticationInteractors,
                federationInteractors,
                userRepository,
                userRegistrationService,
                tenantRepository,
                oAuthFLowEventPublisher),
            OAuthFlowApi.class);

    this.tokenApi =
        TransactionInterceptor.createProxy(
            new TokenEntryService(
                tokenProtocol,
                tokenIntrospectionProtocol,
                tokenRevocationProtocol,
                userRepository,
                tenantRepository,
                passwordVerificationDelegation),
            TokenApi.class);

    this.oidcMetaDataApi =
        TransactionInterceptor.createProxy(
            new OidcMetaDataEntryService(tenantRepository, discoveryProtocol, jwksProtocol),
            OidcMetaDataApi.class);

    this.userinfoApi =
        TransactionInterceptor.createProxy(
            new UserinfoEntryService(userinfoProtocol, userRepository, tenantRepository),
            UserinfoApi.class);

    this.cibaFlowApi =
        TransactionInterceptor.createProxy(
            new CibaFlowEntryService(cibaProtocol, userRepository, tenantRepository),
            CibaFlowApi.class);

    this.securityEventApi =
        TransactionInterceptor.createProxy(
            new SecurityEventEntryService(
                tenantRepository, securityEventRepository, securityEventHooks, hookQueryRepository),
            SecurityEventApi.class);

    this.onboardingApi =
        TransactionInterceptor.createProxy(
            new OnboardingEntryService(
                tenantRepository,
                organizationRepository,
                userRegistrationService,
                serverConfigurationRepository),
            OnboardingApi.class);

    this.serverManagementApi =
        TransactionInterceptor.createProxy(
            new ServerManagementEntryService(tenantRepository, serverConfigurationRepository),
            ServerManagementApi.class);

    this.clientManagementApi =
        TransactionInterceptor.createProxy(
            new ClientManagementEntryService(
                tenantRepository, new ClientConfigurationHandler(clientConfigurationRepository)),
            ClientManagementApi.class);

    this.userManagementApi =
        TransactionInterceptor.createProxy(
            new UserManagementEntryService(tenantRepository, userRepository),
            UserManagementApi.class);

    this.operatorAuthenticationApi =
        TransactionInterceptor.createProxy(
            new OperatorAuthenticationEntryService(
                tokenIntrospectionProtocol, tenantRepository, userRepository),
            OperatorAuthenticationApi.class);
  }

  public OAuthFlowApi oAuthFlowApi() {
    return oAuthFlowApi;
  }

  public TokenApi tokenAPi() {
    return tokenApi;
  }

  public OidcMetaDataApi oidcMetaDataApi() {
    return oidcMetaDataApi;
  }

  public UserinfoApi userinfoApi() {
    return userinfoApi;
  }

  public CibaFlowApi cibaFlowApi() {
    return cibaFlowApi;
  }

  public SecurityEventApi securityEventApi() {
    return securityEventApi;
  }

  public OnboardingApi onboardingApi() {
    return onboardingApi;
  }

  public ServerManagementApi serverManagementApi() {
    return serverManagementApi;
  }

  public ClientManagementApi clientManagementApi() {
    return clientManagementApi;
  }

  public UserManagementApi userManagementAPi() {
    return userManagementApi;
  }

  public OperatorAuthenticationApi operatorAuthenticationApi() {
    return operatorAuthenticationApi;
  }

  public IdpServerStarterApi idpServerStarterApi() {
    return idpServerStarterApi;
  }
}
