package org.idp.server.control_plane.management.identity.verification;

import java.util.UUID;
import org.idp.server.basic.json.JsonConverter;
import org.idp.server.control_plane.management.identity.verification.io.IdentityVerificationConfigRegistrationRequest;
import org.idp.server.control_plane.management.identity.verification.io.IdentityVerificationConfigurationRequest;
import org.idp.server.core.identity.verification.configuration.IdentityVerificationConfiguration;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class IdentityVerificationConfigRegistrationContextCreator {

  Tenant tenant;
  IdentityVerificationConfigRegistrationRequest request;
  boolean dryRun;
  JsonConverter jsonConverter;

  public IdentityVerificationConfigRegistrationContextCreator(
      Tenant tenant, IdentityVerificationConfigRegistrationRequest request, boolean dryRun) {
    this.tenant = tenant;
    this.request = request;
    this.dryRun = dryRun;
    this.jsonConverter = JsonConverter.snakeCaseInstance();
  }

  public IdentityVerificationConfigRegistrationContext create() {
    IdentityVerificationConfigurationRequest configurationRequest =
        jsonConverter.read(request.toMap(), IdentityVerificationConfigurationRequest.class);
    String identifier =
        configurationRequest.hasId() ? configurationRequest.id() : UUID.randomUUID().toString();

    IdentityVerificationConfiguration configuration =
        configurationRequest.toConfiguration(identifier);

    return new IdentityVerificationConfigRegistrationContext(tenant, configuration, dryRun);
  }
}
