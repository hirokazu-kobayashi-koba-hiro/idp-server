package org.idp.server.control_plane.management.identity.verification;

import java.util.Map;
import org.idp.server.control_plane.management.identity.verification.io.IdentityVerificationConfigManagementResponse;
import org.idp.server.control_plane.management.identity.verification.io.IdentityVerificationConfigManagementStatus;
import org.idp.server.core.identity.verification.IdentityVerificationType;
import org.idp.server.core.identity.verification.configuration.IdentityVerificationConfiguration;
import org.idp.server.core.multi_tenancy.tenant.Tenant;

public class IdentityVerificationConfigRegistrationContext {

  Tenant tenant;
  IdentityVerificationConfiguration identityVerificationConfiguration;
  boolean dryRun;

  public IdentityVerificationConfigRegistrationContext(
      Tenant tenant,
      IdentityVerificationConfiguration identityVerificationConfiguration,
      boolean dryRun) {
    this.tenant = tenant;
    this.identityVerificationConfiguration = identityVerificationConfiguration;
    this.dryRun = dryRun;
  }

  public Tenant tenant() {
    return null;
  }

  public IdentityVerificationConfiguration configuration() {
    return identityVerificationConfiguration;
  }

  public IdentityVerificationType type() {
    return identityVerificationConfiguration.type();
  }

  public boolean isDryRun() {
    return dryRun;
  }

  public IdentityVerificationConfigManagementResponse toResponse() {
    Map<String, Object> contents =
        Map.of("result", identityVerificationConfiguration.toMap(), "dry_run", dryRun);
    return new IdentityVerificationConfigManagementResponse(
        IdentityVerificationConfigManagementStatus.CREATED, contents);
  }
}
