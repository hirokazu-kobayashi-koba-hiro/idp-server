package org.idp.server.core.adapters.datasource.configuration.memory;

import java.io.IOException;
import java.util.*;
import org.idp.server.core.adapters.datasource.oauth.memory.IdpServerFailedInitializationException;
import org.idp.server.core.basic.json.JsonConverter;
import org.idp.server.core.basic.resource.ResourceReadable;
import org.idp.server.core.configuration.ClientConfiguration;
import org.idp.server.core.configuration.ClientConfigurationNotFoundException;
import org.idp.server.core.configuration.ClientConfigurationRepository;
import org.idp.server.core.tenant.Tenant;
import org.idp.server.core.tenant.TenantIdentifier;
import org.idp.server.core.type.oauth.ClientId;

/** ClientConfigurationMemoryDataSource */
public class ClientConfigurationMemoryDataSource
    implements ClientConfigurationRepository, ResourceReadable {

  Map<MultiClientIdentifier, ClientConfiguration> map = new HashMap<>();

  public ClientConfigurationMemoryDataSource(List<String> paths) {
    initialize(paths);
  }

  @Override
  public void register(ClientConfiguration clientConfiguration) {
    MultiClientIdentifier multiClientIdentifier =
        new MultiClientIdentifier(
            clientConfiguration.tenantIdentifier(), clientConfiguration.clientId());
    map.put(multiClientIdentifier, clientConfiguration);
  }

  @Override
  public ClientConfiguration get(Tenant tenant, ClientId clientId) {
    MultiClientIdentifier multiClientIdentifier =
        new MultiClientIdentifier(tenant.identifier(), clientId);
    ClientConfiguration clientConfiguration = map.get(multiClientIdentifier);
    if (Objects.isNull(clientConfiguration)) {
      throw new ClientConfigurationNotFoundException(
          String.format("unregistered client (%s)", clientId.value()));
    }
    return clientConfiguration;
  }

  @Override
  public List<ClientConfiguration> find(Tenant tenant, int limit, int offset) {
    ArrayList<ClientConfiguration> clientConfigurations = new ArrayList<>(map.values());
    return clientConfigurations.stream()
        .filter(
            clientConfiguration -> clientConfiguration.tenantId().equals(tenant.identifierValue()))
        .toList();
  }

  void initialize(List<String> paths) {
    try {
      JsonConverter jsonConverter = JsonConverter.createWithSnakeCaseStrategy();
      for (String path : paths) {
        String json = read(path);
        ClientConfiguration clientConfiguration =
            jsonConverter.read(json, ClientConfiguration.class);
        TenantIdentifier tenantIdentifier = clientConfiguration.tenantIdentifier();
        ClientId clientId = clientConfiguration.clientId();
        MultiClientIdentifier multiClientIdentifier =
            new MultiClientIdentifier(tenantIdentifier, clientId);
        map.put(multiClientIdentifier, clientConfiguration);
      }
    } catch (IOException e) {
      throw new IdpServerFailedInitializationException(e.getMessage(), e);
    }
  }
}
