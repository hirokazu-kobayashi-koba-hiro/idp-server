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

package org.idp.server.control_plane.management.identity.verification.handler;

import java.util.List;
import java.util.Map;
import org.idp.server.control_plane.management.identity.verification.IdentityVerificationConfigManagementContextBuilder;
import org.idp.server.control_plane.management.identity.verification.io.IdentityVerificationConfigManagementResponse;
import org.idp.server.control_plane.management.identity.verification.io.IdentityVerificationConfigManagementStatus;
import org.idp.server.core.extension.identity.verification.configuration.IdentityVerificationConfiguration;
import org.idp.server.core.extension.identity.verification.configuration.IdentityVerificationQueries;
import org.idp.server.core.extension.identity.verification.repository.IdentityVerificationConfigurationQueryRepository;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.type.RequestAttributes;

/**
 * Service for identity verification configuration list retrieval operations.
 *
 * <p>Handles business logic for retrieving lists of identity verification configurations. Part of
 * Handler/Service pattern.
 */
public class IdentityVerificationConfigFindListService
    implements IdentityVerificationConfigManagementService<IdentityVerificationQueries> {

  private final IdentityVerificationConfigurationQueryRepository queryRepository;

  public IdentityVerificationConfigFindListService(
      IdentityVerificationConfigurationQueryRepository queryRepository) {
    this.queryRepository = queryRepository;
  }

  @Override
  public IdentityVerificationConfigManagementResponse execute(
      IdentityVerificationConfigManagementContextBuilder builder,
      Tenant tenant,
      User operator,
      OAuthToken oAuthToken,
      IdentityVerificationQueries queries,
      RequestAttributes requestAttributes,
      boolean dryRun) {

    long totalCount = queryRepository.findTotalCount(tenant, queries);
    if (totalCount == 0) {
      Map<String, Object> response =
          Map.of(
              "list", List.of(),
              "total_count", totalCount,
              "limit", queries.limit(),
              "offset", queries.offset());
      return new IdentityVerificationConfigManagementResponse(
          IdentityVerificationConfigManagementStatus.OK, response);
    }

    List<IdentityVerificationConfiguration> configurations =
        queryRepository.findList(tenant, queries);

    // Note: For list operations, we don't populate builder.withBefore()
    // as there's no single "before" state for multiple items

    Map<String, Object> response =
        Map.of(
            "list",
            configurations.stream().map(IdentityVerificationConfiguration::toMap).toList(),
            "total_count",
            totalCount,
            "limit",
            queries.limit(),
            "offset",
            queries.offset());

    return new IdentityVerificationConfigManagementResponse(
        IdentityVerificationConfigManagementStatus.OK, response);
  }
}
