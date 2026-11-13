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
package org.idp.server.platform.crypto.datakey;

import org.idp.server.platform.multi_tenancy.tenant.Tenant;

/**
 * A use of tenant data keys, contributed by the feature that encrypts under them.
 *
 * <p>Data keys are per tenant and purpose. The purpose's owner knows what it encrypts, and so how
 * much of it a given generation still holds — what an administrator needs to see before an older
 * generation can be considered done.
 */
public interface DataKeyPurpose {

  /** The purpose's name, as stored with its data keys. */
  String name();

  /** The name the count below is reported under, e.g. {@code linked_external_accounts}. */
  String dataName();

  /** How much of this purpose's data the data key still encrypts. */
  long countUnder(Tenant tenant, String dataKeyId);
}
