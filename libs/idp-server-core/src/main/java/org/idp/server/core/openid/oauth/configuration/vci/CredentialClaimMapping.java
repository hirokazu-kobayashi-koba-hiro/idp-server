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
package org.idp.server.core.openid.oauth.configuration.vci;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.platform.json.JsonReadable;

/**
 * One claim of an issued credential: its name, where its value comes from, and whether the holder
 * may withhold it.
 *
 * <pre>{@code
 * { "name": "given_name", "from": "$.given_name", "selectively_disclosable": true }
 * }</pre>
 *
 * <p>{@code from} is a JSONPath into the user's claims (see {@code CredentialSubjectSource}).
 * Selective disclosure is the default: a credential reveals only what its holder chooses.
 */
public class CredentialClaimMapping implements JsonReadable {

  String name;
  String from;
  boolean selectivelyDisclosable = true;

  public CredentialClaimMapping() {}

  public String name() {
    return name;
  }

  public String from() {
    return from;
  }

  public boolean selectivelyDisclosable() {
    return selectivelyDisclosable;
  }

  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    map.put("name", name);
    map.put("from", from);
    map.put("selectively_disclosable", selectivelyDisclosable);
    return map;
  }
}
