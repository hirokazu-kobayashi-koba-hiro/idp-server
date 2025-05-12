package org.idp.server.control_plane.management.identity.verification;

import java.util.UUID;
import org.idp.server.basic.json.JsonConverter;
import org.idp.server.control_plane.management.identity.verification.io.IdentityVerificationConfigUpdateRequest;
import org.idp.server.control_plane.management.identity.verification.io.IdentityVerificationConfigurationRequest;
import org.idp.server.core.identity.verification.configuration.IdentityVerificationConfiguration;
import org.idp.server.core.multi_tenancy.tenant.Tenant;

public class IdentityVerificationConfigUpdateContextCreator {

  Tenant tenant;
  IdentityVerificationConfigUpdateRequest request;
  IdentityVerificationConfiguration configuration;
  JsonConverter jsonConverter;

  public IdentityVerificationConfigUpdateContextCreator(
      Tenant tenant,
      IdentityVerificationConfigUpdateRequest request,
      IdentityVerificationConfiguration configuration) {
    this.tenant = tenant;
    this.request = request;
    this.configuration = configuration;
    this.jsonConverter = JsonConverter.snakeCaseInstance();
  }

  public IdentityVerificationConfigUpdateContext create() {
    IdentityVerificationConfigurationRequest configurationRequest =
        jsonConverter.read(request.get("config"), IdentityVerificationConfigurationRequest.class);
    String identifier =
        configurationRequest.hasId() ? configurationRequest.id() : UUID.randomUUID().toString();

    IdentityVerificationConfiguration configuration =
        configurationRequest.toConfiguration(identifier);
    boolean dryRun = request.isDryRun();

    return new IdentityVerificationConfigUpdateContext(
        tenant, this.configuration, configuration, dryRun);
  }
}
