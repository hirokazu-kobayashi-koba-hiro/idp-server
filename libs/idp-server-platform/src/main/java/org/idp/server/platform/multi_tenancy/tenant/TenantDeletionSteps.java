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
package org.idp.server.platform.multi_tenancy.tenant;

import java.util.List;
import org.idp.server.platform.dependency.ApplicationComponentContainer;
import org.idp.server.platform.log.LoggerWrapper;
import org.idp.server.platform.plugin.PluginLoader;

/** Every feature's work before a tenant is deleted, run in turn. */
public class TenantDeletionSteps {

  private static final LoggerWrapper log = LoggerWrapper.getLogger(TenantDeletionSteps.class);

  List<TenantDeletionStep> steps;

  public TenantDeletionSteps(List<TenantDeletionStep> steps) {
    this.steps = steps;
  }

  public static TenantDeletionSteps load(
      ApplicationComponentContainer applicationComponentContainer) {
    List<TenantDeletionStep> steps =
        PluginLoader.loadFromInternalModule(TenantDeletionStepFactory.class).stream()
            .map(factory -> factory.create(applicationComponentContainer))
            .toList();
    steps.forEach(step -> log.info("Tenant deletion step registered: {}", step.name()));
    return new TenantDeletionSteps(steps);
  }

  public void runBefore(Tenant tenant) {
    steps.forEach(step -> step.beforeDeletion(tenant));
  }
}
