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

package org.idp.server.core.openid.extension.attestation.android;

import org.idp.server.core.openid.clientinstance.registration.PlatformAttestationVerificationException;

/**
 * Raised when an Android key attestation cannot be parsed or does not hold.
 *
 * <p>A subtype of the SPI failure so that a caller only has to know the contract: whether the
 * evidence was malformed, unsigned by a trusted root or simply not configured for, the registration
 * is rejected the same way.
 */
public class AndroidKeyAttestationException extends PlatformAttestationVerificationException {

  public AndroidKeyAttestationException(String message) {
    super(message);
  }

  public AndroidKeyAttestationException(String message, Throwable cause) {
    super(message, cause);
  }
}
