/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.control_plane.management.tenant.verifier;

import org.idp.server.control_plane.base.verifier.TenantVerifier;
import org.idp.server.control_plane.base.verifier.VerificationResult;
import org.idp.server.control_plane.management.tenant.TenantManagementRegistrationContext;

public class TenantManagementVerifier {

  TenantVerifier tenantVerifier;

  public TenantManagementVerifier(TenantVerifier tenantVerifier) {
    this.tenantVerifier = tenantVerifier;
  }

  public TenantManagementVerificationResult verify(TenantManagementRegistrationContext context) {

    VerificationResult verificationResult = tenantVerifier.verify(context.newTenant());

    if (!verificationResult.isValid()) {
      return TenantManagementVerificationResult.error(verificationResult, context.isDryRun());
    }

    return TenantManagementVerificationResult.success(verificationResult, context.isDryRun());
  }
}
