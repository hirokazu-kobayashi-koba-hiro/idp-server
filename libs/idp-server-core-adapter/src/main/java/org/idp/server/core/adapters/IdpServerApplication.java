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
import org.idp.server.core.adapters.datasource.sharedsignal.EventDataSource;
import org.idp.server.core.adapters.datasource.sharedsignal.SharedSignalFrameworkConfigurationDataSource;
import org.idp.server.core.adapters.datasource.token.database.OAuthTokenDataSource;
import org.idp.server.core.adapters.datasource.user.UserDataSource;
import org.idp.server.core.adapters.httpclient.ciba.NotificationClient;
import org.idp.server.core.adapters.httpclient.credential.VerifiableCredentialBlockCertClient;
import org.idp.server.core.adapters.httpclient.credential.VerifiableCredentialJwtClient;
import org.idp.server.core.adapters.httpclient.federation.FederationClient;
import org.idp.server.core.adapters.httpclient.oauth.RequestObjectHttpClient;
import org.idp.server.core.adapters.httpclient.sharedsignal.SharedSignalEventClient;
import org.idp.server.core.basic.sql.TransactionInterceptor;
import org.idp.server.core.basic.sql.TransactionManager;
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
import org.idp.server.core.type.verifiablecredential.Format;
import org.idp.server.core.user.UserService;
import org.idp.server.core.verifiablecredential.VerifiableCredentialCreator;
import org.idp.server.core.verifiablecredential.VerifiableCredentialCreators;

import java.util.HashMap;
import java.util.Map;

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
