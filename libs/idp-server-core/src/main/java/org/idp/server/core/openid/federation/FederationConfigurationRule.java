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
package org.idp.server.core.openid.federation;

import java.util.List;
import java.util.Map;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

/**
 * What one {@code type} of federation configuration requires of the configurations registered under
 * it, contributed by the feature that reads them.
 *
 * <p>The management API stores a configuration's payload as given and knows nothing of what it
 * means. A feature that relies on configurations of its own type says here what a usable one looks
 * like, what still depends on one, and whether it may be renamed — the same split as an identity
 * provider factory validating its own configuration class.
 */
public interface FederationConfigurationRule {

  FederationType type();

  /**
   * What is wrong with the configuration, for a registration or an update to be refused; empty when
   * it is usable.
   */
  List<String> problems(FederationConfiguration configuration);

  /**
   * What still depends on the configuration, by name and count, for a deletion to be refused; empty
   * when nothing does. The names are returned to the caller as they are.
   */
  Map<String, Long> dependents(Tenant tenant, FederationConfiguration configuration);

  /**
   * Whether {@code sso_provider} may change. Data that names the configuration by it would be left
   * pointing at nothing, so a type whose data does keeps it fixed.
   */
  boolean allowsRename();
}
