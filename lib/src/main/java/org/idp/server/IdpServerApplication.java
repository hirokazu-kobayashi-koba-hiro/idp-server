package org.idp.server;

import java.util.List;
import org.idp.server.core.repository.ClientConfigurationRepository;
import org.idp.server.core.repository.AuthorizationRequestRepository;
import org.idp.server.core.repository.ServerConfigurationRepository;
import org.idp.server.datasource.memory.ClientConfigurationMemoryDataSource;
import org.idp.server.datasource.memory.AuthorizationRequestMemoryDataSource;
import org.idp.server.datasource.memory.ServerConfigurationMemoryDataSource;

/** IdpServerApplication */
public class IdpServerApplication {

  OAuthApi oAuthApi;

  IdpServerApplication(
      AuthorizationRequestRepository authorizationRequestRepository,
      ServerConfigurationRepository serverConfigurationRepository,
      ClientConfigurationRepository clientConfigurationRepository) {
    this.oAuthApi =
        new OAuthApi(
                authorizationRequestRepository, serverConfigurationRepository, clientConfigurationRepository);
  }

  public static IdpServerApplication initializeWithInMemory(
      List<String> serverConfigurations, List<String> clientConfigurations) {
    return new IdpServerApplication(
        new AuthorizationRequestMemoryDataSource(),
        new ServerConfigurationMemoryDataSource(serverConfigurations),
        new ClientConfigurationMemoryDataSource(clientConfigurations));
  }

  public OAuthApi oAuthApi() {
    return oAuthApi;
  }
}
