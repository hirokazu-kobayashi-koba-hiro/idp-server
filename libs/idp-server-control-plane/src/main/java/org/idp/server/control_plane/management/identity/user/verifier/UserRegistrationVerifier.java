/*
 * Copyright 2025 Hirokazu Kobayashi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.idp.server.control_plane.management.identity.user.verifier;

import org.idp.server.control_plane.base.verifier.UserVerifier;
import org.idp.server.control_plane.base.verifier.VerificationResult;
import org.idp.server.control_plane.management.identity.user.UserRegistrationContext;

public class UserRegistrationVerifier {

  UserVerifier userVerifier;
  UserRegistrationRelatedDataVerifier userRegistrationRelatedDataVerifier;

  public UserRegistrationVerifier(
      UserVerifier userVerifier,
      UserRegistrationRelatedDataVerifier userRegistrationRelatedDataVerifier) {
    this.userVerifier = userVerifier;
    this.userRegistrationRelatedDataVerifier = userRegistrationRelatedDataVerifier;
  }

  public UserRegistrationVerificationResult verify(UserRegistrationContext context) {

    VerificationResult verificationResult = userVerifier.verify(context.tenant(), context.user());

    if (!verificationResult.isValid()) {
      return UserRegistrationVerificationResult.error(verificationResult, context.isDryRun());
    }
    VerificationResult rolesResult =
        userRegistrationRelatedDataVerifier.verifyRoles(context.tenant(), context.request());
    if (!rolesResult.isValid()) {
      return UserRegistrationVerificationResult.error(verificationResult, context.isDryRun());
    }

    VerificationResult tenantAssignmentsResult =
        userRegistrationRelatedDataVerifier.verifyTenantAssignments(context.request());
    if (!tenantAssignmentsResult.isValid()) {
      return UserRegistrationVerificationResult.error(verificationResult, context.isDryRun());
    }

    VerificationResult organizationAssignmentsResult =
        userRegistrationRelatedDataVerifier.verifyOrganizationAssignments(
            context.tenant(), context.request());
    if (!organizationAssignmentsResult.isValid()) {
      return UserRegistrationVerificationResult.error(verificationResult, context.isDryRun());
    }

    return UserRegistrationVerificationResult.success(verificationResult, context.isDryRun());
  }
}
