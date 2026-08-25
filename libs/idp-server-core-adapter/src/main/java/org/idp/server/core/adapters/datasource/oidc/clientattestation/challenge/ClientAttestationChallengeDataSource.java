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

package org.idp.server.core.adapters.datasource.oidc.clientattestation.challenge;

import java.util.Map;
import org.idp.server.core.openid.oauth.clientattestation.challenge.ClientAttestationChallenge;
import org.idp.server.core.openid.oauth.clientattestation.challenge.ClientAttestationChallengeRepository;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class ClientAttestationChallengeDataSource implements ClientAttestationChallengeRepository {

  ClientAttestationChallengeSqlExecutor executor;

  public ClientAttestationChallengeDataSource(ClientAttestationChallengeSqlExecutor executor) {
    this.executor = executor;
  }

  @Override
  public void register(Tenant tenant, ClientAttestationChallenge challenge) {
    executor.insert(tenant, challenge);
  }

  @Override
  public ClientAttestationChallenge find(Tenant tenant, String challenge) {
    Map<String, String> result = executor.selectOne(tenant, challenge);

    if (result == null || result.isEmpty()) {
      return new ClientAttestationChallenge();
    }

    return ModelConverter.convert(result);
  }
}
