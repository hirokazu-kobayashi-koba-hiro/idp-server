package org.idp.server;

import java.util.List;
import org.idp.server.datasource.memory.AuthorizationCodeGrantMemoryDataSource;
import org.idp.server.datasource.memory.AuthorizationRequestMemoryDataSource;
import org.idp.server.datasource.memory.ClientConfigurationMemoryDataSource;
import org.idp.server.datasource.memory.ServerConfigurationMemoryDataSource;
import org.idp.server.handler.token.OAuthTokenRequestHandler;
import org.idp.server.io.config.MemoryDataSourceConfig;

/** IdpServerApplication */
public class IdpServerApplication {

  OAuthApi oAuthApi;
  TokenApi tokenApi;

  public IdpServerApplication(MemoryDataSourceConfig memoryDataSourceConfig) {
    List<String> serverConfigurations = memoryDataSourceConfig.serverConfigurations();
    List<String> clientConfigurations = memoryDataSourceConfig.clientConfigurations();
    AuthorizationRequestMemoryDataSource authorizationRequestMemoryDataSource =
        new AuthorizationRequestMemoryDataSource();
    AuthorizationCodeGrantMemoryDataSource authorizationCodeGrantMemoryDataSource =
        new AuthorizationCodeGrantMemoryDataSource();
    ServerConfigurationMemoryDataSource serverConfigurationMemoryDataSource =
        new ServerConfigurationMemoryDataSource(serverConfigurations);
    ClientConfigurationMemoryDataSource clientConfigurationMemoryDataSource =
        new ClientConfigurationMemoryDataSource(clientConfigurations);
    this.oAuthApi =
        new OAuthApi(
            authorizationRequestMemoryDataSource,
            authorizationCodeGrantMemoryDataSource,
            serverConfigurationMemoryDataSource,
            clientConfigurationMemoryDataSource);
    OAuthTokenRequestHandler tokenRequestHandler =
        new OAuthTokenRequestHandler(
            authorizationRequestMemoryDataSource, authorizationCodeGrantMemoryDataSource);
    this.tokenApi =
        new TokenApi(
            tokenRequestHandler,
            serverConfigurationMemoryDataSource,
            clientConfigurationMemoryDataSource);
  }

  public OAuthApi oAuthApi() {
    return oAuthApi;
  }

  public TokenApi tokenApi() {
    return tokenApi;
  }
}
