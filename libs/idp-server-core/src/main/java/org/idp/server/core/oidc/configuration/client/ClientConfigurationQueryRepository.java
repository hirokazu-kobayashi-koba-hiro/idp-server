/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.oidc.configuration.client;

import java.util.List;
import org.idp.server.basic.type.oauth.RequestedClientId;
import org.idp.server.core.oidc.client.ClientIdentifier;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public interface ClientConfigurationQueryRepository {

  ClientConfiguration get(Tenant tenant, RequestedClientId requestedClientId);

  ClientConfiguration get(Tenant tenant, ClientIdentifier clientIdentifier);

  List<ClientConfiguration> findList(Tenant tenant, int limit, int offset);

  ClientConfiguration find(Tenant tenant, ClientIdentifier clientIdentifier);
}
