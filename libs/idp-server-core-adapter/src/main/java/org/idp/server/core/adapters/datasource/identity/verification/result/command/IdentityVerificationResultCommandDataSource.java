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

package org.idp.server.core.adapters.datasource.identity.verification.result.command;

import org.idp.server.core.extension.identity.verification.repository.IdentityVerificationResultCommandRepository;
import org.idp.server.core.extension.identity.verification.result.IdentityVerificationResult;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class IdentityVerificationResultCommandDataSource
    implements IdentityVerificationResultCommandRepository {

  IdentityVerificationResultCommandSqlExecutors executors;

  public IdentityVerificationResultCommandDataSource() {
    this.executors = new IdentityVerificationResultCommandSqlExecutors();
  }

  @Override
  public void register(Tenant tenant, IdentityVerificationResult result) {
    IdentityVerificationResultCommandSqlExecutor executor = executors.get(tenant.databaseType());
    executor.insert(tenant, result);
  }
}
