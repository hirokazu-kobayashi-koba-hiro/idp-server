package org.idp.server;

import java.util.List;
import org.idp.server.core.repository.ClientConfigurationRepository;
import org.idp.server.core.repository.OAuthRequestRepository;
import org.idp.server.core.repository.ServerConfigurationRepository;
import org.idp.server.datasource.memory.ClientConfigurationMemoryDataSource;
import org.idp.server.datasource.memory.OAuthRequestMemoryDataSource;
import org.idp.server.datasource.memory.ServerConfigurationMemoryDataSource;

/** IdpServerApplication */
public class IdpServerApplication {

  OAuthApi oAuthApi;

  IdpServerApplication(
      OAuthRequestRepository oAuthRequestRepository,
      ServerConfigurationRepository serverConfigurationRepository,
      ClientConfigurationRepository clientConfigurationRepository) {
    this.oAuthApi =
        new OAuthApi(
            oAuthRequestRepository, serverConfigurationRepository, clientConfigurationRepository);
  }

  public static IdpServerApplication initializeWithInMemory(
      List<String> serverConfigurations, List<String> clientConfigurations) {
    return new IdpServerApplication(
        new OAuthRequestMemoryDataSource(),
        new ServerConfigurationMemoryDataSource(serverConfigurations),
        new ClientConfigurationMemoryDataSource(clientConfigurations));
  }

  public OAuthApi oAuthApi() {
    return oAuthApi;
  }
}
