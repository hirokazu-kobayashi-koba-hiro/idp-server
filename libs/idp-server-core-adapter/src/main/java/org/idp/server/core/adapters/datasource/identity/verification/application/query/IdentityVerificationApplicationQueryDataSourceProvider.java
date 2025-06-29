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

package org.idp.server.core.adapters.datasource.identity.verification.application.query;

import org.idp.server.core.extension.identity.verification.repository.IdentityVerificationApplicationQueryRepository;
import org.idp.server.platform.dependency.ApplicationComponentDependencyContainer;
import org.idp.server.platform.dependency.ApplicationComponentProvider;

public class IdentityVerificationApplicationQueryDataSourceProvider
    implements ApplicationComponentProvider<IdentityVerificationApplicationQueryRepository> {

  @Override
  public Class<IdentityVerificationApplicationQueryRepository> type() {
    return IdentityVerificationApplicationQueryRepository.class;
  }

  @Override
  public IdentityVerificationApplicationQueryRepository provide(
      ApplicationComponentDependencyContainer container) {
    return new IdentityVerificationApplicationQueryDataSource();
  }
}
