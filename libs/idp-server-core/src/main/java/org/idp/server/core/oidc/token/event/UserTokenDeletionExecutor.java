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

package org.idp.server.core.oidc.token.event;

import java.util.Map;
import org.idp.server.core.oidc.identity.User;
import org.idp.server.core.oidc.identity.event.UserLifecycleEvent;
import org.idp.server.core.oidc.identity.event.UserLifecycleEventExecutor;
import org.idp.server.core.oidc.identity.event.UserLifecycleEventResult;
import org.idp.server.core.oidc.identity.event.UserLifecycleType;
import org.idp.server.core.oidc.token.repository.OAuthTokenOperationCommandRepository;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

public class UserTokenDeletionExecutor implements UserLifecycleEventExecutor {

  OAuthTokenOperationCommandRepository oAuthTokenOperationCommandRepository;
  LoggerWrapper log = LoggerWrapper.getLogger(UserTokenDeletionExecutor.class);

  public UserTokenDeletionExecutor(
      OAuthTokenOperationCommandRepository oAuthTokenOperationCommandRepository) {
    this.oAuthTokenOperationCommandRepository = oAuthTokenOperationCommandRepository;
  }

  @Override
  public UserLifecycleType lifecycleType() {
    return UserLifecycleType.DELETE;
  }

  @Override
  public String name() {
    return "token-deletion";
  }

  @Override
  public boolean shouldExecute(UserLifecycleEvent userLifecycleEvent) {
    return userLifecycleEvent.lifecycleType() == UserLifecycleType.DELETE;
  }

  @Override
  public UserLifecycleEventResult execute(UserLifecycleEvent userLifecycleEvent) {
    try {
      Tenant tenant = userLifecycleEvent.tenant();
      User user = userLifecycleEvent.user();
      oAuthTokenOperationCommandRepository.deleteAll(tenant, user);
      return UserLifecycleEventResult.success(name(), Map.of());
    } catch (Exception e) {

      log.error("UserLifecycleEventExecutor error: ", e.getMessage(), e);
      return UserLifecycleEventResult.failure(name(), Map.of("error", e.getMessage()));
    }
  }
}
