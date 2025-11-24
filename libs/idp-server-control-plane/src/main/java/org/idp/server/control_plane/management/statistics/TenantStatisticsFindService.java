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

package org.idp.server.control_plane.management.statistics;

import java.util.List;
import org.idp.server.control_plane.management.statistics.handler.TenantStatisticsManagementService;
import org.idp.server.control_plane.management.statistics.validator.TenantStatisticsRequestValidator;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.token.OAuthToken;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;
import org.idp.server.platform.statistics.TenantStatisticsData;
import org.idp.server.platform.statistics.TenantStatisticsQueries;
import org.idp.server.platform.statistics.repository.TenantStatisticsDataQueryRepository;
import org.idp.server.platform.type.RequestAttributes;

public class TenantStatisticsFindService
    implements TenantStatisticsManagementService<TenantStatisticsQueries> {

  private final TenantStatisticsDataQueryRepository repository;

  public TenantStatisticsFindService(TenantStatisticsDataQueryRepository repository) {
    this.repository = repository;
  }

  @Override
  public TenantStatisticsResponse execute(
      Tenant tenant,
      User operator,
      OAuthToken oAuthToken,
      TenantStatisticsQueries queries,
      RequestAttributes requestAttributes) {

    new TenantStatisticsRequestValidator(queries).validate();

    List<TenantStatisticsData> statistics = repository.findByDateRange(tenant, queries);

    return TenantStatisticsResponse.success(
        tenant.identifier().value(),
        queries.fromAsLocalDate().toString(),
        queries.toAsLocalDate().toString(),
        statistics);
  }
}
