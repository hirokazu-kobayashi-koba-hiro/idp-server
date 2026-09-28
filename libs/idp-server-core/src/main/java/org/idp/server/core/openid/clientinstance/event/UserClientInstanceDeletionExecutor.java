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
package org.idp.server.core.openid.clientinstance.event;

import java.util.List;
import java.util.Map;
import org.idp.server.core.openid.clientinstance.ClientInstance;
import org.idp.server.core.openid.clientinstance.ClientInstanceCommandRepository;
import org.idp.server.core.openid.clientinstance.ClientInstanceQueryRepository;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.event.UserLifecycleEvent;
import org.idp.server.core.openid.identity.event.UserLifecycleEventExecutor;
import org.idp.server.core.openid.identity.event.UserLifecycleEventResult;
import org.idp.server.core.openid.identity.event.UserLifecycleType;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

/**
 * Deletes the client instances bound to a deleted user, across every client.
 *
 * <p>Like the rest of the user's data, the instances go with the user rather than stay behind as
 * revoked rows holding the user's identifier and device evidence. Nothing is left to revive: the
 * user's tokens are deleted by {@code UserTokenDeletionExecutor}, and the key stays in the device.
 */
public class UserClientInstanceDeletionExecutor implements UserLifecycleEventExecutor {

  ClientInstanceQueryRepository clientInstanceQueryRepository;
  ClientInstanceCommandRepository clientInstanceCommandRepository;
  LoggerWrapper log = LoggerWrapper.getLogger(UserClientInstanceDeletionExecutor.class);

  public UserClientInstanceDeletionExecutor(
      ClientInstanceQueryRepository clientInstanceQueryRepository,
      ClientInstanceCommandRepository clientInstanceCommandRepository) {
    this.clientInstanceQueryRepository = clientInstanceQueryRepository;
    this.clientInstanceCommandRepository = clientInstanceCommandRepository;
  }

  @Override
  public UserLifecycleType lifecycleType() {
    return UserLifecycleType.DELETE;
  }

  @Override
  public String name() {
    return "client-instance-deletion";
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

      List<ClientInstance> instances =
          clientInstanceQueryRepository.findListByUser(tenant, user.sub());
      for (ClientInstance instance : instances) {
        clientInstanceCommandRepository.delete(
            tenant, instance.requestedClientId(), instance.identifier());
      }

      if (!instances.isEmpty()) {
        log.info(
            "Client instances deleted for deleted user: user={}, count={}",
            user.sub(),
            instances.size());
      }
      return UserLifecycleEventResult.success(name(), Map.of("deleted_count", instances.size()));
    } catch (Exception e) {

      log.error("UserLifecycleEventExecutor error: ", e.getMessage(), e);
      return UserLifecycleEventResult.failure(name(), Map.of("error", e.getMessage()));
    }
  }
}
