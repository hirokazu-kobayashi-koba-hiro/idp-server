package org.idp.server.control_plane.management.oidc.client;

import org.idp.server.basic.json.JsonConverter;
import org.idp.server.control_plane.management.oidc.client.io.ClientRegistrationRequest;
import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.core.oidc.configuration.client.ClientConfiguration;

public class ClientUpdateContextCreator {

  Tenant tenant;
  ClientConfiguration before;
  ClientRegistrationRequest request;
  JsonConverter jsonConverter;

  public ClientUpdateContextCreator(
      Tenant tenant, ClientConfiguration before, ClientRegistrationRequest request) {
    this.tenant = tenant;
    this.before = before;
    this.request = request;
    this.jsonConverter = JsonConverter.snakeCaseInstance();
  }

  public ClientUpdateContext create() {
    ClientConfiguration clientConfiguration =
        jsonConverter.read(request.get("client"), ClientConfiguration.class);
    boolean dryRun = request.isDryRun();

    return new ClientUpdateContext(tenant, before, clientConfiguration, dryRun);
  }
}
