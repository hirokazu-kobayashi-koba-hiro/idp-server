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

package org.idp.server.core.adapters.datasource.identity.verification.application.query;

import java.util.List;
import java.util.Map;
import org.idp.server.core.extension.identity.exception.IdentityVerificationApplicationNotFoundException;
import org.idp.server.core.extension.identity.verification.application.model.IdentityVerificationApplication;
import org.idp.server.core.extension.identity.verification.application.model.IdentityVerificationApplicationIdentifier;
import org.idp.server.core.extension.identity.verification.application.model.IdentityVerificationApplicationQueries;
import org.idp.server.core.extension.identity.verification.application.model.IdentityVerificationApplications;
import org.idp.server.core.extension.identity.verification.repository.IdentityVerificationApplicationQueryRepository;
import org.idp.server.core.openid.identity.User;
import org.idp.server.platform.datasource.SqlTooManyResultsException;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class IdentityVerificationApplicationQueryDataSource
    implements IdentityVerificationApplicationQueryRepository {

  IdentityVerificationApplicationQuerySqlExecutor executor;
  LoggerWrapper log = LoggerWrapper.getLogger(IdentityVerificationApplicationQueryDataSource.class);

  public IdentityVerificationApplicationQueryDataSource(
      IdentityVerificationApplicationQuerySqlExecutor executor) {
    this.executor = executor;
  }

  @Override
  public IdentityVerificationApplication get(
      Tenant tenant, User user, IdentityVerificationApplicationIdentifier identifier) {
    Map<String, String> result = executor.selectOne(tenant, user, identifier);

    if (result == null || result.isEmpty()) {
      throw new IdentityVerificationApplicationNotFoundException(
          String.format("IdentityVerificationApplication not found (%s)", identifier.value()));
    }

    return ModelConverter.convert(result);
  }

  @Override
  public IdentityVerificationApplication get(
      Tenant tenant, IdentityVerificationApplicationIdentifier identifier) {
    Map<String, String> result = executor.selectOne(tenant, identifier);

    if (result == null || result.isEmpty()) {
      throw new IdentityVerificationApplicationNotFoundException(
          String.format("IdentityVerificationApplication not found (%s)", identifier.value()));
    }

    return ModelConverter.convert(result);
  }

  // TODO
  @Override
  public IdentityVerificationApplication get(Tenant tenant, String key, String identifier) {
    try {
      Map<String, String> result = executor.selectOneByDetail(tenant, key, identifier);

      if (result == null || result.isEmpty()) {
        throw new IdentityVerificationApplicationNotFoundException(
            String.format(
                "IdentityVerificationApplication not found key: %s id: %s", key, identifier));
      }

      return ModelConverter.convert(result);
    } catch (SqlTooManyResultsException tooManyResultsException) {

      log.error("IdentityVerificationApplication is too many found", tooManyResultsException);
      throw new IdentityVerificationApplicationNotFoundException(
          String.format(
              "IdentityVerificationApplication not found key: %s id: %s", key, identifier));
    }
  }

  @Override
  public IdentityVerificationApplications findAll(Tenant tenant, User user) {
    List<Map<String, String>> result = executor.selectList(tenant, user);

    if (result == null || result.isEmpty()) {
      return new IdentityVerificationApplications();
    }

    List<IdentityVerificationApplication> applicationList =
        result.stream().map(ModelConverter::convert).toList();
    return new IdentityVerificationApplications(applicationList);
  }

  @Override
  public IdentityVerificationApplications findList(
      Tenant tenant, User user, IdentityVerificationApplicationQueries queries) {
    List<Map<String, String>> result = executor.selectList(tenant, user, queries);

    if (result == null || result.isEmpty()) {
      return new IdentityVerificationApplications();
    }

    List<IdentityVerificationApplication> applicationList =
        result.stream().map(ModelConverter::convert).toList();
    return new IdentityVerificationApplications(applicationList);
  }

  @Override
  public long findTotalCount(
      Tenant tenant, User user, IdentityVerificationApplicationQueries queries) {
    Map<String, String> result = executor.selectCount(tenant, user, queries);

    if (result == null || result.isEmpty()) {
      return 0;
    }

    return Long.parseLong(result.get("count"));
  }
}
