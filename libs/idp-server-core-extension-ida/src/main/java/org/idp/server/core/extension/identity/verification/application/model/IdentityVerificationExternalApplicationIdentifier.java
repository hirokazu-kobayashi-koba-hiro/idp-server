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

package org.idp.server.core.extension.identity.verification.application.model;

import java.util.Objects;

/**
 * Identifier issued by an external identity verification service (e.g. eKYC vendor) for a single
 * application. Stored in its own DB column ({@code external_application_id}) so that callback
 * lookups can use a B-tree index instead of the JSONB ({@code application_details}) GIN index.
 *
 * <p>The format is opaque to idp-server — it can be a UUID, a vendor-specific token, etc.
 */
public class IdentityVerificationExternalApplicationIdentifier {
  String value;

  public IdentityVerificationExternalApplicationIdentifier() {}

  public IdentityVerificationExternalApplicationIdentifier(String value) {
    // Empty string is normalized to null so that "absent" maps to a single representation in DB.
    // Required for the (tenant_id, external_application_id) UNIQUE index: empty strings would
    // collide under that constraint, while NULL is exempt.
    this.value = (value == null || value.isEmpty()) ? null : value;
  }

  public String value() {
    return value;
  }

  public boolean exists() {
    return value != null && !value.isEmpty();
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) return false;
    IdentityVerificationExternalApplicationIdentifier that =
        (IdentityVerificationExternalApplicationIdentifier) o;
    return Objects.equals(value, that.value);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(value);
  }
}
