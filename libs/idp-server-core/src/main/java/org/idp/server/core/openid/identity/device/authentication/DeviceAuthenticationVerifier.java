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

package org.idp.server.core.openid.identity.device.authentication;

import org.idp.server.core.openid.identity.device.AuthenticationDevice;
import org.idp.server.core.openid.identity.device.credential.DeviceCredential;
import org.idp.server.core.openid.identity.device.credential.DeviceCredentialIdentifier;
import org.idp.server.core.openid.identity.device.credential.DeviceCredentialType;
import org.idp.server.core.openid.identity.device.credential.JwtBearerCredentialData;
import org.idp.server.platform.exception.UnauthorizedException;
import org.idp.server.platform.jose.JoseInvalidException;
import org.idp.server.platform.jose.JsonWebSignature;
import org.idp.server.platform.jose.JwtCredential;
import org.idp.server.platform.jose.JwtSignatureVerifier;
import org.idp.server.platform.multi_tenancy.tenant.policy.DeviceAuthenticationType;

/**
 * DeviceAuthenticationVerifier verifies device authentication using JWT assertions.
 *
 * <p>Supports both symmetric (device_secret_jwt) and asymmetric (private_key_jwt) authentication.
 *
 * <p>This class provides both high-level API (throwing UnauthorizedException) and low-level API
 * (throwing JoseInvalidException) for different use cases:
 *
 * <ul>
 *   <li>High-level: {@link #verify} - for API endpoint authentication
 *   <li>Low-level: {@link #verifySignature} - for JWT Bearer Grant and other token operations
 * </ul>
 */
public class DeviceAuthenticationVerifier {

  public DeviceAuthenticationVerifier() {}

  /**
   * Verifies device authentication using JWT assertion (high-level API).
   *
   * @param device the authentication device with integrated credential
   * @param assertion the JWT assertion
   * @param authenticationType the authentication type
   * @throws UnauthorizedException if verification fails
   */
  public void verify(
      AuthenticationDevice device, String assertion, DeviceAuthenticationType authenticationType) {

    try {
      JsonWebSignature jws = JsonWebSignature.parse(assertion);
      verifySignature(device, jws);
    } catch (JoseInvalidException e) {
      throw new UnauthorizedException("Invalid JWT assertion: " + e.getMessage());
    } catch (UnauthorizedException e) {
      throw e;
    } catch (Exception e) {
      throw new UnauthorizedException("Device authentication failed: " + e.getMessage());
    }
  }

  /**
   * Verifies device JWT signature (low-level API).
   *
   * <p>This method extracts the credential from the device and verifies the signature. Use this for
   * JWT Bearer Grant and other token operations where you need to control exception handling.
   *
   * @param device the authentication device with integrated credential
   * @param jws the parsed JSON Web Signature
   * @throws JoseInvalidException if signature verification fails or credential not found
   */
  public void verifySignature(AuthenticationDevice device, JsonWebSignature jws)
      throws JoseInvalidException {

    if (!device.hasCredentialType() || !device.hasCredentialPayload()) {
      throw new JoseInvalidException(
          String.format("No credential found for device '%s'", device.id()));
    }

    DeviceCredential credential = extractCredential(device);

    // Verify algorithm matches
    String jwtAlgorithm = jws.algorithm();
    JwtBearerCredentialData credentialData = credential.jwtBearerData();
    if (credentialData.hasAlgorithm() && !credentialData.algorithm().equals(jwtAlgorithm)) {
      throw new JoseInvalidException(
          String.format(
              "Algorithm mismatch: JWT uses '%s' but credential expects '%s'",
              jwtAlgorithm, credentialData.algorithm()));
    }

    verifyWithCredential(jws, credential);
  }

  /**
   * Extracts DeviceCredential from AuthenticationDevice's integrated credential fields.
   *
   * @param device the authentication device
   * @return the device credential
   */
  private DeviceCredential extractCredential(AuthenticationDevice device) {
    return new DeviceCredential(
        new DeviceCredentialIdentifier(device.credentialId()),
        DeviceCredentialType.valueOf(device.credentialType()),
        device.credentialPayload(),
        null,
        null,
        null);
  }

  /**
   * Verifies JWT signature using the provided credential.
   *
   * @param jws the JSON Web Signature
   * @param credential the device credential
   * @throws JoseInvalidException if verification fails
   */
  public void verifyWithCredential(JsonWebSignature jws, DeviceCredential credential)
      throws JoseInvalidException {

    if (credential.isSymmetric()) {
      verifySymmetric(jws, credential);
    } else if (credential.isAsymmetric()) {
      verifyAsymmetric(jws, credential);
    } else {
      throw new JoseInvalidException("Unsupported credential type: " + credential.type());
    }
  }

  private void verifySymmetric(JsonWebSignature jws, DeviceCredential credential)
      throws JoseInvalidException {
    JwtBearerCredentialData data = credential.jwtBearerData();
    if (!data.hasSecretValue()) {
      throw new JoseInvalidException(
          "Device credential does not have a secret value for symmetric signing");
    }

    JwtCredential jwtCredential = JwtCredential.symmetric(data.secretValue());
    JwtSignatureVerifier signatureVerifier = new JwtSignatureVerifier();
    signatureVerifier.verify(jws, jwtCredential);
  }

  private void verifyAsymmetric(JsonWebSignature jws, DeviceCredential credential)
      throws JoseInvalidException {
    JwtBearerCredentialData data = credential.jwtBearerData();
    if (!data.hasJwks()) {
      throw new JoseInvalidException("Device credential does not have JWKS for asymmetric signing");
    }

    JwtCredential jwtCredential = JwtCredential.asymmetric(data.jwks());
    JwtSignatureVerifier signatureVerifier = new JwtSignatureVerifier();
    signatureVerifier.verify(jws, jwtCredential);
  }
}
