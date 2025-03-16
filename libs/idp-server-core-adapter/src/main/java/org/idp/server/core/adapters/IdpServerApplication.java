package org.idp.server.core.adapters;

import org.idp.server.core.*;
import org.idp.server.core.adapters.datasource.ciba.database.grant.CibaGrantDataSource;
import org.idp.server.core.adapters.datasource.ciba.database.request.BackchannelAuthenticationDataSource;
import org.idp.server.core.adapters.datasource.configuration.database.client.ClientConfigurationDataSource;
import org.idp.server.core.adapters.datasource.configuration.database.server.ServerConfigurationDataSource;
import org.idp.server.core.adapters.datasource.credential.database.VerifiableCredentialTransactionDataSource;
import org.idp.server.core.adapters.datasource.federation.FederatableIdProviderConfigurationDataSource;
import org.idp.server.core.adapters.datasource.federation.FederationSessionDataSource;
import org.idp.server.core.adapters.datasource.grantmanagment.AuthorizationGrantedMemoryDataSource;
import org.idp.server.core.adapters.datasource.oauth.database.code.AuthorizationCodeGrantDataSource;
import org.idp.server.core.adapters.datasource.oauth.database.request.AuthorizationRequestDataSource;
import org.idp.server.core.adapters.datasource.organization.OrganizationDataSource;
import org.idp.server.core.adapters.datasource.sharedsignal.EventDataSource;
import org.idp.server.core.adapters.datasource.sharedsignal.SharedSignalFrameworkConfigurationDataSource;
import org.idp.server.core.adapters.datasource.tenant.TenantDataSource;
import org.idp.server.core.adapters.datasource.token.database.OAuthTokenDataSource;
import org.idp.server.core.adapters.datasource.user.UserDataSource;
import org.idp.server.core.adapters.httpclient.ciba.NotificationClient;
import org.idp.server.core.adapters.httpclient.credential.VerifiableCredentialBlockCertClient;
import org.idp.server.core.adapters.httpclient.credential.VerifiableCredentialJwtClient;
import org.idp.server.core.adapters.httpclient.federation.FederationClient;
import org.idp.server.core.adapters.httpclient.oauth.RequestObjectHttpClient;
import org.idp.server.core.adapters.httpclient.sharedsignal.SharedSignalEventClient;
import org.idp.server.core.api.*;
import org.idp.server.core.basic.sql.TransactionInterceptor;
import org.idp.server.core.basic.sql.TransactionManager;
import org.idp.server.core.federation.FederationService;
import org.idp.server.core.function.*;
import org.idp.server.core.handler.ciba.CibaAuthorizeHandler;
import org.idp.server.core.handler.ciba.CibaDenyHandler;
import org.idp.server.core.handler.ciba.CibaRequestHandler;
import org.idp.server.core.handler.config.DatabaseConfig;
import org.idp.server.core.handler.configuration.ClientConfigurationHandler;
import org.idp.server.core.handler.configuration.ServerConfigurationHandler;
import org.idp.server.core.handler.credential.CredentialHandler;
import org.idp.server.core.handler.discovery.DiscoveryHandler;
import org.idp.server.core.handler.federation.FederationHandler;
import org.idp.server.core.handler.oauth.OAuthAuthorizeHandler;
import org.idp.server.core.handler.oauth.OAuthDenyHandler;
import org.idp.server.core.handler.oauth.OAuthHandler;
import org.idp.server.core.handler.oauth.OAuthRequestHandler;
import org.idp.server.core.handler.sharedsignal.EventHandler;
import org.idp.server.core.handler.token.TokenRequestHandler;
import org.idp.server.core.handler.tokenintrospection.TokenIntrospectionHandler;
import org.idp.server.core.handler.tokenrevocation.TokenRevocationHandler;
import org.idp.server.core.handler.userinfo.UserinfoHandler;
import org.idp.server.core.oauth.OAuthRequestDelegate;
import org.idp.server.core.oauth.interaction.OAuthUserInteractionType;
import org.idp.server.core.oauth.interaction.OAuthUserInteractor;
import org.idp.server.core.oauth.interaction.OAuthUserInteractors;
import org.idp.server.core.organization.OrganizationService;
import org.idp.server.core.protcol.*;
import org.idp.server.core.sharedsignal.EventPublisher;
import org.idp.server.core.tenant.TenantService;
import org.idp.server.core.type.verifiablecredential.Format;
import org.idp.server.core.user.*;
import org.idp.server.core.verifiablecredential.VerifiableCredentialCreator;
import org.idp.server.core.verifiablecredential.VerifiableCredentialCreators;

import java.util.HashMap;
import java.util.Map;

/** IdpServerApplication */
public class IdpServerApplication {

  OAuthFlowFunction oAuthFlowFunction;
  TokenFunction tokenFunction;
  OidcMetaDataFunction oidcMetaDataFunction;
  UserinfoFunction userinfoFunction;
  CibaFlowFunction cibaFlowFunction;
  EventFunction eventFunction;
  OnboardingFunction onboardingFunction;
  ServerManagementFunction serverManagementFunction;
  ClientManagementFunction clientManagementFunction;
  UserManagementFunction userManagementFunction;
  OperatorAuthenticationFunction operatorAuthenticationFunction;


  public IdpServerApplication(DatabaseConfig databaseConfig,
                              OAuthRequestDelegate oAuthRequestDelegate,
                              Map<OAuthUserInteractionType, OAuthUserInteractor> additionalUserInteractions,
                              PasswordEncodeDelegation passwordEncodeDelegation,
                              PasswordVerificationDelegation passwordVerificationDelegation,
                              EventPublisher eventPublisher) {
    TransactionManager.setConnectionConfig(
        databaseConfig.url(), databaseConfig.username(), databaseConfig.password());

    AuthorizationRequestDataSource authorizationRequestDataSource =
        new AuthorizationRequestDataSource();
    AuthorizationCodeGrantDataSource authorizationCodeGrantDataSource =
        new AuthorizationCodeGrantDataSource();
    AuthorizationGrantedMemoryDataSource authorizationGrantedDataSource =
        new AuthorizationGrantedMemoryDataSource();
    OAuthTokenDataSource oAuthTokenDataSource = new OAuthTokenDataSource();
    ServerConfigurationDataSource serverConfigurationMemoryDataSource =
        new ServerConfigurationDataSource();
    ClientConfigurationDataSource clientConfigurationMemoryDataSource =
        new ClientConfigurationDataSource();
    VerifiableCredentialTransactionDataSource verifiableCredentialTransactionDataSource =
        new VerifiableCredentialTransactionDataSource();
    EventDataSource eventDataSource = new EventDataSource();
    FederatableIdProviderConfigurationDataSource federatableIdProviderConfigurationDataSource =
        new FederatableIdProviderConfigurationDataSource();
    FederationSessionDataSource federationSessionDataSource = new FederationSessionDataSource();
    SharedSignalFrameworkConfigurationDataSource sharedSignalFrameworkConfigurationDataSource =
        new SharedSignalFrameworkConfigurationDataSource();
    UserDataSource userDataSource = new UserDataSource();
    OrganizationDataSource organizationDataSource = new OrganizationDataSource();
    TenantDataSource tenantDataSource = new TenantDataSource();

    OAuthRequestHandler oAuthRequestHandler =
        new OAuthRequestHandler(
            authorizationRequestDataSource,
            serverConfigurationMemoryDataSource,
            clientConfigurationMemoryDataSource,
            new RequestObjectHttpClient());
    OAuthAuthorizeHandler oAuthAuthorizeHandler =
        new OAuthAuthorizeHandler(
            authorizationRequestDataSource,
            authorizationCodeGrantDataSource,
            oAuthTokenDataSource,
            serverConfigurationMemoryDataSource,
            clientConfigurationMemoryDataSource);
    OAuthDenyHandler oAuthDenyHandler =
        new OAuthDenyHandler(
            authorizationRequestDataSource,
            serverConfigurationMemoryDataSource,
            clientConfigurationMemoryDataSource);
    OAuthHandler oAuthHandler =
        new OAuthHandler(
            authorizationRequestDataSource,
            serverConfigurationMemoryDataSource,
            clientConfigurationMemoryDataSource);

    OAuthApi oAuthApi =
            new OAuthApiImpl(
                    oAuthRequestHandler, oAuthAuthorizeHandler, oAuthDenyHandler, oAuthHandler);
    UserRegistrationService userRegistrationService = new UserRegistrationService(userDataSource, passwordEncodeDelegation);
    UserAuthenticationService userAuthenticationService = new UserAuthenticationService(passwordVerificationDelegation);
    FederationHandler federationHandler =
            new FederationHandler(
                    federatableIdProviderConfigurationDataSource,
                    federationSessionDataSource,
                    new FederationClient());
    FederationApi federationApi =
            TransactionInterceptor.createProxy(
                    new FederationApiImpl(federationHandler), FederationApi.class);

    OrganizationService organizationService = new OrganizationService(organizationDataSource);
    TenantService tenantService = new TenantService(tenantDataSource);
    UserService userService = new UserService(userDataSource);

    FederationService federationService = new FederationService(federationApi, userService);

    TokenIntrospectionHandler tokenIntrospectionHandler =
        new TokenIntrospectionHandler(oAuthTokenDataSource);
    TokenIntrospectionApi tokenIntrospectionApi =
        TransactionInterceptor.createProxy(
            new TokenIntrospectionApiImpl(tokenIntrospectionHandler), TokenIntrospectionApi.class);
    TokenRevocationHandler tokenRevocationHandler =
        new TokenRevocationHandler(
            oAuthTokenDataSource,
            serverConfigurationMemoryDataSource,
            clientConfigurationMemoryDataSource);
    TokenRevocationApi tokenRevocationApi =
        TransactionInterceptor.createProxy(
            new TokenRevocationApiImpl(tokenRevocationHandler), TokenRevocationApi.class);
    UserinfoHandler userinfoHandler =
        new UserinfoHandler(
            oAuthTokenDataSource,
            serverConfigurationMemoryDataSource,
            clientConfigurationMemoryDataSource);
    UserinfoApi userinfoApi =
        TransactionInterceptor.createProxy(new UserinfoApiImpl(userinfoHandler), UserinfoApi.class);
    DiscoveryHandler discoveryHandler = new DiscoveryHandler(serverConfigurationMemoryDataSource);
    DiscoveryApi discoveryApi =
        TransactionInterceptor.createProxy(
            new DiscoveryApiImpl(discoveryHandler), DiscoveryApi.class);
    JwksApi jwksApi =
        TransactionInterceptor.createProxy(new JwksApiImpl(discoveryHandler), JwksApi.class);
    BackchannelAuthenticationDataSource backchannelAuthenticationDataSource =
        new BackchannelAuthenticationDataSource();
    CibaGrantDataSource cibaGrantDataSource = new CibaGrantDataSource();
    NotificationClient notificationClient = new NotificationClient();
    CibaApi cibaApi =
        TransactionInterceptor.createProxy(
            new CibaApiImpl(
                new CibaRequestHandler(
                    backchannelAuthenticationDataSource,
                    cibaGrantDataSource,
                    serverConfigurationMemoryDataSource,
                    clientConfigurationMemoryDataSource),
                new CibaAuthorizeHandler(
                    backchannelAuthenticationDataSource,
                    cibaGrantDataSource,
                    authorizationGrantedDataSource,
                    oAuthTokenDataSource,
                    notificationClient,
                    serverConfigurationMemoryDataSource,
                    clientConfigurationMemoryDataSource),
                new CibaDenyHandler(
                    cibaGrantDataSource,
                    serverConfigurationMemoryDataSource,
                    clientConfigurationMemoryDataSource)),
            CibaApi.class);
    TokenRequestHandler tokenRequestHandler =
        new TokenRequestHandler(
            authorizationRequestDataSource,
            authorizationCodeGrantDataSource,
            authorizationGrantedDataSource,
            backchannelAuthenticationDataSource,
            cibaGrantDataSource,
            oAuthTokenDataSource,
            serverConfigurationMemoryDataSource,
            clientConfigurationMemoryDataSource);
    TokenApi tokenApi =
        TransactionInterceptor.createProxy(new TokenApiImpl(tokenRequestHandler), TokenApi.class);

    Map<Format, VerifiableCredentialCreator> vcCreators = new HashMap<>();
    vcCreators.put(Format.jwt_vc_json, new VerifiableCredentialJwtClient());
    vcCreators.put(Format.ldp_vc, new VerifiableCredentialBlockCertClient());

    VerifiableCredentialCreators creators = new VerifiableCredentialCreators(vcCreators);
    CredentialHandler credentialHandler =
            new CredentialHandler(
                    oAuthTokenDataSource,
                    verifiableCredentialTransactionDataSource,
                    serverConfigurationMemoryDataSource,
                    clientConfigurationMemoryDataSource,
                    creators);


    SharedSignalEventClient sharedSignalEventClient = new SharedSignalEventClient();
    EventHandler eventHandler =
        new EventHandler(
            eventDataSource, sharedSignalFrameworkConfigurationDataSource, sharedSignalEventClient);

    ServerConfigurationHandler serverConfigurationHandler = new ServerConfigurationHandler(serverConfigurationMemoryDataSource);

    //create instance
    HashMap<OAuthUserInteractionType, OAuthUserInteractor> interactors = new HashMap<>(additionalUserInteractions);
    interactors.put(OAuthUserInteractionType.SIGNUP_REQUEST, userRegistrationService);
    interactors.put(OAuthUserInteractionType.PASSWORD_AUTHENTICATION, userAuthenticationService);
    OAuthUserInteractors oAuthUserInteractors = new OAuthUserInteractors(interactors);

    this.oAuthFlowFunction =
            TransactionInterceptor.createProxy(
                    new OAuthFlowService(
                            oAuthApi, oAuthRequestDelegate, oAuthUserInteractors, userService, userRegistrationService, tenantService, federationService, eventPublisher
                    ), OAuthFlowFunction.class
            );

    this.tokenFunction = TransactionInterceptor.createProxy(
            new TokenService(
                    tokenApi, tokenIntrospectionApi, tokenRevocationApi, userService, tenantService
            ), TokenFunction.class
    );

    this.oidcMetaDataFunction = TransactionInterceptor.createProxy(
            new OidcMetaDataService(tenantService, discoveryApi, jwksApi), OidcMetaDataFunction.class
    );

    this.userinfoFunction = TransactionInterceptor.createProxy(
            new UserinfoService(userinfoApi, userService, tenantService), UserinfoFunction.class
    );

    this.cibaFlowFunction = TransactionInterceptor.createProxy(
            new CibaFlowService(cibaApi, userService, tenantService), CibaFlowFunction.class
    );

    this.eventFunction =
            TransactionInterceptor.createProxy(new EventService(eventHandler), EventFunction.class);

    this.onboardingFunction = TransactionInterceptor.createProxy(
            new OnboardingService(tenantService, organizationService, userService, serverConfigurationHandler),
            OnboardingFunction.class
    );

    this.serverManagementFunction =
            TransactionInterceptor.createProxy(
                    new ServerManagementService(
                            tenantService,serverConfigurationHandler
                            ),
                    ServerManagementFunction.class);

    this.clientManagementFunction =
            TransactionInterceptor.createProxy(
                    new ClientManagementService(
                            tenantService,
                            new ClientConfigurationHandler(clientConfigurationMemoryDataSource)),
                    ClientManagementFunction.class);

    this.userManagementFunction = TransactionInterceptor.createProxy(new UserManagementService(tenantService, userService), UserManagementFunction.class);

    this.operatorAuthenticationFunction = TransactionInterceptor.createProxy(
            new OperatorAuthenticationService(tokenIntrospectionApi, tenantService, userService), OperatorAuthenticationFunction.class
    );
  }

  public OAuthFlowFunction oAuthFlowFunction() {
    return oAuthFlowFunction;
  }

  public TokenFunction tokenFunction() {
    return tokenFunction;
  }

  public OidcMetaDataFunction oidcMetaDataFunction() {
    return oidcMetaDataFunction;
  }

  public UserinfoFunction userinfoFunction() {
    return userinfoFunction;
  }

  public CibaFlowFunction cibaFlowFunction() {
    return cibaFlowFunction;
  }

  public EventFunction eventFunction() {
    return eventFunction;
  }

  public OnboardingFunction onboardingFunction() {
    return onboardingFunction;
  }

  public ServerManagementFunction serverManagementFunction() {
    return serverManagementFunction;
  }

  public ClientManagementFunction clientManagementFunction() {
    return clientManagementFunction;
  }

  public UserManagementFunction userManagementFunction() {
    return userManagementFunction;
  }

  public OperatorAuthenticationFunction operatorAuthenticationFunction() {
    return operatorAuthenticationFunction;
  }
}
