package org.idp.server.core.adapters.datasource.configuration.client;

import java.util.List;
import java.util.Map;
import org.idp.server.core.oidc.configuration.ClientConfiguration;
import org.idp.server.core.oidc.client.ClientIdentifier;
import org.idp.server.core.tenant.Tenant;
import org.idp.server.basic.type.oauth.RequestedClientId;

public interface ClientConfigSqlExecutor {

  void insert(Tenant tenant, ClientConfiguration clientConfiguration);

  Map<String, String> selectByAlias(Tenant tenant, RequestedClientId requestedClientId);

  Map<String, String> selectById(Tenant tenant, ClientIdentifier clientIdentifier);

  List<Map<String, String>> selectList(Tenant tenant, int limit, int offset);

  void update(Tenant tenant, ClientConfiguration clientConfiguration);

  void delete(Tenant tenant, RequestedClientId requestedClientId);
}
