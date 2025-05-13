package org.idp.server.control_plane.management.oidc.client;

import org.idp.server.basic.json.JsonConverter;
import org.idp.server.control_plane.management.oidc.client.io.ClientRegistrationRequest;
import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.core.oidc.configuration.client.ClientConfiguration;

public class ClientRegistrationContextCreator {

  Tenant tenant;
  ClientRegistrationRequest request;
  boolean dryRun;
  JsonConverter jsonConverter;

  public ClientRegistrationContextCreator(Tenant tenant, ClientRegistrationRequest request, boolean dryRun) {
    this.tenant = tenant;
    this.request = request;
    this.dryRun = dryRun;
    this.jsonConverter = JsonConverter.snakeCaseInstance();
  }

  public ClientRegistrationContext create() {
    ClientConfiguration clientConfiguration =
        jsonConverter.read(request.toMap(), ClientConfiguration.class);

    return new ClientRegistrationContext(tenant, clientConfiguration, dryRun);
  }
}
