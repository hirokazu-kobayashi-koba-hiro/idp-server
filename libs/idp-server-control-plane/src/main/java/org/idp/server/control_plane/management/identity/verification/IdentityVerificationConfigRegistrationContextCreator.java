package org.idp.server.control_plane.management.identity.verification;

import java.util.UUID;
import org.idp.server.basic.json.JsonConverter;
import org.idp.server.control_plane.management.identity.verification.io.IdentityVerificationConfigRegistrationRequest;
import org.idp.server.control_plane.management.identity.verification.io.IdentityVerificationConfigurationRequest;
import org.idp.server.core.identity.authentication.PasswordEncodeDelegation;
import org.idp.server.core.identity.verification.configuration.IdentityVerificationConfiguration;
import org.idp.server.core.multi_tenancy.tenant.Tenant;

public class IdentityVerificationConfigRegistrationContextCreator {

  Tenant tenant;
  IdentityVerificationConfigRegistrationRequest request;
  PasswordEncodeDelegation passwordEncodeDelegation;
  JsonConverter jsonConverter;

  public IdentityVerificationConfigRegistrationContextCreator(
      Tenant tenant, IdentityVerificationConfigRegistrationRequest request) {
    this.tenant = tenant;
    this.request = request;
    this.jsonConverter = JsonConverter.snakeCaseInstance();
  }

  public IdentityVerificationConfigRegistrationContext create() {
    IdentityVerificationConfigurationRequest configurationRequest =
        jsonConverter.read(request.get("config"), IdentityVerificationConfigurationRequest.class);
    String identifier =
        configurationRequest.hasId() ? configurationRequest.id() : UUID.randomUUID().toString();

    IdentityVerificationConfiguration configuration =
        configurationRequest.toConfiguration(identifier);
    boolean dryRun = request.isDryRun();

    return new IdentityVerificationConfigRegistrationContext(tenant, configuration, dryRun);
  }
}
