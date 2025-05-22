/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.extension.identity.verification.verifier;

import java.util.ArrayList;
import java.util.List;
import org.idp.server.core.extension.identity.verification.IdentityVerificationProcess;
import org.idp.server.core.extension.identity.verification.IdentityVerificationRequest;
import org.idp.server.core.extension.identity.verification.IdentityVerificationType;
import org.idp.server.core.extension.identity.verification.application.IdentityVerificationApplications;
import org.idp.server.core.extension.identity.verification.configuration.IdentityVerificationConfiguration;
import org.idp.server.core.oidc.identity.User;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class IdentityVerificationRequestVerifiers {

  List<IdentityVerificationRequestVerifier> verifiers;

  public IdentityVerificationRequestVerifiers() {
    this.verifiers = new ArrayList<>();
    this.verifiers.add(new DenyDuplicateIdentityVerificationApplicationVerifier());
    this.verifiers.add(new UnmatchedEmailIdentityVerificationApplicationVerifier());
    this.verifiers.add(new UnmatchedPhoneIdentityVerificationApplicationVerifier());
    this.verifiers.add(new ContinuousCustomerDueDiligenceIdentityVerificationVerifier());
  }

  public IdentityVerificationRequestVerificationResult verify(
      Tenant tenant,
      User user,
      IdentityVerificationApplications applications,
      IdentityVerificationType type,
      IdentityVerificationProcess processes,
      IdentityVerificationRequest request,
      IdentityVerificationConfiguration verificationConfiguration) {

    for (IdentityVerificationRequestVerifier verifier : verifiers) {

      if (!verifier.shouldVerify(
          tenant, user, applications, type, processes, request, verificationConfiguration)) {
        continue;
      }

      IdentityVerificationRequestVerificationResult verifyResult =
          verifier.verify(
              tenant, user, applications, type, processes, request, verificationConfiguration);

      if (verifyResult.isError()) {
        return verifyResult;
      }
    }

    return IdentityVerificationRequestVerificationResult.success();
  }
}
