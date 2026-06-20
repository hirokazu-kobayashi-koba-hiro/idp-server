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

package org.idp.server.core.openid.identity.id_token;

import java.util.List;
import org.idp.server.platform.json.JsonReadable;

public class ClaimsObject implements JsonReadable {
  boolean essential;
  String value;
  List<String> values;
  String purpose;

  public ClaimsObject() {}

  public boolean essential() {
    return essential;
  }

  public String value() {
    return value;
  }

  public List<String> values() {
    return values;
  }

  /**
   * The {@code purpose} claims-request member (OIDC4IDA Implementer's Draft §5.1; dropped in
   * OIDC4IDA 1.0 final, but still exercised by the eKYC OP conformance suite, tag IA-9): the reason
   * the RP requests this claim, shown to the end-user. {@code null} when not requested.
   */
  public String purpose() {
    return purpose;
  }

  public boolean hasPurpose() {
    return purpose != null && !purpose.isEmpty();
  }
}
