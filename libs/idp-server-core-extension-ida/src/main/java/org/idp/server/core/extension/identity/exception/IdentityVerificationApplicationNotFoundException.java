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

package org.idp.server.core.extension.identity.exception;

import org.idp.server.core.extension.identity.verification.application.model.IdentityVerificationApplicationIdentifier;
import org.idp.server.platform.exception.NotFoundException;

/**
 * An identity verification application that cannot be used for the request.
 *
 * <p>Build it with {@link #byId} or {@link #byKey}. The repository uses them when nothing matches,
 * and the entry services use them when an application exists but was filed under another type, so
 * the two cases produce the same message and cannot be told apart (Issue #1969).
 */
public class IdentityVerificationApplicationNotFoundException extends NotFoundException {
  public IdentityVerificationApplicationNotFoundException(String message) {
    super(message);
  }

  /** No application with this id (or none the request may use). */
  public static IdentityVerificationApplicationNotFoundException byId(
      IdentityVerificationApplicationIdentifier identifier) {
    return new IdentityVerificationApplicationNotFoundException(
        String.format("IdentityVerificationApplication not found (%s)", identifier.value()));
  }

  /** No application whose {@code key} is {@code value} (or none the request may use). */
  public static IdentityVerificationApplicationNotFoundException byKey(String key, String value) {
    return new IdentityVerificationApplicationNotFoundException(
        String.format("IdentityVerificationApplication not found key: %s id: %s", key, value));
  }
}
