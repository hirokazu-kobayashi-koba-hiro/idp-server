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

package org.idp.server.core.openid.oauth.type.verifiablecredential;

import java.util.ArrayList;
import java.util.List;

public class VerifiableCredentialContext {
  public static List<String> values = new ArrayList<>();

  static {
    values.add("https://www.w3.org/2018/credentials/v1");
    values.add("https://www.w3.org/2018/credentials/examples/v1");
  }
}
