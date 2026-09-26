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

package org.idp.server.core.openid.clientinstance.registration;

import java.util.List;
import org.idp.server.core.openid.clientinstance.ClientInstance;

/**
 * A registered Client Instance, and the instances of the same user and client it took the place of.
 */
public class ClientInstanceRegistrationResult {

  ClientInstance registered;
  List<ClientInstance> superseded;

  public ClientInstanceRegistrationResult(
      ClientInstance registered, List<ClientInstance> superseded) {
    this.registered = registered;
    this.superseded = superseded;
  }

  public ClientInstance registered() {
    return registered;
  }

  /** Revoked by this registration, as they were before it. */
  public List<ClientInstance> superseded() {
    return superseded;
  }
}
