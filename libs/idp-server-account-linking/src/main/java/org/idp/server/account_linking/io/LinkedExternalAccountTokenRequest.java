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

package org.idp.server.account_linking.io;

import java.util.List;
import org.idp.server.account_linking.AccountAlias;
import org.idp.server.core.openid.oauth.clientattestation.ClientAttestationJwt;
import org.idp.server.core.openid.oauth.clientattestation.ClientAttestationPopJwt;
import org.idp.server.core.openid.oauth.type.mtls.ClientCert;
import org.idp.server.core.openid.oauth.type.oauth.ClientSecretBasic;
import org.idp.server.core.openid.oauth.type.oauth.RequestedClientId;
import org.idp.server.core.openid.token.AuthorizationHeaderHandlerable;
import org.idp.server.platform.http.BasicAuth;
import org.idp.server.platform.http.HttpRequestInputs;
import org.idp.server.platform.multi_tenancy.tenant.Tenant;

/**
 * A request for the stored access token of one linked external account.
 *
 * <p>The client is resolved the way introspection resolves it: Basic credentials first, then the
 * {@code client_id} parameter, then the subject of a Client Attestation. Whichever names it only
 * selects the configuration to load; the client authenticator still verifies the credential.
 */
public class LinkedExternalAccountTokenRequest implements AuthorizationHeaderHandlerable {

  Tenant tenant;
  AccountAlias alias;
  HttpRequestInputs inputs;

  public LinkedExternalAccountTokenRequest(
      Tenant tenant, AccountAlias alias, HttpRequestInputs inputs) {
    this.tenant = tenant;
    this.alias = alias;
    this.inputs = inputs;
  }

  public Tenant tenant() {
    return tenant;
  }

  public AccountAlias alias() {
    return alias;
  }

  public LinkedExternalAccountTokenParameters parameters() {
    return new LinkedExternalAccountTokenParameters(inputs.bodyParameters());
  }

  public RequestedClientId clientId() {
    String authorizationHeader = inputs.authorizationHeader();
    if (isBasicAuth(authorizationHeader)) {
      BasicAuth basicAuth = convertClientSecretBasicAuth(authorizationHeader);
      return new RequestedClientId(basicAuth.username());
    }
    LinkedExternalAccountTokenParameters parameters = parameters();
    if (parameters.hasClientId()) {
      return parameters.clientId();
    }
    ClientAttestationJwt clientAttestationJwt = clientAttestationJwt();
    if (clientAttestationJwt.exists()) {
      String subject = clientAttestationJwt.extractSubject();
      if (!subject.isEmpty()) {
        return new RequestedClientId(subject);
      }
    }
    return new RequestedClientId();
  }

  public ClientSecretBasic clientSecretBasic() {
    String authorizationHeader = inputs.authorizationHeader();
    if (isBasicAuth(authorizationHeader)) {
      return new ClientSecretBasic(convertClientSecretBasicAuth(authorizationHeader));
    }
    return new ClientSecretBasic();
  }

  public ClientCert clientCert() {
    return new ClientCert(inputs.tlsClientCertPem());
  }

  public ClientAttestationJwt clientAttestationJwt() {
    List<String> headers = inputs.headerValues(ClientAttestationJwt.HEADER_NAME);
    if (headers.isEmpty()) {
      return new ClientAttestationJwt();
    }
    return new ClientAttestationJwt(headers.get(0));
  }

  public ClientAttestationPopJwt clientAttestationPopJwt() {
    List<String> headers = inputs.headerValues(ClientAttestationPopJwt.HEADER_NAME);
    if (headers.isEmpty()) {
      return new ClientAttestationPopJwt();
    }
    return new ClientAttestationPopJwt(headers.get(0));
  }
}
