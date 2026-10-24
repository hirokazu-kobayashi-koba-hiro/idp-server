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
package org.idp.server.control_plane.management.tenant;

import java.util.Set;
import org.idp.server.account_linking.AccountLinkingTokenCipher;

/** The purposes a tenant's data key exists for, and so the ones an administrator may rotate. */
public class DataKeyPurposes {

  public static final String DEFAULT = AccountLinkingTokenCipher.PURPOSE;

  public static final Set<String> ALL = Set.of(AccountLinkingTokenCipher.PURPOSE);

  public static boolean isKnown(String purpose) {
    return ALL.contains(purpose);
  }
}
