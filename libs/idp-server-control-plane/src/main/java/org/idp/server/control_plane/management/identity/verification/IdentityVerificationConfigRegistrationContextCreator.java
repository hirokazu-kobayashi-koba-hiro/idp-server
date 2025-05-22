/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.control_plane.management.identity.verification;

import java.util.UUID;
import org.idp.server.basic.json.JsonConverter;
import org.idp.server.control_plane.management.identity.verification.io.IdentityVerificationConfigRegistrationRequest;
import org.idp.server.control_plane.management.identity.verification.io.IdentityVerificationConfigurationRequest;
import org.idp.server.core.extension.identity.verification.configuration.IdentityVerificationConfiguration;
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
