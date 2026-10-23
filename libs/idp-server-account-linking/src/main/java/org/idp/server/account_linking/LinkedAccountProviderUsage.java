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

package org.idp.server.account_linking;

import java.util.Map;
import org.idp.server.account_linking.repository.LinkedExternalAccountQueryRepository;
import org.idp.server.core.openid.federation.FederationConfiguration;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

/**
 * How many linked external accounts depend on a federation configuration.
 *
 * <p>A link names its provider by the configuration's {@code sso_provider}, and revoking its stored
 * tokens needs that configuration's endpoints and client credentials. Removing or renaming a
 * configuration that links still use would leave their tokens alive at the external IdP with no way
 * left to revoke them, so the management API asks this first.
 */
public class LinkedAccountProviderUsage {

  LinkedExternalAccountQueryRepository accountQueryRepository;

  public LinkedAccountProviderUsage(LinkedExternalAccountQueryRepository accountQueryRepository) {
    this.accountQueryRepository = accountQueryRepository;
  }

  /** The number of links that resolve to this configuration; 0 for one linking does not use. */
  public long linksOf(Tenant tenant, FederationConfiguration configuration) {
    if (!AccountLinkingConfigurationResolver.FEDERATION_TYPE.equals(configuration.type())) {
      return 0;
    }
    return accountQueryRepository.findTotalCount(
        tenant,
        new LinkedExternalAccountQueries(Map.of("provider", configuration.ssoProvider().name())));
  }

  /** Whether an update moves the configuration away from the name or type links resolve it by. */
  public static boolean changesLinkingKey(
      FederationConfiguration before, FederationConfiguration after) {
    return !before.type().equals(after.type())
        || !before.ssoProvider().name().equals(after.ssoProvider().name());
  }
}
