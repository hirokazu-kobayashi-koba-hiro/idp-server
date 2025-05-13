package org.idp.server.control_plane.management.oidc.client;

import org.idp.server.basic.json.JsonConverter;
import org.idp.server.control_plane.management.oidc.client.io.ClientRegistrationRequest;
import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.core.oidc.configuration.client.ClientConfiguration;

public class ClientUpdateContextCreator {

  Tenant tenant;
  ClientConfiguration before;
  ClientRegistrationRequest request;
  boolean dryRun;
  JsonConverter jsonConverter;

  public ClientUpdateContextCreator(
      Tenant tenant, ClientConfiguration before, ClientRegistrationRequest request, boolean dryRun) {
    this.tenant = tenant;
    this.before = before;
    this.request = request;
    this.dryRun = dryRun;
    this.jsonConverter = JsonConverter.snakeCaseInstance();
  }

  public ClientUpdateContext create() {
    ClientConfiguration clientConfiguration =
        jsonConverter.read(request.toMap(), ClientConfiguration.class);

    return new ClientUpdateContext(tenant, before, clientConfiguration, dryRun);
  }
}
