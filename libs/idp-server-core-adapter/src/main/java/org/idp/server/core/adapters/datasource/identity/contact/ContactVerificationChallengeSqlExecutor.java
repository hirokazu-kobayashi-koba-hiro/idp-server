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
import org.idp.server.core.openid.identity.UserIdentifier;
import org.idp.server.core.openid.identity.contact.ContactVerificationChallenge;
import org.idp.server.core.openid.identity.contact.ContactVerificationChallengeIdentifier;
import org.idp.server.core.openid.identity.contact.ContactVerificationChallengeQueries;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public interface ContactVerificationChallengeSqlExecutor {

  void insert(Tenant tenant, ContactVerificationChallenge challenge);

  Map<String, String> selectOneForUpdate(
      Tenant tenant,
      ContactVerificationChallengeIdentifier identifier,
      UserIdentifier userIdentifier);

  void updateAttempts(Tenant tenant, ContactVerificationChallenge challenge);

  void delete(Tenant tenant, ContactVerificationChallengeIdentifier identifier);

  void deleteAllBy(Tenant tenant, UserIdentifier userIdentifier);

  Map<String, String> selectOne(Tenant tenant, ContactVerificationChallengeIdentifier identifier);

  List<Map<String, String>> selectList(Tenant tenant, ContactVerificationChallengeQueries queries);

  Map<String, String> selectCount(Tenant tenant, ContactVerificationChallengeQueries queries);
}
