/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.core.extension.verifiable_credentials;

import org.idp.server.basic.type.verifiablecredential.CNonce;
import org.idp.server.basic.type.verifiablecredential.CNonceExpiresIn;
import org.idp.server.basic.type.verifiablecredential.Format;

public class VerifiableCredentialResponse {
  Format format;
  VerifiableCredential credentialJwt;
  CNonce cNonce;
  CNonceExpiresIn cNonceExpiresIn;
  String contents;

  public VerifiableCredentialResponse() {}

  VerifiableCredentialResponse(
      Format format,
      VerifiableCredential credentialJwt,
      CNonce cNonce,
      CNonceExpiresIn cNonceExpiresIn,
      String contents) {
    this.format = format;
    this.credentialJwt = credentialJwt;
    this.cNonce = cNonce;
    this.cNonceExpiresIn = cNonceExpiresIn;
    this.contents = contents;
  }

  public Format getFormat() {
    return format;
  }

  public VerifiableCredential credentialJwt() {
    return credentialJwt;
  }

  public CNonce cNonce() {
    return cNonce;
  }

  public CNonceExpiresIn cNonceExpiresIn() {
    return cNonceExpiresIn;
  }

  public String contents() {
    return contents;
  }
}
