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

package org.idp.server.core.openid.oauth.configuration.vc;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.idp.server.core.openid.oauth.type.verifiablecredential.CredentialIssuer;
import org.idp.server.platform.json.JsonReadable;

/**
 *
 *
 * <pre>
 * {
 *     "format": "jwt_vc_json",
 *     "id": "UniversityDegree_JWT",
 *     "cryptographic_binding_methods_supported": [
 *         "did:example"
 *     ],
 *     "cryptographic_suites_supported": [
 *         "ES256K"
 *     ],
 *     "credential_definition":{
 *         "type": [
 *             "VerifiableCredential",
 *             "UniversityDegreeCredential"
 *         ],
 *         "credentialSubject": {
 *             "given_name": {
 *                 "display": [
 *                     {
 *                         "name": "Given Name",
 *                         "locale": "en-US"
 *                     }
 *                 ]
 *             },
 *             "last_name": {
 *                 "display": [
 *                     {
 *                         "name": "Surname",
 *                         "locale": "en-US"
 *                     }
 *                 ]
 *             },
 *             "degree": {},
 *             "gpa": {
 *                 "display": [
 *                     {
 *                         "name": "GPA"
 *                     }
 *                 ]
 *             }
 *         }
 *     },
 *     "proof_types_supported": [
 *         "jwt"
 *     ],
 *     "display": [
 *         {
 *             "name": "University Credential",
 *             "locale": "en-US",
 *             "logo": {
 *                 "url": "https://exampleuniversity.com/public/logo.png",
 *                 "alt_text": "a square logo of a university"
 *             },
 *             "background_color": "#12107c",
 *             "text_color": "#FFFFFF"
 *         }
 *     ]
 * }
 * </pre>
 */
public class VerifiableCredentialConfiguration implements JsonReadable {
  String credentialIssuer;
  String authorizationServer = "";
  String credentialEndpoint;
  String batchCredentialEndpoint = "";
  String deferredCredentialEndpoint = "";
  List<VerifiableCredentialsSupportConfiguration> credentialsSupported = new ArrayList<>();

  public CredentialIssuer credentialIssuer() {
    return new CredentialIssuer(credentialIssuer);
  }

  public String authorizationServer() {
    return authorizationServer;
  }

  public String credentialEndpoint() {
    return credentialEndpoint;
  }

  public String batchCredentialEndpoint() {
    return batchCredentialEndpoint;
  }

  public String deferredCredentialEndpoint() {
    return deferredCredentialEndpoint;
  }

  public List<VerifiableCredentialsSupportConfiguration> credentialsSupported() {
    return credentialsSupported;
  }

  public boolean isSupportedFormat(String format) {
    List<String> formats =
        credentialsSupported.stream()
            .map(VerifiableCredentialsSupportConfiguration::format)
            .toList();
    return formats.contains(format);
  }

  public boolean exists() {
    return Objects.nonNull(credentialIssuer) && !credentialIssuer.isEmpty();
  }
}
