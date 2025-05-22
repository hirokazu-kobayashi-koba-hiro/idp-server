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


package org.idp.server.core.extension.verifiable_credentials;

import java.util.Map;
import java.util.Objects;
import org.idp.server.basic.type.verifiablecredential.Format;

public class VerifiableCredentialCreators {

  Map<Format, VerifiableCredentialCreator> values;

  public VerifiableCredentialCreators(Map<Format, VerifiableCredentialCreator> values) {
    this.values = values;
  }

  public VerifiableCredentialCreator get(Format format) {
    VerifiableCredentialCreator verifiableCredentialCreator = values.get(format);
    if (Objects.isNull(verifiableCredentialCreator)) {
      throw new RuntimeException(String.format("unsupported format (%s)", format.name()));
    }
    return verifiableCredentialCreator;
  }
}
