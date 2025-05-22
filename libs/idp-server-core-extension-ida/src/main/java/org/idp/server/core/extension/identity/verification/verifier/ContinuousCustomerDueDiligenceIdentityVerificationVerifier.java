/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.extension.identity.verification.verifier;

import java.util.List;
import org.idp.server.core.extension.identity.verification.IdentityVerificationProcess;
import org.idp.server.core.extension.identity.verification.IdentityVerificationRequest;
import org.idp.server.core.extension.identity.verification.IdentityVerificationType;
import org.idp.server.core.extension.identity.verification.application.IdentityVerificationApplications;
import org.idp.server.core.extension.identity.verification.configuration.IdentityVerificationConfiguration;
import org.idp.server.core.oidc.identity.User;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class ContinuousCustomerDueDiligenceIdentityVerificationVerifier
    implements IdentityVerificationRequestVerifier {

  public boolean shouldVerify(
      Tenant tenant,
      User user,
      IdentityVerificationApplications applications,
      IdentityVerificationType type,
      IdentityVerificationProcess processes,
      IdentityVerificationRequest request,
      IdentityVerificationConfiguration verificationConfiguration) {

    return type.isContinuousCustomerDueDiligence();
  }

  @Override
  public IdentityVerificationRequestVerificationResult verify(
      Tenant tenant,
      User user,
      IdentityVerificationApplications applications,
      IdentityVerificationType type,
      IdentityVerificationProcess processes,
      IdentityVerificationRequest request,
      IdentityVerificationConfiguration verificationConfiguration) {

    if (!applications.containsApproved(verificationConfiguration.approvedTargetTypes())) {

      List<String> errors =
          List.of(
              String.format(
                  "user does not have approved application required any type (%s)",
                  verificationConfiguration.approvedTargetTypesAsString()));
      return IdentityVerificationRequestVerificationResult.failure(errors);
    }

    return IdentityVerificationRequestVerificationResult.success();
  }
}
