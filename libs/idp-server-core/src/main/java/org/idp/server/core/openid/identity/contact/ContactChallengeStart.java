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

package org.idp.server.core.openid.identity.contact;

import java.util.HashMap;
import java.util.Map;

/**
 * The outcome of starting a contact challenge (Issue #1416).
 *
 * <p>Which field carries the secret depends on who generated it. When idp-server generates the code
 * it holds the code; when the tenant delegates to an external verification service, that service
 * holds the code and idp-server holds only a reference used to ask it later.
 */
public class ContactChallengeStart {

  boolean succeeded;
  String verificationCode;
  Map<String, Object> externalReference;

  ContactChallengeStart(
      boolean succeeded, String verificationCode, Map<String, Object> externalReference) {
    this.succeeded = succeeded;
    this.verificationCode = verificationCode;
    this.externalReference = externalReference == null ? new HashMap<>() : externalReference;
  }

  /** idp-server generated and delivered the code. */
  public static ContactChallengeStart internal(String verificationCode) {
    return new ContactChallengeStart(true, verificationCode, new HashMap<>());
  }

  /**
   * An external service generated and delivered the code; {@code externalReference} is what
   * identifies that exchange when the code is later submitted for verification.
   */
  public static ContactChallengeStart external(Map<String, Object> externalReference) {
    return new ContactChallengeStart(true, null, externalReference);
  }

  public static ContactChallengeStart failure() {
    return new ContactChallengeStart(false, null, new HashMap<>());
  }

  public boolean isSucceeded() {
    return succeeded;
  }

  public String verificationCode() {
    return verificationCode;
  }

  public Map<String, Object> externalReference() {
    return externalReference;
  }

  public boolean isExternal() {
    return verificationCode == null;
  }
}
