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

package org.idp.server.core.adapters.datasource.oidc.clientinstance.registration;

import java.util.Map;
import org.idp.server.core.openid.clientinstance.registration.ClientInstanceRegistrationChallenge;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public interface ClientInstanceRegistrationChallengeSqlExecutor {

  void insert(Tenant tenant, ClientInstanceRegistrationChallenge challenge);

  Map<String, String> selectOne(Tenant tenant, String challenge);

  /**
   * Stamps used_at only when it is still null.
   *
   * @return number of updated rows; 0 means the challenge had already been consumed
   */
  int updateUsedAt(Tenant tenant, String challenge);
}
