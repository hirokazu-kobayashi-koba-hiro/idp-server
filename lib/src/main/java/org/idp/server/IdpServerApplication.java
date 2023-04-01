package org.idp.server;

import java.util.List;
import org.idp.server.core.repository.ClientConfigurationRepository;
import org.idp.server.core.repository.AuthorizationRequestRepository;
import org.idp.server.core.repository.ServerConfigurationRepository;
import org.idp.server.datasource.memory.AuthorizationCodeGrantMemoryDataSource;
import org.idp.server.datasource.memory.ClientConfigurationMemoryDataSource;
import org.idp.server.datasource.memory.AuthorizationRequestMemoryDataSource;
import org.idp.server.datasource.memory.ServerConfigurationMemoryDataSource;
import org.idp.server.io.config.MemoryDataSourceConfig;

/** IdpServerApplication */
public class IdpServerApplication {

  OAuthApi oAuthApi;

  public IdpServerApplication(MemoryDataSourceConfig memoryDataSourceConfig) {
    List<String> serverConfigurations = memoryDataSourceConfig.serverConfigurations();
    List<String> clientConfigurations = memoryDataSourceConfig.clientConfigurations();
     this.oAuthApi = new OAuthApi(
        new AuthorizationRequestMemoryDataSource(),
        new AuthorizationCodeGrantMemoryDataSource(),
        new ServerConfigurationMemoryDataSource(serverConfigurations),
        new ClientConfigurationMemoryDataSource(clientConfigurations));
  }

  public OAuthApi oAuthApi() {
    return oAuthApi;
  }
}
