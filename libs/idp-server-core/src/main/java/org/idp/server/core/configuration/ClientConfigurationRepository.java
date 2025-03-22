package org.idp.server.core.configuration;

import java.util.List;
import org.idp.server.core.tenant.Tenant;
import org.idp.server.core.type.oauth.ClientId;

public interface ClientConfigurationRepository {

  void register(Tenant tenant, ClientConfiguration clientConfiguration);

  ClientConfiguration get(Tenant tenant, ClientId clientId);

  List<ClientConfiguration> find(Tenant tenant, int limit, int offset);
}
