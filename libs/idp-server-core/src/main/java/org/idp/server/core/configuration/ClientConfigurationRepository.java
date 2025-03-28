package org.idp.server.core.configuration;

import java.util.List;

import org.idp.server.core.oauth.client.ClientIdentifier;
import org.idp.server.core.tenant.Tenant;
import org.idp.server.core.type.oauth.ClientId;

public interface ClientConfigurationRepository {

  void register(Tenant tenant, ClientConfiguration clientConfiguration);

  ClientConfiguration get(Tenant tenant, ClientId clientId);

  ClientConfiguration get(Tenant tenant, ClientIdentifier clientIdentifier);

  List<ClientConfiguration> find(Tenant tenant, int limit, int offset);

  void update(Tenant tenant, ClientConfiguration clientConfiguration);

  void delete(Tenant tenant, ClientId clientId);
}
