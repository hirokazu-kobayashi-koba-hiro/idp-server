package org.idp.server.core.adapters;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.core.*;
import org.idp.server.core.adapters.datasource.ciba.database.grant.CibaGrantDataSource;
import org.idp.server.core.adapters.datasource.ciba.database.request.BackchannelAuthenticationDataSource;
import org.idp.server.core.adapters.datasource.configuration.database.client.ClientConfigurationDataSource;
import org.idp.server.core.adapters.datasource.configuration.database.server.ServerConfigurationDataSource;
import org.idp.server.core.adapters.datasource.credential.database.VerifiableCredentialTransactionDataSource;
import org.idp.server.core.adapters.datasource.grantmanagment.AuthorizationGrantedDataSource;
import org.idp.server.core.adapters.datasource.identity.PermissionCommandDataSource;
import org.idp.server.core.adapters.datasource.identity.RoleCommandDataSource;
import org.idp.server.core.adapters.datasource.identity.UserDataSource;
import org.idp.server.core.adapters.datasource.oauth.database.code.AuthorizationCodeGrantDataSource;
import org.idp.server.core.adapters.datasource.oauth.database.request.AuthorizationRequestDataSource;
import org.idp.server.core.adapters.datasource.organization.OrganizationDataSource;
import org.idp.server.core.adapters.datasource.security.SecurityEventDataSource;
import org.idp.server.core.adapters.datasource.security.SecurityEventHookConfigurationQueryDataSource;
import org.idp.server.core.adapters.datasource.tenant.TenantDataSource;
import org.idp.server.core.adapters.datasource.token.database.OAuthTokenDataSource;
import org.idp.server.core.adapters.httpclient.ciba.NotificationClient;
import org.idp.server.core.adapters.httpclient.credential.VerifiableCredentialBlockCertClient;
import org.idp.server.core.adapters.httpclient.credential.VerifiableCredentialJwtClient;
import org.idp.server.core.adapters.httpclient.oauth.RequestObjectHttpClient;
import org.idp.server.core.api.*;
import org.idp.server.core.authentication.*;
import org.idp.server.core.authentication.webauthn.WebAuthnExecutorLoader;
import org.idp.server.core.authentication.webauthn.WebAuthnExecutors;
import org.idp.server.core.basic.sql.TransactionInterceptor;
import org.idp.server.core.basic.sql.TransactionManager;
import org.idp.server.core.federation.FederationDependencyContainer;
import org.idp.server.core.federation.FederationDependencyContainerLoader;
import org.idp.server.core.federation.FederationInteractorLoader;
import org.idp.server.core.federation.FederationInteractors;
import org.idp.server.core.federation.oidc.OidcSsoExecutorLoader;
import org.idp.server.core.federation.oidc.OidcSsoExecutors;
import org.idp.server.core.handler.ciba.CibaAuthorizeHandler;
import org.idp.server.core.handler.ciba.CibaDenyHandler;
import org.idp.server.core.handler.ciba.CibaRequestHandler;
import org.idp.server.core.handler.config.DatabaseConfig;
import org.idp.server.core.handler.configuration.ClientConfigurationHandler;
import org.idp.server.core.handler.configuration.ServerConfigurationHandler;
import org.idp.server.core.handler.credential.CredentialHandler;
import org.idp.server.core.handler.discovery.DiscoveryHandler;
import org.idp.server.core.handler.oauth.OAuthAuthorizeHandler;
import org.idp.server.core.handler.oauth.OAuthDenyHandler;
import org.idp.server.core.handler.oauth.OAuthHandler;
import org.idp.server.core.handler.oauth.OAuthRequestHandler;
import org.idp.server.core.handler.security.SecurityEventHandler;
import org.idp.server.core.handler.token.TokenRequestHandler;
import org.idp.server.core.handler.tokenintrospection.TokenIntrospectionHandler;
import org.idp.server.core.handler.tokenrevocation.TokenRevocationHandler;
import org.idp.server.core.handler.userinfo.UserinfoHandler;
import org.idp.server.core.notification.EmailSenderLoader;
import org.idp.server.core.notification.EmailSenders;
import org.idp.server.core.oauth.OAuthRequestDelegate;
import org.idp.server.core.oauth.identity.PasswordEncodeDelegation;
import org.idp.server.core.oauth.identity.PasswordVerificationDelegation;
import org.idp.server.core.oauth.identity.UserRegistrationService;
import org.idp.server.core.protocol.*;
import org.idp.server.core.security.SecurityEventHooks;
import org.idp.server.core.security.SecurityEventPublisher;
import org.idp.server.core.security.event.OAuthFlowEventPublisher;
import org.idp.server.core.security.hook.SecurityEventHooksLoader;
import org.idp.server.core.type.verifiablecredential.Format;
import org.idp.server.core.verifiablecredential.VerifiableCredentialCreator;
import org.idp.server.core.verifiablecredential.VerifiableCredentialCreators;

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

    AuthorizationRequestDataSource authorizationRequestDataSource =
        new AuthorizationRequestDataSource();
    AuthorizationCodeGrantDataSource authorizationCodeGrantDataSource =
        new AuthorizationCodeGrantDataSource();
    AuthorizationGrantedDataSource authorizationGrantedDataSource =
        new AuthorizationGrantedDataSource();
    OAuthTokenDataSource oAuthTokenDataSource = new OAuthTokenDataSource(encryptionKey);
    ServerConfigurationDataSource serverConfigurationDataSource =
        new ServerConfigurationDataSource();
    ClientConfigurationDataSource clientConfigurationDataSource =
        new ClientConfigurationDataSource();
    VerifiableCredentialTransactionDataSource verifiableCredentialTransactionDataSource =
        new VerifiableCredentialTransactionDataSource();
    SecurityEventDataSource eventDataSource = new SecurityEventDataSource();
    UserDataSource userDataSource = new UserDataSource();
    OrganizationDataSource organizationDataSource = new OrganizationDataSource();
    TenantDataSource tenantDataSource = new TenantDataSource();
    RoleCommandDataSource roleCommandDataSource = new RoleCommandDataSource();
    PermissionCommandDataSource permissionCommandDataSource = new PermissionCommandDataSource();
    SecurityEventHookConfigurationQueryDataSource hookQueryDataSource =
        new SecurityEventHookConfigurationQueryDataSource();

    OAuthRequestHandler oAuthRequestHandler =
        new OAuthRequestHandler(
            authorizationRequestDataSource,
            serverConfigurationDataSource,
            clientConfigurationDataSource,
            new RequestObjectHttpClient(),
            authorizationGrantedDataSource);
    OAuthAuthorizeHandler oAuthAuthorizeHandler =
        new OAuthAuthorizeHandler(
            authorizationRequestDataSource,
            authorizationCodeGrantDataSource,
            oAuthTokenDataSource,
            serverConfigurationDataSource,
            clientConfigurationDataSource);
    OAuthDenyHandler oAuthDenyHandler =
        new OAuthDenyHandler(
            authorizationRequestDataSource,
            serverConfigurationDataSource,
            clientConfigurationDataSource);
    OAuthHandler oAuthHandler =
        new OAuthHandler(
            authorizationRequestDataSource,
            serverConfigurationDataSource,
            clientConfigurationDataSource);

    OAuthProtocol oAuthProtocol =
        new OAuthProtocolImpl(
            oAuthRequestHandler,
            oAuthAuthorizeHandler,
            oAuthDenyHandler,
            oAuthHandler,
            oAuthRequestDelegate);
    UserRegistrationService userRegistrationService = new UserRegistrationService(userDataSource);

    TokenIntrospectionHandler tokenIntrospectionHandler =
        new TokenIntrospectionHandler(oAuthTokenDataSource);
    TokenIntrospectionProtocol tokenIntrospectionProtocol =
        new TokenIntrospectionProtocolImpl(tokenIntrospectionHandler);
    TokenRevocationHandler tokenRevocationHandler =
        new TokenRevocationHandler(
            oAuthTokenDataSource, serverConfigurationDataSource, clientConfigurationDataSource);

    TokenRevocationProtocol tokenRevocationProtocol =
        new TokenRevocationProtocolImpl(tokenRevocationHandler);
    UserinfoHandler userinfoHandler =
        new UserinfoHandler(
            oAuthTokenDataSource, serverConfigurationDataSource, clientConfigurationDataSource);
    UserinfoProtocol userinfoProtocol = new UserinfoProtocolImpl(userinfoHandler);

    DiscoveryHandler discoveryHandler = new DiscoveryHandler(serverConfigurationDataSource);
    DiscoveryProtocol discoveryProtocol = new DiscoveryProtocolImpl(discoveryHandler);
    JwksProtocol jwksProtocol = new JwksProtocolImpl(discoveryHandler);

    BackchannelAuthenticationDataSource backchannelAuthenticationDataSource =
        new BackchannelAuthenticationDataSource();
    CibaGrantDataSource cibaGrantDataSource = new CibaGrantDataSource();
    NotificationClient notificationClient = new NotificationClient();
    CibaProtocol cibaProtocol =
        new CibaProtocolImpl(
            new CibaRequestHandler(
                backchannelAuthenticationDataSource,
                cibaGrantDataSource,
                serverConfigurationDataSource,
                clientConfigurationDataSource),
            new CibaAuthorizeHandler(
                backchannelAuthenticationDataSource,
                cibaGrantDataSource,
                authorizationGrantedDataSource,
                oAuthTokenDataSource,
                notificationClient,
                serverConfigurationDataSource,
                clientConfigurationDataSource),
            new CibaDenyHandler(
                cibaGrantDataSource, serverConfigurationDataSource, clientConfigurationDataSource));

    TokenRequestHandler tokenRequestHandler =
        new TokenRequestHandler(
            authorizationRequestDataSource,
            authorizationCodeGrantDataSource,
            authorizationGrantedDataSource,
            backchannelAuthenticationDataSource,
            cibaGrantDataSource,
            oAuthTokenDataSource,
            serverConfigurationDataSource,
            clientConfigurationDataSource);
    TokenProtocol tokenProtocol = new TokenProtocolImpl(tokenRequestHandler);

    Map<Format, VerifiableCredentialCreator> vcCreators = new HashMap<>();
    vcCreators.put(Format.jwt_vc_json, new VerifiableCredentialJwtClient());
    vcCreators.put(Format.ldp_vc, new VerifiableCredentialBlockCertClient());

    VerifiableCredentialCreators creators = new VerifiableCredentialCreators(vcCreators);
    CredentialHandler credentialHandler =
        new CredentialHandler(
            oAuthTokenDataSource,
            verifiableCredentialTransactionDataSource,
            serverConfigurationDataSource,
            clientConfigurationDataSource,
            creators);

    SecurityEventHooks securityEventHooks = SecurityEventHooksLoader.load();

    SecurityEventHandler securityEventHandler =
        new SecurityEventHandler(
            tenantDataSource, eventDataSource, securityEventHooks, hookQueryDataSource);

    ServerConfigurationHandler serverConfigurationHandler =
        new ServerConfigurationHandler(serverConfigurationDataSource);

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
                organizationDataSource,
                tenantDataSource,
                userDataSource,
                permissionCommandDataSource,
                roleCommandDataSource,
                serverConfigurationDataSource,
                passwordEncodeDelegation),
            IdpServerStarterApi.class);

    OAuthFlowEventPublisher oAuthFLowEventPublisher =
        new OAuthFlowEventPublisher(securityEventPublisher);

    OidcSsoExecutors oidcSsoExecutors = OidcSsoExecutorLoader.load();
    FederationDependencyContainer federationDependencyContainer = FederationDependencyContainerLoader.load();
    federationDependencyContainer.register(OidcSsoExecutors.class, oidcSsoExecutors);
    FederationInteractors federationInteractors = FederationInteractorLoader.load(federationDependencyContainer);

    this.oAuthFlowApi =
        TransactionInterceptor.createProxy(
            new OAuthFlowEntryService(
                oAuthProtocol,
                oAuthRequestDelegate,
                authenticationInteractors,
                federationInteractors,
                userDataSource,
                userRegistrationService,
                tenantDataSource,
                oAuthFLowEventPublisher),
            OAuthFlowApi.class);

    this.tokenApi =
        TransactionInterceptor.createProxy(
            new TokenEntryService(
                tokenProtocol,
                tokenIntrospectionProtocol,
                tokenRevocationProtocol,
                userDataSource,
                tenantDataSource,
                passwordVerificationDelegation),
            TokenApi.class);

    this.oidcMetaDataApi =
        TransactionInterceptor.createProxy(
            new OidcMetaDataEntryService(tenantDataSource, discoveryProtocol, jwksProtocol),
            OidcMetaDataApi.class);

    this.userinfoApi =
        TransactionInterceptor.createProxy(
            new UserinfoEntryService(userinfoProtocol, userDataSource, tenantDataSource),
            UserinfoApi.class);

    this.cibaFlowApi =
        TransactionInterceptor.createProxy(
            new CibaFlowEntryService(cibaProtocol, userDataSource, tenantDataSource),
            CibaFlowApi.class);

    this.securityEventApi =
        TransactionInterceptor.createProxy(
            new SecurityEventEntryService(securityEventHandler), SecurityEventApi.class);

    this.onboardingApi =
        TransactionInterceptor.createProxy(
            new OnboardingEntryService(
                tenantDataSource,
                organizationDataSource,
                userRegistrationService,
                serverConfigurationDataSource),
            OnboardingApi.class);

    this.serverManagementApi =
        TransactionInterceptor.createProxy(
            new ServerManagementEntryService(tenantDataSource, serverConfigurationHandler),
            ServerManagementApi.class);

    this.clientManagementApi =
        TransactionInterceptor.createProxy(
            new ClientManagementEntryService(
                tenantDataSource, new ClientConfigurationHandler(clientConfigurationDataSource)),
            ClientManagementApi.class);

    this.userManagementApi =
        TransactionInterceptor.createProxy(
            new UserManagementEntryService(tenantDataSource, userDataSource),
            UserManagementApi.class);

    this.operatorAuthenticationApi =
        TransactionInterceptor.createProxy(
            new OperatorAuthenticationEntryService(
                tokenIntrospectionProtocol, tenantDataSource, userDataSource),
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
