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

package org.idp.server.usecases.application.system;

import java.util.ArrayList;
import java.util.List;
import org.idp.server.core.openid.identity.event.*;
import org.idp.server.platform.datasource.Transaction;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.multi_tenancy.tenant.TenantIdentifier;

@Transaction
public class UserLifecycleEventEntryService implements UserLifecycleEventApi {

  UserLifecycleEventExecutorsMap userLifecycleEventExecutorsMap;
  UserLifecycleEventResultCommandRepository resultCommandRepository;
  LoggerWrapper log = LoggerWrapper.getLogger(UserLifecycleEventEntryService.class);

  public UserLifecycleEventEntryService(
      UserLifecycleEventExecutorsMap userLifecycleEventExecutorsMap,
      UserLifecycleEventResultCommandRepository resultCommandRepository) {
    this.userLifecycleEventExecutorsMap = userLifecycleEventExecutorsMap;
    this.resultCommandRepository = resultCommandRepository;
  }

  @Override
  public void handle(TenantIdentifier tenantIdentifier, UserLifecycleEvent userLifecycleEvent) {
    log.info("UserLifecycleEventEntryService.handle: " + userLifecycleEvent.lifecycleType().name());

    List<UserLifecycleEventResult> results = new ArrayList<>();
    UserLifecycleEventExecutors userLifecycleEventExecutors =
        userLifecycleEventExecutorsMap.find(userLifecycleEvent.lifecycleType());

    for (UserLifecycleEventExecutor executor : userLifecycleEventExecutors) {

      if (executor.shouldExecute(userLifecycleEvent)) {
        log.info("UserLifecycleEventEntryService.execute: " + executor.name());
        UserLifecycleEventResult deletionResult = executor.execute(userLifecycleEvent);
        results.add(deletionResult);
      }
    }

    resultCommandRepository.register(userLifecycleEvent.tenant(), userLifecycleEvent, results);
  }
}
