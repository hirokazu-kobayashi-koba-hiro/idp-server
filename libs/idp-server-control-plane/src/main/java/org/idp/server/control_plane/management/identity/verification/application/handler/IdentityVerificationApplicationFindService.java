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

package org.idp.server.control_plane.management.identity.verification.application.handler;

import org.idp.server.control_plane.management.exception.ResourceNotFoundException;
import org.idp.server.control_plane.management.identity.verification.application.IdentityVerificationApplicationManagementContextBuilder;
import org.idp.server.control_plane.management.identity.verification.application.io.IdentityVerificationApplicationFindRequest;
import org.idp.server.control_plane.management.identity.verification.application.io.IdentityVerificationApplicationManagementResponse;
import org.idp.server.control_plane.management.identity.verification.application.io.IdentityVerificationApplicationManagementStatus;
import org.idp.server.core.extension.identity.verification.application.model.IdentityVerificationApplication;
import org.idp.server.core.extension.identity.verification.application.model.IdentityVerificationApplicationIdentifier;
import org.idp.server.core.extension.identity.verification.repository.IdentityVerificationApplicationQueryRepository;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.type.RequestAttributes;

public class IdentityVerificationApplicationFindService
    implements IdentityVerificationApplicationManagementService<
        IdentityVerificationApplicationFindRequest> {

  private final IdentityVerificationApplicationQueryRepository queryRepository;

  public IdentityVerificationApplicationFindService(
      IdentityVerificationApplicationQueryRepository queryRepository) {
    this.queryRepository = queryRepository;
  }

  @Override
  public IdentityVerificationApplicationManagementResponse execute(
      IdentityVerificationApplicationManagementContextBuilder builder,
      Tenant tenant,
      User operator,
      OAuthToken oAuthToken,
      IdentityVerificationApplicationFindRequest request,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    IdentityVerificationApplicationIdentifier identifier = request.identifier();
    IdentityVerificationApplication application = queryRepository.get(tenant, identifier);

    if (!application.exists()) {
      throw new ResourceNotFoundException(
          "Identity verification application not found: " + identifier.value());
    }

    builder.withBefore(application);

    return new IdentityVerificationApplicationManagementResponse(
        IdentityVerificationApplicationManagementStatus.OK, application.toMap());
  }
}
