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

package org.idp.server.control_plane.management.identity.verification.result.handler;

import org.idp.server.control_plane.management.exception.ResourceNotFoundException;
import org.idp.server.control_plane.management.identity.verification.result.IdentityVerificationResultManagementContextBuilder;
import org.idp.server.control_plane.management.identity.verification.result.io.IdentityVerificationResultFindRequest;
import org.idp.server.control_plane.management.identity.verification.result.io.IdentityVerificationResultManagementResponse;
import org.idp.server.control_plane.management.identity.verification.result.io.IdentityVerificationResultManagementStatus;
import org.idp.server.core.extension.identity.verification.repository.IdentityVerificationResultQueryRepository;
import org.idp.server.core.extension.identity.verification.result.IdentityVerificationResult;
import org.idp.server.core.extension.identity.verification.result.IdentityVerificationResultIdentifier;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.type.RequestAttributes;

public class IdentityVerificationResultFindService
    implements IdentityVerificationResultManagementService<IdentityVerificationResultFindRequest> {

  private final IdentityVerificationResultQueryRepository queryRepository;

  public IdentityVerificationResultFindService(
      IdentityVerificationResultQueryRepository queryRepository) {
    this.queryRepository = queryRepository;
  }

  @Override
  public IdentityVerificationResultManagementResponse execute(
      IdentityVerificationResultManagementContextBuilder builder,
      Tenant tenant,
      User operator,
      OAuthToken oAuthToken,
      IdentityVerificationResultFindRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    IdentityVerificationResultIdentifier identifier = request.identifier();
    IdentityVerificationResult result = queryRepository.get(tenant, identifier);

    if (!result.exists()) {
      throw new ResourceNotFoundException(
          "Identity verification result not found: " + identifier.value());
    }

    builder.withBefore(result);

    return new IdentityVerificationResultManagementResponse(
        IdentityVerificationResultManagementStatus.OK, result.toMap());
  }
}
