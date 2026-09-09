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

package org.idp.server.core.adapters.datasource.identity.email;

import java.util.Map;
import java.util.Objects;
import org.idp.server.core.openid.identity.UserIdentifier;
import org.idp.server.core.openid.identity.email.EmailVerificationChallenge;
import org.idp.server.core.openid.identity.email.EmailVerificationChallengeIdentifier;
import org.idp.server.core.openid.identity.email.EmailVerificationChallengeRepository;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class EmailVerificationChallengeDataSource implements EmailVerificationChallengeRepository {

  EmailVerificationChallengeSqlExecutor executor;

  public EmailVerificationChallengeDataSource(EmailVerificationChallengeSqlExecutor executor) {
    this.executor = executor;
  }

  @Override
  public void register(Tenant tenant, EmailVerificationChallenge challenge) {
    executor.insert(tenant, challenge);
  }

  @Override
  public EmailVerificationChallenge findForUpdate(
      Tenant tenant,
      EmailVerificationChallengeIdentifier identifier,
      UserIdentifier userIdentifier) {

    Map<String, String> result = executor.selectOneForUpdate(tenant, identifier, userIdentifier);

    if (Objects.isNull(result) || result.isEmpty()) {
      return new EmailVerificationChallenge();
    }

    return ModelConverter.convert(result);
  }

  @Override
  public void countUpAttempts(Tenant tenant, EmailVerificationChallenge challenge) {
    executor.updateAttempts(tenant, challenge);
  }

  @Override
  public void delete(Tenant tenant, EmailVerificationChallengeIdentifier identifier) {
    executor.delete(tenant, identifier);
  }

  @Override
  public void deleteAllBy(Tenant tenant, UserIdentifier userIdentifier) {
    executor.deleteAllBy(tenant, userIdentifier);
  }
}
