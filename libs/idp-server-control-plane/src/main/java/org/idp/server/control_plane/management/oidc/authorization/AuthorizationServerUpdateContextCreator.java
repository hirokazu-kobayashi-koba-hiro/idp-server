package org.idp.server.control_plane.management.oidc.authorization;

import org.idp.server.basic.json.JsonConverter;
import org.idp.server.control_plane.management.oidc.authorization.io.AuthorizationServerUpdateRequest;
import org.idp.server.core.oidc.configuration.AuthorizationServerConfiguration;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class AuthorizationServerUpdateContextCreator {

  Tenant tenant;
  AuthorizationServerConfiguration before;
  AuthorizationServerUpdateRequest request;
  boolean dryRun;
  JsonConverter jsonConverter;

  public AuthorizationServerUpdateContextCreator(
      Tenant tenant,
      AuthorizationServerConfiguration before,
      AuthorizationServerUpdateRequest request,
      boolean dryRun) {
    this.tenant = tenant;
    this.before = before;
    this.request = request;
    this.dryRun = dryRun;
    this.jsonConverter = JsonConverter.snakeCaseInstance();
  }

  public AuthorizationServerUpdateContext create() {
    AuthorizationServerConfiguration configuration =
        jsonConverter.read(request.toMap(), AuthorizationServerConfiguration.class);

    return new AuthorizationServerUpdateContext(tenant, before, configuration, dryRun);
  }
}
