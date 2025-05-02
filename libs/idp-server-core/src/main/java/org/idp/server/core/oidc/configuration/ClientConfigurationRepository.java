package org.idp.server.core.oidc.configuration;

import java.util.List;
import org.idp.server.basic.type.oauth.RequestedClientId;
import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.core.oidc.client.ClientIdentifier;

public interface ClientConfigurationRepository {

  void register(Tenant tenant, ClientConfiguration clientConfiguration);

  ClientConfiguration get(Tenant tenant, RequestedClientId requestedClientId);

  ClientConfiguration get(Tenant tenant, ClientIdentifier clientIdentifier);

  List<ClientConfiguration> find(Tenant tenant, int limit, int offset);

  void update(Tenant tenant, ClientConfiguration clientConfiguration);

  void delete(Tenant tenant, RequestedClientId requestedClientId);
}
