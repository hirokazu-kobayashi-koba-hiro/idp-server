package org.idp.server.core.adapters;

import org.idp.server.core.*;
import org.idp.server.core.basic.sql.TransactionInterceptor;
import org.idp.server.core.basic.sql.TransactionManager;
import org.idp.server.core.handler.ciba.CibaAuthorizeHandler;
import org.idp.server.core.handler.ciba.CibaDenyHandler;
import org.idp.server.core.handler.ciba.CibaRequestHandler;
import org.idp.server.core.handler.ciba.datasource.database.grant.CibaGrantDataSource;
import org.idp.server.core.handler.ciba.datasource.database.request.BackchannelAuthenticationDataSource;
import org.idp.server.core.handler.ciba.httpclient.NotificationClient;
import org.idp.server.core.handler.config.DatabaseConfig;
import org.idp.server.core.handler.configuration.ClientConfigurationHandler;
import org.idp.server.core.handler.configuration.ServerConfigurationHandler;
import org.idp.server.core.handler.configuration.datasource.database.client.ClientConfigurationDataSource;
import org.idp.server.core.handler.configuration.datasource.database.server.ServerConfigurationDataSource;
import org.idp.server.core.handler.credential.CredentialHandler;
import org.idp.server.core.handler.credential.datasource.database.VerifiableCredentialTransactionDataSource;
import org.idp.server.core.handler.discovery.DiscoveryHandler;
import org.idp.server.core.handler.federation.FederationHandler;
import org.idp.server.core.handler.federation.datasource.FederatableIdProviderConfigurationDataSource;
import org.idp.server.core.handler.federation.datasource.FederationSessionDataSource;
import org.idp.server.core.handler.federation.httpclient.FederationClient;
import org.idp.server.core.handler.grantmanagment.datasource.AuthorizationGrantedMemoryDataSource;
import org.idp.server.core.handler.oauth.OAuthAuthorizeHandler;
import org.idp.server.core.handler.oauth.OAuthDenyHandler;
import org.idp.server.core.handler.oauth.OAuthHandler;
import org.idp.server.core.handler.oauth.OAuthRequestHandler;
import org.idp.server.core.handler.oauth.datasource.database.code.AuthorizationCodeGrantDataSource;
import org.idp.server.core.handler.oauth.datasource.database.request.AuthorizationRequestDataSource;
import org.idp.server.core.handler.oauth.httpclient.RequestObjectHttpClient;
import org.idp.server.core.handler.sharedsignal.EventHandler;
import org.idp.server.core.handler.sharedsignal.datasource.EventDataSource;
import org.idp.server.core.handler.sharedsignal.datasource.SharedSignalFrameworkConfigurationDataSource;
import org.idp.server.core.handler.sharedsignal.httpClient.SharedSignalEventClient;
import org.idp.server.core.handler.token.TokenRequestHandler;
import org.idp.server.core.handler.token.datasource.database.OAuthTokenDataSource;
import org.idp.server.core.handler.tokenintrospection.TokenIntrospectionHandler;
import org.idp.server.core.handler.tokenrevocation.TokenRevocationHandler;
import org.idp.server.core.handler.user.datasource.UserDataSource;
import org.idp.server.core.handler.userinfo.UserinfoHandler;
import org.idp.server.core.user.UserService;

/** IdpServerApplication */
public class IdpServerApplication {

  OAuthApi oAuthApi;
  TokenApi tokenApi;
  TokenIntrospectionApi tokenIntrospectionApi;
  TokenRevocationApi tokenRevocationApi;
  UserinfoApi userinfoApi;
  DiscoveryApi discoveryApi;
  JwksApi jwksApi;
  CibaApi cibaApi;
  CredentialApi credentialApi;
  ServerManagementApi serverManagementApi;
  ClientManagementApi clientManagementApi;
  EventApi eventApi;
  FederationApi federationApi;
  UserManagementApi userManagementApi;



  public IdpServerApplication(DatabaseConfig databaseConfig) {
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

    this.oAuthApi =
        TransactionInterceptor.createProxy(
            new OAuthApiImpl(
                oAuthRequestHandler, oAuthAuthorizeHandler, oAuthDenyHandler, oAuthHandler),
            OAuthApi.class);

    TokenIntrospectionHandler tokenIntrospectionHandler =
        new TokenIntrospectionHandler(oAuthTokenDataSource);
    this.tokenIntrospectionApi =
        TransactionInterceptor.createProxy(
            new TokenIntrospectionApiImpl(tokenIntrospectionHandler), TokenIntrospectionApi.class);
    TokenRevocationHandler tokenRevocationHandler =
        new TokenRevocationHandler(
            oAuthTokenDataSource,
            serverConfigurationMemoryDataSource,
            clientConfigurationMemoryDataSource);
    this.tokenRevocationApi =
        TransactionInterceptor.createProxy(
            new TokenRevocationApiImpl(tokenRevocationHandler), TokenRevocationApi.class);
    UserinfoHandler userinfoHandler =
        new UserinfoHandler(
            oAuthTokenDataSource,
            serverConfigurationMemoryDataSource,
            clientConfigurationMemoryDataSource);
    this.userinfoApi =
        TransactionInterceptor.createProxy(new UserinfoApiImpl(userinfoHandler), UserinfoApi.class);
    DiscoveryHandler discoveryHandler = new DiscoveryHandler(serverConfigurationMemoryDataSource);
    this.discoveryApi =
        TransactionInterceptor.createProxy(
            new DiscoveryApiImpl(discoveryHandler), DiscoveryApi.class);
    this.jwksApi =
        TransactionInterceptor.createProxy(new JwksApiImpl(discoveryHandler), JwksApi.class);
    BackchannelAuthenticationDataSource backchannelAuthenticationDataSource =
        new BackchannelAuthenticationDataSource();
    CibaGrantDataSource cibaGrantDataSource = new CibaGrantDataSource();
    NotificationClient notificationClient = new NotificationClient();
    this.cibaApi =
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
    this.tokenApi =
        TransactionInterceptor.createProxy(new TokenApiImpl(tokenRequestHandler), TokenApi.class);
    CredentialHandler credentialHandler =
        new CredentialHandler(
            oAuthTokenDataSource,
            verifiableCredentialTransactionDataSource,
            serverConfigurationMemoryDataSource,
            clientConfigurationMemoryDataSource);
    this.credentialApi =
        TransactionInterceptor.createProxy(
            new CredentialApiImpl(credentialHandler), CredentialApi.class);
    this.serverManagementApi =
        TransactionInterceptor.createProxy(
            new ServerManagementApiImpl(
                new ServerConfigurationHandler(serverConfigurationMemoryDataSource)),
            ServerManagementApi.class);
    this.clientManagementApi =
        TransactionInterceptor.createProxy(
            new ClientManagementApiImpl(
                new ClientConfigurationHandler(clientConfigurationMemoryDataSource)),
            ClientManagementApi.class);

    SharedSignalEventClient sharedSignalEventClient = new SharedSignalEventClient();
    EventHandler eventHandler =
        new EventHandler(
            eventDataSource, sharedSignalFrameworkConfigurationDataSource, sharedSignalEventClient);
    this.eventApi =
        TransactionInterceptor.createProxy(new EventApiImpl(eventHandler), EventApi.class);

    FederationHandler federationHandler =
        new FederationHandler(
            federatableIdProviderConfigurationDataSource,
            federationSessionDataSource,
            new FederationClient());
    this.federationApi =
        TransactionInterceptor.createProxy(
            new FederationApiImpl(federationHandler), FederationApi.class);

    UserService userService = new UserService(userDataSource);
    this.userManagementApi = TransactionInterceptor.createProxy(new UserManagementApiImpl(userService), UserManagementApi.class);
  }

  public OAuthApi oAuthApi() {
    return oAuthApi;
  }

  public TokenApi tokenApi() {
    return tokenApi;
  }

  public TokenIntrospectionApi tokenIntrospectionApi() {
    return tokenIntrospectionApi;
  }

  public TokenRevocationApi tokenRevocationApi() {
    return tokenRevocationApi;
  }

  public UserinfoApi userinfoApi() {
    return userinfoApi;
  }

  public CredentialApi credentialApi() {
    return credentialApi;
  }

  public DiscoveryApi discoveryApi() {
    return discoveryApi;
  }

  public JwksApi jwksApi() {
    return jwksApi;
  }

  public CibaApi cibaApi() {
    return cibaApi;
  }

  public ServerManagementApi serverManagementApi() {
    return serverManagementApi;
  }

  public ClientManagementApi clientManagementApi() {
    return clientManagementApi;
  }

  public EventApi eventApi() {
    return eventApi;
  }

  public FederationApi federationApi() {
    return federationApi;
  }

  public UserManagementApi userManagementApi() {
    return userManagementApi;
  }
}
