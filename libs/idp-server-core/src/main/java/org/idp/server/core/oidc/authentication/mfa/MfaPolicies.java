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

package org.idp.server.core.oidc.authentication.mfa;

import java.util.HashMap;
import java.util.Map;
import org.idp.server.basic.type.AuthFlow;
import org.idp.server.core.oidc.configuration.authentication.AuthenticationPolicy;
import org.idp.server.platform.exception.UnSupportedException;
import org.idp.server.platform.json.JsonConverter;

public class MfaPolicies {

  Map<AuthFlow, AuthenticationPolicy> policies;

  public MfaPolicies() {
    policies = new HashMap<>();
    policies.put(AuthFlow.FIDO_UAF_REGISTRATION, createFidoUafPolicy());
    policies.put(AuthFlow.FIDO_UAF_DEREGISTRATION, createFidoUafDeregistrationPolicy());
  }

  public AuthenticationPolicy get(AuthFlow authFlow) {
    AuthenticationPolicy authenticationPolicy = policies.get(authFlow);

    if (authenticationPolicy == null) {
      throw new UnSupportedException("No authentication policy found for flow " + authFlow.value());
    }

    return authenticationPolicy;
  }

  private AuthenticationPolicy createFidoUafPolicy() {
    String json =
        """
                {
                  "id": "fido-uaf-registration",
                  "priority": 100,
                  "available_methods": [
                    "fido-uaf"
                  ],
                  "success_conditions": {
                    "any_of": [
                      {
                        "type": "fido-uaf-registration",
                        "success_count": 1
                      }
                    ]
                  },
                  "failure_conditions": {
                    "any_of": [
                      {
                        "type": "fido-uaf-registration",
                        "failure_count": 5
                      }
                    ]
                  },
                  "lock_conditions": {
                    "any_of": [
                      {
                        "type": "fido-uaf-registration",
                        "failure_count": 5
                      }
                    ]
                  },
                  "authentication_device_rule": {
                    "max_devices": 100,
                    "required_identity_verification": true
                  }
                }
                """;
    return JsonConverter.snakeCaseInstance().read(json, AuthenticationPolicy.class);
  }

  private AuthenticationPolicy createFidoUafDeregistrationPolicy() {
    String json =
        """
                    {
                      "id": "fido-uaf-registration",
                      "priority": 100,
                      "available_methods": [
                        "fido-uaf"
                      ],
                      "success_conditions": {
                        "any_of": [
                          {
                            "type": "fido-uaf-deregistration",
                            "success_count": 1
                          }
                        ]
                      },
                      "failure_conditions": {
                        "any_of": [
                          {
                            "type": "fido-uaf-deregistration",
                            "failure_count": 5
                          }
                        ]
                      },
                      "lock_conditions": {
                        "any_of": [
                          {
                            "type": "fido-uaf-deregistration",
                            "failure_count": 5
                          }
                        ]
                      }
                    }
                    """;
    return JsonConverter.snakeCaseInstance().read(json, AuthenticationPolicy.class);
  }
}
