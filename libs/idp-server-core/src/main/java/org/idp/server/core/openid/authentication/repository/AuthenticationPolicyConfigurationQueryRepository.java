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

package org.idp.server.core.openid.authentication.repository;

import java.util.List;
import org.idp.server.core.openid.authentication.policy.AuthenticationPolicyConfiguration;
import org.idp.server.core.openid.authentication.policy.AuthenticationPolicyConfigurationIdentifier;
import org.idp.server.core.openid.oauth.type.AuthFlow;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public interface AuthenticationPolicyConfigurationQueryRepository {

  AuthenticationPolicyConfiguration find(Tenant tenant, AuthFlow authFlow);

  AuthenticationPolicyConfiguration get(Tenant tenant, AuthFlow authFlow);

  AuthenticationPolicyConfiguration find(
      Tenant tenant, AuthenticationPolicyConfigurationIdentifier identifier);

  long findTotalCount(Tenant tenant);

  List<AuthenticationPolicyConfiguration> findList(Tenant tenant, int limit, int offset);
}
