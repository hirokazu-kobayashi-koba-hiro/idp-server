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

package org.idp.server.core.extension.ciba;

import static org.idp.server.core.openid.oauth.type.OAuthRequestKey.*;

import java.util.List;
import java.util.Map;
import org.idp.server.core.openid.oauth.clientauthenticator.BackchannelRequestParameters;
import org.idp.server.core.openid.oauth.rar.AuthorizationDetails;
import org.idp.server.core.openid.oauth.type.ArrayValueMap;
import org.idp.server.core.openid.oauth.type.OAuthRequestKey;
import org.idp.server.core.openid.oauth.type.ciba.*;
import org.idp.server.core.openid.oauth.type.oauth.*;
import org.idp.server.core.openid.oauth.type.oidc.AcrValues;
import org.idp.server.core.openid.oauth.type.oidc.IdTokenHint;
import org.idp.server.core.openid.oauth.type.oidc.LoginHint;
import org.idp.server.core.openid.oauth.type.oidc.RequestObject;

/**
 * CIBA Authentication Request Parameters
 *
 * <p>Represents parameters for Client-Initiated Backchannel Authentication requests as defined in
 * OpenID Connect CIBA Core Section 7.1.
 *
 * <h2>CIBA Core Section 7.1: Authentication Request</h2>
 *
 * <p>Client-Initiated Backchannel Authentication defines an authentication request that is
 * requested directly from the Client to the OpenID Provider <strong>without going through the
 * user's browser</strong>. The Client MUST send an authentication request to the OpenID Provider by
 * building an HTTP POST request that will take to the OpenID Provider all the information needed to
 * authenticate the user without asking them for their identifier.
 *
 * <h3>Client Authentication Requirements</h3>
 *
 * <p>The Client MUST authenticate to the Backchannel Authentication Endpoint using the
 * authentication method registered for its {@code client_id}, such as:
 *
 * <ul>
 *   <li>{@code client_secret_post}, {@code client_secret_basic} (OpenID Core Section 9)
 *   <li>{@code private_key_jwt} (JWT-based client authentication)
 *   <li>{@code tls_client_auth}, {@code self_signed_tls_client_auth} (mTLS)
 * </ul>
 *
 * <p><strong>Audience Value Ambiguity Resolution</strong>: When JWT client assertion based
 * authentication is employed, the Issuer Identifier of the OP SHOULD be used as the value of the
 * audience. To facilitate interoperability, the OP MUST accept its Issuer Identifier, Token
 * Endpoint URL, or Backchannel Authentication Endpoint URL as values that identify it as an
 * intended audience.
 *
 * <h3>Required Parameters</h3>
 *
 * <ul>
 *   <li><strong>scope</strong> (REQUIRED): Must contain {@code openid} scope value. OpenID Connect
 *       implements authentication as an extension to OAuth 2.0 by including the openid scope value
 *       in the authorization requests.
 *   <li><strong>One hint parameter</strong> (REQUIRED): Must provide exactly one of {@code
 *       login_hint_token}, {@code id_token_hint}, or {@code login_hint} to identify the end-user.
 * </ul>
 *
 * <h3>Conditionally Required Parameters</h3>
 *
 * <ul>
 *   <li><strong>client_notification_token</strong> (REQUIRED for Ping/Push modes, NOT required for
 *       Poll mode): Bearer token used by the OP to authenticate callback requests to the Client's
 *       notification endpoint. Must not exceed 1024 characters and must contain sufficient entropy
 *       (minimum 128 bits, 160 bits recommended). In Poll mode, this parameter is not needed as the
 *       client retrieves results directly from the token endpoint without OP-initiated callbacks.
 * </ul>
 *
 * <h3>Optional Parameters</h3>
 *
 * <ul>
 *   <li><strong>acr_values</strong>: Space-separated Authentication Context Class Reference values
 *       in order of preference
 *   <li><strong>binding_message</strong>: Human-readable message displayed on both consumption and
 *       authentication devices to interlock them (e.g., transaction approval code)
 *   <li><strong>user_code</strong>: Secret code (password/pin) known to user and verifiable by OP
 *   <li><strong>requested_expiry</strong>: Positive integer requesting the {@code expires_in} value
 *       for the {@code auth_req_id}
 * </ul>
 *
 * <h3>Request Format</h3>
 *
 * <p>Authentication requests are made using HTTP POST with {@code
 * application/x-www-form-urlencoded} format and UTF-8 character encoding in the HTTP request
 * entity-body. When applicable, additional parameters required by the client authentication method
 * are included (e.g., {@code client_assertion} and {@code client_assertion_type} for JWT-based
 * authentication).
 *
 * <h3>Example Request</h3>
 *
 * <pre>{@code
 * POST /bc-authorize HTTP/1.1
 * Host: server.example.com
 * Content-Type: application/x-www-form-urlencoded
 *
 * scope=openid%20email%20example-scope&
 * client_notification_token=8d67dc78-7faa-4d41-aabd-67707b374255&
 * binding_message=W4SCT&
 * login_hint_token=eyJraWQiOiJsdGFjZXNidyIsImFsZyI6IkVTMjU2In0.ey...&
 * client_assertion_type=urn%3Aietf%3Aparams%3Aoauth%3Aclient-assertion-type%3Ajwt-bearer&
 * client_assertion=eyJraWQiOiJsdGFjZXNidyIsImFsZyI6IkVTMjU2In0.eyJ...
 * }</pre>
 *
 * <h3>Request Object Pattern (FAPI CIBA)</h3>
 *
 * <p>FAPI CIBA Profile requires signed request objects passed via the {@code request} parameter.
 * All authentication parameters must be included in the signed JWT, not as HTTP request parameters.
 *
 * <pre>{@code
 * POST /bc-authorize HTTP/1.1
 * Host: server.example.com
 * Content-Type: application/x-www-form-urlencoded
 *
 * request=eyJhbGciOiJQUzI1NiIsInR5cCI6IkpXVCIsImtpZCI6InNlbGZfcmVxdWVzdF9rZXlfcHMyNTYifQ...&
 * client_id=fapi-ciba-client
 * }</pre>
 *
 * <p><strong>Note</strong>: CIBA does not support {@code request_uri} parameter (request by
 * reference). Only {@code request} parameter (request by value) is supported.
 *
 * @see <a
 *     href="https://openid.net/specs/openid-client-initiated-backchannel-authentication-core-1_0.html#rfc.section.7.1">CIBA
 *     Core Section 7.1</a>
 * @see <a href="https://openid.net/specs/openid-financial-api-ciba.html">FAPI CIBA Profile</a>
 */
public class CibaRequestParameters implements BackchannelRequestParameters {
  ArrayValueMap values;

  public CibaRequestParameters() {
    this.values = new ArrayValueMap();
  }

  public CibaRequestParameters(ArrayValueMap values) {
    this.values = values;
  }

  public CibaRequestParameters(Map<String, String[]> values) {
    this.values = new ArrayValueMap(values);
  }

  public boolean isEmpty() {
    return values.isEmpty();
  }

  /**
   * Returns the scope parameter value.
   *
   * <p><strong>REQUIRED</strong>. The scope of the access request as described by Section 3.3 of
   * RFC 6749. OpenID Connect implements authentication as an extension to OAuth 2.0 by including
   * the {@code openid} scope value in the authorization requests. CIBA authentication requests MUST
   * therefore contain the {@code openid} scope value.
   *
   * <p>Other scope values MAY be present, including but not limited to the {@code profile}, {@code
   * email}, {@code address}, and {@code phone} scope values from Section 5.4 of OpenID Core.
   *
   * @return the scope parameter value
   * @see <a href="https://datatracker.ietf.org/doc/html/rfc6749#section-3.3">RFC 6749 Section
   *     3.3</a>
   */
  public Scopes scope() {
    return new Scopes(getValueOrEmpty(scope));
  }

  public boolean hasScope() {
    return contains(scope);
  }

  /**
   * Returns the client_notification_token parameter value.
   *
   * <p><strong>REQUIRED if the Client is registered to use Ping or Push modes</strong>. NOT
   * required for Poll mode.
   *
   * <p>It is a bearer token provided by the Client that will be used by the OpenID Provider to
   * authenticate the callback request to the Client. The length of the token MUST NOT exceed 1024
   * characters and it MUST conform to the syntax for Bearer credentials as defined in Section 2.1
   * of RFC 6750.
   *
   * <p>Clients MUST ensure that it contains sufficient entropy (a minimum of 128 bits while 160
   * bits is recommended) to make brute force guessing or forgery of a valid token computationally
   * infeasible.
   *
   * @return the client_notification_token parameter value
   * @see <a href="https://datatracker.ietf.org/doc/html/rfc6750#section-2.1">RFC 6750 Section
   *     2.1</a>
   */
  public ClientNotificationToken clientNotificationToken() {
    return new ClientNotificationToken(getValueOrEmpty(client_notification_token));
  }

  public boolean hasClientNotificationToken() {
    return contains(client_notification_token);
  }

  /**
   * Returns the user_code parameter value.
   *
   * <p><strong>OPTIONAL</strong>. A secret code, such as a password or pin, that is known only to
   * the user but verifiable by the OP. The code is used to authorize sending an authentication
   * request to the user's authentication device.
   *
   * <p>This parameter should only be present if the client registration parameter {@code
   * backchannel_user_code_parameter} indicates support for the user code.
   *
   * @return the user_code parameter value
   */
  public UserCode userCode() {
    return new UserCode(getValueOrEmpty(user_code));
  }

  public boolean hasUserCode() {
    return contains(user_code);
  }

  /**
   * Returns the binding_message parameter value.
   *
   * <p><strong>OPTIONAL</strong>. A human-readable identifier or message intended to be displayed
   * on both the consumption device and the authentication device to interlock them together for the
   * transaction by way of a visual cue for the end-user.
   *
   * <p>This interlocking message enables the end-user to ensure that the action taken on the
   * authentication device is related to the request initiated by the consumption device. The value
   * SHOULD contain something that enables the end-user to reliably discern that the transaction is
   * related across the consumption device and the authentication device, such as a random value of
   * reasonable entropy (e.g. a transactional approval code).
   *
   * <p>Because the various devices involved may have limited display abilities and the message is
   * intending for visual inspection by the end-user, the binding_message value SHOULD be relatively
   * short and use a limited set of plain text characters.
   *
   * @return the binding_message parameter value
   */
  public BindingMessage bindingMessage() {
    return new BindingMessage(getValueOrEmpty(binding_message));
  }

  public boolean hasBindingMessage() {
    return contains(binding_message);
  }

  /**
   * Returns the login_hint_token parameter value.
   *
   * <p><strong>OPTIONAL</strong> (but one hint parameter is REQUIRED). A token containing
   * information identifying the end-user for whom authentication is being requested.
   *
   * <p>The particular details and security requirements for the login_hint_token as well as how the
   * end-user is identified by its content are deployment or profile specific.
   *
   * <p><strong>Note</strong>: The Client MUST provide exactly one of {@code login_hint_token},
   * {@code id_token_hint}, or {@code login_hint} in the authentication request.
   *
   * @return the login_hint_token parameter value
   */
  public LoginHintToken loginHintToken() {
    return new LoginHintToken(getValueOrEmpty(login_hint_token));
  }

  public boolean hasLoginHintToken() {
    return contains(login_hint_token);
  }

  /**
   * Returns the requested_expiry parameter value.
   *
   * <p><strong>OPTIONAL</strong>. A positive integer allowing the client to request the {@code
   * expires_in} value for the {@code auth_req_id} the server will return.
   *
   * <p>The server MAY use this value to influence the lifetime of the authentication request and is
   * encouraged to do so where it will improve the user experience, for example by terminating the
   * authentication when it knows the client is no longer interested in the result.
   *
   * @return the requested_expiry parameter value
   */
  public RequestedExpiry requestedExpiry() {
    return new RequestedExpiry(getValueOrEmpty(requested_expiry));
  }

  public boolean hasRequestedExpiry() {
    return contains(requested_expiry);
  }

  public RequestedClientId clientId() {
    return new RequestedClientId(getValueOrEmpty(client_id));
  }

  public boolean hasClientId() {
    return contains(client_id);
  }

  /**
   * Returns the id_token_hint parameter value.
   *
   * <p><strong>OPTIONAL</strong> (but one hint parameter is REQUIRED). An ID Token previously
   * issued to the Client by the OpenID Provider being passed back as a hint to identify the
   * end-user for whom authentication is being requested.
   *
   * <p>If the ID Token received by the Client from the OP was asymmetrically encrypted, to use it
   * as an id_token_hint, the client MUST decrypt the encrypted ID Token to extract the signed ID
   * Token contained in it.
   *
   * <p><strong>Note</strong>: The Client MUST provide exactly one of {@code login_hint_token},
   * {@code id_token_hint}, or {@code login_hint} in the authentication request.
   *
   * @return the id_token_hint parameter value
   */
  public IdTokenHint idTokenHint() {
    return new IdTokenHint(getValueOrEmpty(id_token_hint));
  }

  public boolean hasIdTokenHint() {
    return contains(id_token_hint);
  }

  /**
   * Returns the login_hint parameter value.
   *
   * <p><strong>OPTIONAL</strong> (but one hint parameter is REQUIRED). A hint to the OpenID
   * Provider regarding the end-user for whom authentication is being requested.
   *
   * <p>The value may contain an email address, phone number, account number, subject identifier,
   * username, etc., which identifies the end-user to the OP. The value may be directly collected
   * from the user by the Client before requesting authentication at the OP, for example, but may
   * also be obtained by other means.
   *
   * <p><strong>Note</strong>: The Client MUST provide exactly one of {@code login_hint_token},
   * {@code id_token_hint}, or {@code login_hint} in the authentication request.
   *
   * @return the login_hint parameter value
   */
  public LoginHint loginHint() {
    return new LoginHint(getValueOrEmpty(login_hint));
  }

  public boolean hasLoginHint() {
    return contains(login_hint);
  }

  /**
   * Returns the acr_values parameter value.
   *
   * <p><strong>OPTIONAL</strong>. Requested Authentication Context Class Reference values. A
   * space-separated string that specifies the acr values that the OpenID Provider is being
   * requested to use for processing this Authentication Request, with the values appearing in order
   * of preference.
   *
   * <p>The actual means of authenticating the end-user, however, are ultimately at the discretion
   * of the OP, and the Authentication Context Class satisfied by the authentication performed is
   * returned as the {@code acr} Claim Value of the ID Token.
   *
   * <p>When the acr_values parameter is present in the authentication request, it is highly
   * RECOMMENDED that the resulting ID Token contains an {@code acr} Claim.
   *
   * @return the acr_values parameter value
   */
  public AcrValues acrValues() {
    return new AcrValues(getValueOrEmpty(acr_values));
  }

  public boolean hasAcrValues() {
    return contains(acr_values);
  }

  /**
   * Returns the request parameter value (signed request object).
   *
   * <p><strong>OPTIONAL</strong> in standard CIBA, <strong>REQUIRED</strong> in FAPI CIBA Profile.
   *
   * <p>A signed JWT containing all authentication request parameters. When present, all
   * authentication parameters MUST be included in the JWT claims, not as HTTP request parameters.
   *
   * <p><strong>FAPI CIBA Requirements</strong>:
   *
   * <ul>
   *   <li>Signing algorithm: PS256 or ES256 only
   *   <li>Key size: RSA ≥ 2048 bits, EC ≥ 256 bits
   *   <li>Request object lifetime: exp - nbf ≤ 60 minutes
   *   <li>nbf claim: no longer than 60 minutes in the past
   *   <li>aud claim: must contain OP's Issuer Identifier URL
   * </ul>
   *
   * <p><strong>Note</strong>: CIBA does not support {@code request_uri} parameter (request by
   * reference). Only {@code request} parameter (request by value) is supported.
   *
   * @return the request parameter value
   * @see <a href="https://openid.net/specs/openid-financial-api-ciba.html">FAPI CIBA Profile</a>
   */
  public RequestObject request() {
    return new RequestObject(getValueOrEmpty(request));
  }

  public boolean hasRequest() {
    return contains(request);
  }

  @Override
  public ClientSecret clientSecret() {
    return new ClientSecret(getValueOrEmpty(client_secret));
  }

  @Override
  public boolean hasClientSecret() {
    return contains(client_secret);
  }

  @Override
  public ClientAssertion clientAssertion() {
    return new ClientAssertion(getValueOrEmpty(client_assertion));
  }

  @Override
  public boolean hasClientAssertion() {
    return contains(client_assertion);
  }

  @Override
  public ClientAssertionType clientAssertionType() {
    return ClientAssertionType.of(getValueOrEmpty(client_assertion_type));
  }

  @Override
  public boolean hasClientAssertionType() {
    return contains(client_assertion_type);
  }

  public String getValueOrEmpty(OAuthRequestKey key) {
    return values.getFirstOrEmpty(key.name());
  }

  boolean contains(OAuthRequestKey key) {
    return values.contains(key.name());
  }

  public List<String> multiValueKeys() {
    return values.multiValueKeys();
  }

  public Map<String, String> singleValues() {
    return values.singleValueMap();
  }

  public CibaRequestPattern analyze() {
    if (hasRequest()) {
      return CibaRequestPattern.REQUEST_OBJECT;
    }
    return CibaRequestPattern.NORMAL;
  }

  public AuthorizationDetails authorizationDetails() {
    return AuthorizationDetails.fromString(getValueOrEmpty(authorization_details));
  }

  public boolean hasAuthorizationDetails() {
    return contains(authorization_details);
  }
}
