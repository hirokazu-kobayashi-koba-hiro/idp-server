package org.idp.server.core.oidc.configuration.client;

import java.util.List;
import org.idp.server.basic.type.oauth.RequestedClientId;
import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.core.oidc.client.ClientIdentifier;

public interface ClientConfigurationQueryRepository {

  ClientConfiguration get(Tenant tenant, RequestedClientId requestedClientId);

  ClientConfiguration get(Tenant tenant, ClientIdentifier clientIdentifier);

  List<ClientConfiguration> findList(Tenant tenant, int limit, int offset);

  ClientConfiguration find(Tenant tenant, ClientIdentifier clientIdentifier);
}
