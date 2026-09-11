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

package org.idp.server.core.adapters.datasource.identity.contact;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.idp.server.core.openid.identity.UserIdentifier;
import org.idp.server.core.openid.identity.contact.ContactVerificationChallenge;
import org.idp.server.core.openid.identity.contact.ContactVerificationChallengeIdentifier;
import org.idp.server.core.openid.identity.contact.ContactVerificationChallengeQueries;
import org.idp.server.core.openid.identity.contact.ContactVerificationChallengeRepository;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class ContactVerificationChallengeDataSource
    implements ContactVerificationChallengeRepository {

  ContactVerificationChallengeSqlExecutor executor;

  public ContactVerificationChallengeDataSource(ContactVerificationChallengeSqlExecutor executor) {
    this.executor = executor;
  }

  @Override
  public void register(Tenant tenant, ContactVerificationChallenge challenge) {
    executor.insert(tenant, challenge);
  }

  @Override
  public ContactVerificationChallenge findForUpdate(
      Tenant tenant,
      ContactVerificationChallengeIdentifier identifier,
      UserIdentifier userIdentifier) {

    Map<String, String> result = executor.selectOneForUpdate(tenant, identifier, userIdentifier);

    if (Objects.isNull(result) || result.isEmpty()) {
      return new ContactVerificationChallenge();
    }

    return ModelConverter.convert(result);
  }

  @Override
  public void countUpAttempts(Tenant tenant, ContactVerificationChallenge challenge) {
    executor.updateAttempts(tenant, challenge);
  }

  @Override
  public void delete(Tenant tenant, ContactVerificationChallengeIdentifier identifier) {
    executor.delete(tenant, identifier);
  }

  @Override
  public ContactVerificationChallenge find(
      Tenant tenant, ContactVerificationChallengeIdentifier identifier) {
    Map<String, String> result = executor.selectOne(tenant, identifier);
    if (Objects.isNull(result) || result.isEmpty()) {
      return new ContactVerificationChallenge();
    }
    return ModelConverter.convert(result);
  }

  @Override
  public List<ContactVerificationChallenge> findList(
      Tenant tenant, ContactVerificationChallengeQueries queries) {
    List<Map<String, String>> results = executor.selectList(tenant, queries);
    if (Objects.isNull(results) || results.isEmpty()) {
      return List.of();
    }
    return results.stream().map(ModelConverter::convert).toList();
  }

  @Override
  public long findTotalCount(Tenant tenant, ContactVerificationChallengeQueries queries) {
    Map<String, String> result = executor.selectCount(tenant, queries);
    if (Objects.isNull(result) || result.isEmpty()) {
      return 0;
    }
    return Long.parseLong(result.get("count"));
  }

  @Override
  public void deleteAllBy(Tenant tenant, UserIdentifier userIdentifier) {
    executor.deleteAllBy(tenant, userIdentifier);
  }
}
