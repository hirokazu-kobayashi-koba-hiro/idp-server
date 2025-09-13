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

package org.idp.server.core.adapters.datasource.identity.verification.application.command;

import org.idp.server.core.extension.identity.verification.application.model.IdentityVerificationApplication;
import org.idp.server.core.extension.identity.verification.application.model.IdentityVerificationApplicationIdentifier;
import org.idp.server.core.extension.identity.verification.repository.IdentityVerificationApplicationCommandRepository;
import org.idp.server.core.openid.identity.User;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class IdentityVerificationApplicationCommandDataSource
    implements IdentityVerificationApplicationCommandRepository {

  IdentityVerificationApplicationCommandSqlExecutor executor;

  public IdentityVerificationApplicationCommandDataSource(
      IdentityVerificationApplicationCommandSqlExecutor executor) {
    this.executor = executor;
  }

  @Override
  public void register(Tenant tenant, IdentityVerificationApplication application) {
    executor.insert(tenant, application);
  }

  @Override
  public void update(Tenant tenant, IdentityVerificationApplication application) {
    executor.update(tenant, application);
  }

  @Override
  public void delete(
      Tenant tenant, User user, IdentityVerificationApplicationIdentifier identifier) {
    executor.delete(tenant, user, identifier);
  }
}
