package org.idp.server;

import java.util.List;
import org.idp.server.handler.ciba.CibaAuthorizeHandler;
import org.idp.server.handler.ciba.CibaDenyHandler;
import org.idp.server.handler.ciba.CibaRequestHandler;
import org.idp.server.handler.ciba.datasource.memory.BackchannelAuthenticationMemoryDataSource;
import org.idp.server.handler.ciba.datasource.memory.CibaGrantMemoryDataSource;
import org.idp.server.handler.ciba.httpclient.NotificationClient;
import org.idp.server.handler.config.MemoryDataSourceConfig;
import org.idp.server.handler.discovery.DiscoveryHandler;
import org.idp.server.handler.grantmanagment.datasource.AuthorizationGrantedMemoryDataSource;
import org.idp.server.handler.oauth.OAuthAuthorizeHandler;
import org.idp.server.handler.oauth.OAuthDenyHandler;
import org.idp.server.handler.oauth.OAuthRequestHandler;
import org.idp.server.handler.oauth.datasource.memory.AuthorizationCodeGrantMemoryDataSource;
import org.idp.server.handler.oauth.datasource.memory.AuthorizationRequestMemoryDataSource;
import org.idp.server.handler.oauth.datasource.memory.ClientConfigurationMemoryDataSource;
import org.idp.server.handler.oauth.datasource.memory.ServerConfigurationMemoryDataSource;
import org.idp.server.handler.oauth.httpclient.RequestObjectHttpClient;
import org.idp.server.handler.token.TokenRequestHandler;
import org.idp.server.handler.token.datasource.OAuthTokenMemoryDataSource;
import org.idp.server.handler.tokenintrospection.TokenIntrospectionHandler;
import org.idp.server.handler.tokenrevocation.TokenRevocationHandler;
import org.idp.server.handler.userinfo.UserinfoHandler;

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

  public IdpServerApplication(MemoryDataSourceConfig memoryDataSourceConfig) {
    List<String> serverConfigurations = memoryDataSourceConfig.serverConfigurations();
    List<String> clientConfigurations = memoryDataSourceConfig.clientConfigurations();
    AuthorizationRequestMemoryDataSource authorizationRequestMemoryDataSource =
        new AuthorizationRequestMemoryDataSource();
    AuthorizationCodeGrantMemoryDataSource authorizationCodeGrantMemoryDataSource =
        new AuthorizationCodeGrantMemoryDataSource();
    AuthorizationGrantedMemoryDataSource authorizationGrantedMemoryDataSource =
        new AuthorizationGrantedMemoryDataSource();
    OAuthTokenMemoryDataSource oAuthTokenMemoryDataSource = new OAuthTokenMemoryDataSource();
    ServerConfigurationMemoryDataSource serverConfigurationMemoryDataSource =
        new ServerConfigurationMemoryDataSource(serverConfigurations);
    ClientConfigurationMemoryDataSource clientConfigurationMemoryDataSource =
        new ClientConfigurationMemoryDataSource(clientConfigurations);
    OAuthRequestHandler oAuthRequestHandler =
        new OAuthRequestHandler(
            authorizationRequestMemoryDataSource,
            serverConfigurationMemoryDataSource,
            clientConfigurationMemoryDataSource,
            new RequestObjectHttpClient());
    OAuthAuthorizeHandler oAuthAuthorizeHandler =
        new OAuthAuthorizeHandler(
            authorizationRequestMemoryDataSource,
            authorizationCodeGrantMemoryDataSource,
            oAuthTokenMemoryDataSource,
            serverConfigurationMemoryDataSource,
            clientConfigurationMemoryDataSource);
    OAuthDenyHandler oAuthDenyHandler =
        new OAuthDenyHandler(
            authorizationRequestMemoryDataSource,
            serverConfigurationMemoryDataSource,
            clientConfigurationMemoryDataSource);
    this.oAuthApi = new OAuthApi(oAuthRequestHandler, oAuthAuthorizeHandler, oAuthDenyHandler);

    TokenIntrospectionHandler tokenIntrospectionHandler =
        new TokenIntrospectionHandler(oAuthTokenMemoryDataSource);
    this.tokenIntrospectionApi = new TokenIntrospectionApi(tokenIntrospectionHandler);
    TokenRevocationHandler tokenRevocationHandler =
        new TokenRevocationHandler(
            oAuthTokenMemoryDataSource,
            serverConfigurationMemoryDataSource,
            clientConfigurationMemoryDataSource);
    this.tokenRevocationApi = new TokenRevocationApi(tokenRevocationHandler);
    UserinfoHandler userinfoHandler =
        new UserinfoHandler(
            oAuthTokenMemoryDataSource,
            serverConfigurationMemoryDataSource,
            clientConfigurationMemoryDataSource);
    this.userinfoApi = new UserinfoApi(userinfoHandler);
    DiscoveryHandler discoveryHandler = new DiscoveryHandler(serverConfigurationMemoryDataSource);
    this.discoveryApi = new DiscoveryApi(discoveryHandler);
    this.jwksApi = new JwksApi(discoveryHandler);
    BackchannelAuthenticationMemoryDataSource backchannelAuthenticationMemoryDataSource =
        new BackchannelAuthenticationMemoryDataSource();
    CibaGrantMemoryDataSource cibaGrantMemoryDataSource = new CibaGrantMemoryDataSource();
    NotificationClient notificationClient = new NotificationClient();
    this.cibaApi =
        new CibaApi(
            new CibaRequestHandler(
                backchannelAuthenticationMemoryDataSource,
                cibaGrantMemoryDataSource,
                serverConfigurationMemoryDataSource,
                clientConfigurationMemoryDataSource),
            new CibaAuthorizeHandler(
                backchannelAuthenticationMemoryDataSource,
                cibaGrantMemoryDataSource,
                authorizationGrantedMemoryDataSource,
                oAuthTokenMemoryDataSource,
                notificationClient,
                serverConfigurationMemoryDataSource,
                clientConfigurationMemoryDataSource),
            new CibaDenyHandler(
                cibaGrantMemoryDataSource,
                serverConfigurationMemoryDataSource,
                clientConfigurationMemoryDataSource));
    TokenRequestHandler tokenRequestHandler =
        new TokenRequestHandler(
            authorizationRequestMemoryDataSource,
            authorizationCodeGrantMemoryDataSource,
            authorizationGrantedMemoryDataSource,
            backchannelAuthenticationMemoryDataSource,
            cibaGrantMemoryDataSource,
            oAuthTokenMemoryDataSource,
            serverConfigurationMemoryDataSource,
            clientConfigurationMemoryDataSource);
    this.tokenApi = new TokenApi(tokenRequestHandler);
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

  public DiscoveryApi discoveryApi() {
    return discoveryApi;
  }

  public JwksApi jwksApi() {
    return jwksApi;
  }

  public CibaApi cibaApi() {
    return cibaApi;
  }
}
