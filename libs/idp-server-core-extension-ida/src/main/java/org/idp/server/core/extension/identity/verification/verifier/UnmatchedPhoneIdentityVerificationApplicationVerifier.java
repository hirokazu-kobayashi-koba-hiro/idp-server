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
import java.util.Map;
import org.idp.server.basic.json.JsonConverter;
import org.idp.server.basic.json.JsonNodeWrapper;
import org.idp.server.core.extension.identity.verification.IdentityVerificationProcess;
import org.idp.server.core.extension.identity.verification.IdentityVerificationRequest;
import org.idp.server.core.extension.identity.verification.IdentityVerificationType;
import org.idp.server.core.extension.identity.verification.application.IdentityVerificationApplications;
import org.idp.server.core.extension.identity.verification.configuration.IdentityVerificationConfiguration;
import org.idp.server.core.extension.identity.verification.configuration.IdentityVerificationProcessConfiguration;
import org.idp.server.core.oidc.identity.User;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class UnmatchedPhoneIdentityVerificationApplicationVerifier
    implements IdentityVerificationRequestVerifier {

  JsonConverter jsonConverter = JsonConverter.snakeCaseInstance();

  @Override
  public boolean shouldVerify(
      Tenant tenant,
      User user,
      IdentityVerificationApplications applications,
      IdentityVerificationType type,
      IdentityVerificationProcess processes,
      IdentityVerificationRequest request,
      IdentityVerificationConfiguration verificationConfiguration) {

    IdentityVerificationProcessConfiguration processConfig =
        verificationConfiguration.getProcessConfig(processes);
    Map<String, Object> verificationSchema = processConfig.requestVerificationSchema();

    if (verificationSchema == null || verificationSchema.isEmpty()) {
      return false;
    }

    JsonNodeWrapper jsonNodeWrapper = JsonNodeWrapper.fromObject(verificationSchema);
    return jsonNodeWrapper.optValueAsBoolean("unmatched_user_claims_phone", false);
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

    IdentityVerificationProcessConfiguration processConfig =
        verificationConfiguration.getProcessConfig(processes);
    Map<String, Object> verificationSchema = processConfig.requestVerificationSchema();
    JsonNodeWrapper jsonNodeWrapper = jsonConverter.readTree(verificationSchema);
    JsonNodeWrapper unmatchedUserClaims =
        jsonNodeWrapper.getValueAsJsonNode("unmatched_user_claims_phone");

    String property = unmatchedUserClaims.getValueOrEmptyAsString("property");
    String requestValue = request.optValueAsString(property, "");

    if (!requestValue.equals(user.phoneNumber())) {
      return IdentityVerificationRequestVerificationResult.failure(
          List.of("PhoneNumber does not match"));
    }

    return IdentityVerificationRequestVerificationResult.success();
  }
}
