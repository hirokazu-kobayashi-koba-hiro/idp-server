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

package org.idp.server.control_plane.base.verifier;

import java.util.List;

public class VerificationResult {

  boolean valid;
  List<String> errors;

  private VerificationResult(boolean valid, List<String> errors) {
    this.valid = valid;
    this.errors = errors;
  }

  public static VerificationResult success() {
    return new VerificationResult(true, List.of());
  }

  public static VerificationResult failure(List<String> errors) {
    return new VerificationResult(false, errors);
  }

  public static VerificationResult empty() {
    return new VerificationResult(true, List.of());
  }

  public boolean isValid() {
    return valid;
  }

  public List<String> errors() {
    return errors;
  }
}
