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

/**
 * Work a feature does before a tenant is deleted.
 *
 * <p>A tenant's rows go with it through foreign key cascades, which do nothing outside this
 * database. A feature holding something that must be undone elsewhere — tokens to revoke at an
 * external IdP — does it here, while the rows still say what to undo.
 */
public interface TenantDeletionStep {

  String name();

  void beforeDeletion(Tenant tenant);
}
