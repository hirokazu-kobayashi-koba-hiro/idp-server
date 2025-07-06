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

package org.idp.server.core.adapters.datasource.identity.verification.result.query;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.idp.server.core.extension.identity.verification.exception.IdentityVerificationApplicationNotFoundException;
import org.idp.server.core.extension.identity.verification.repository.IdentityVerificationResultQueryRepository;
import org.idp.server.core.extension.identity.verification.result.IdentityVerificationResult;
import org.idp.server.core.extension.identity.verification.result.IdentityVerificationResultIdentifier;
import org.idp.server.core.extension.identity.verification.result.IdentityVerificationResultQueries;
import org.idp.server.core.oidc.identity.User;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class IdentityVerificationResultQueryDataSource
    implements IdentityVerificationResultQueryRepository {

  IdentityVerificationResultSqlExecutors executors;

  public IdentityVerificationResultQueryDataSource() {
    this.executors = new IdentityVerificationResultSqlExecutors();
  }

  @Override
  public List<IdentityVerificationResult> findList(
      Tenant tenant, User user, IdentityVerificationResultQueries queries) {
    IdentityVerificationResultSqlExecutor executor = executors.get(tenant.databaseType());

    List<Map<String, String>> result = executor.selectList(tenant, user, queries);

    if (result == null || result.isEmpty()) {
      return new ArrayList<>();
    }

    return result.stream().map(ModelConverter::convert).toList();
  }

  @Override
  public IdentityVerificationResult get(
      Tenant tenant, User user, IdentityVerificationResultIdentifier identifier) {
    IdentityVerificationResultSqlExecutor executor = executors.get(tenant.databaseType());

    Map<String, String> result = executor.selectOne(tenant, user, identifier);

    if (result == null || result.isEmpty()) {
      throw new IdentityVerificationApplicationNotFoundException(
          String.format("IdentityVerificationResult not found (%s)", identifier.value()));
    }

    return ModelConverter.convert(result);
  }
}
