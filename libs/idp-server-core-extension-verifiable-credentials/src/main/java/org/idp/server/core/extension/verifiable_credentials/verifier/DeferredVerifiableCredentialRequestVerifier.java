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

package org.idp.server.core.extension.verifiable_credentials.verifier;

import org.idp.server.core.extension.verifiable_credentials.VerifiableCredentialTransaction;
import org.idp.server.core.extension.verifiable_credentials.exception.VerifiableCredentialBadRequestException;
import org.idp.server.core.extension.verifiable_credentials.request.DeferredCredentialRequestParameters;
import org.idp.server.core.extension.verifiable_credentials.request.VerifiableCredentialRequestTransformable;
import org.idp.server.core.openid.oauth.configuration.AuthorizationServerConfiguration;
import org.idp.server.core.openid.oauth.type.mtls.ClientCert;
import org.idp.server.core.openid.token.OAuthToken;

public class DeferredVerifiableCredentialRequestVerifier
    implements VerifiableCredentialRequestTransformable {

  OAuthToken oAuthToken;
  ClientCert clientCert;
  DeferredCredentialRequestParameters parameters;
  VerifiableCredentialTransaction transaction;
  AuthorizationServerConfiguration authorizationServerConfiguration;

  public DeferredVerifiableCredentialRequestVerifier(
      OAuthToken oAuthToken,
      ClientCert clientCert,
      DeferredCredentialRequestParameters parameters,
      VerifiableCredentialTransaction transaction,
      AuthorizationServerConfiguration authorizationServerConfiguration) {
    this.oAuthToken = oAuthToken;
    this.clientCert = clientCert;
    this.parameters = parameters;
    this.transaction = transaction;
    this.authorizationServerConfiguration = authorizationServerConfiguration;
  }

  public void verify() {
    throwExceptionIfUnSupportedVerifiableCredential();
    VerifiableCredentialOAuthTokenVerifier oAuthTokenVerifier =
        new VerifiableCredentialOAuthTokenVerifier(
            oAuthToken, clientCert, authorizationServerConfiguration);
    oAuthTokenVerifier.verify();
    throwExceptionIfNotContainsTransactionId();
    throwExceptionIfNotFoundTransaction();
  }

  void throwExceptionIfUnSupportedVerifiableCredential() {
    if (!authorizationServerConfiguration.hasCredentialIssuerMetadata()) {
      throw new VerifiableCredentialBadRequestException(
          "invalid_request", "unsupported verifiable credential");
    }
  }

  void throwExceptionIfNotContainsTransactionId() {
    if (!parameters.hasTransactionId()) {
      throw new VerifiableCredentialBadRequestException(
          "invalid_request",
          "transaction_id does not contains, deferred credential request must contain transaction_id");
    }
  }

  void throwExceptionIfNotFoundTransaction() {
    if (!transaction.exists()) {
      throw new VerifiableCredentialBadRequestException(
          "invalid_transaction_id",
          String.format(
              "not found verifiable credential transaction (%s)",
              parameters.transactionId().value()));
    }
  }
}
