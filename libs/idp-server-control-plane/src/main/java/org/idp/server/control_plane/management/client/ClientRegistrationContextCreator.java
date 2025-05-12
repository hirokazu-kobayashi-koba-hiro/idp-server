package org.idp.server.control_plane.management.client;

import org.idp.server.basic.json.JsonConverter;
import org.idp.server.control_plane.management.client.io.ClientRegistrationRequest;
import org.idp.server.core.multi_tenancy.tenant.Tenant;
import org.idp.server.core.oidc.configuration.client.ClientConfiguration;

public class ClientRegistrationContextCreator {

  Tenant tenant;
  ClientRegistrationRequest request;
  JsonConverter jsonConverter;

  public ClientRegistrationContextCreator(Tenant tenant, ClientRegistrationRequest request) {
    this.tenant = tenant;
    this.request = request;
    this.jsonConverter = JsonConverter.snakeCaseInstance();
  }

  public ClientRegistrationContext create() {
    ClientConfiguration clientConfiguration =
        jsonConverter.read(request.get("client"), ClientConfiguration.class);
    boolean dryRun = request.isDryRun();

    return new ClientRegistrationContext(tenant, clientConfiguration, dryRun);
  }
}
