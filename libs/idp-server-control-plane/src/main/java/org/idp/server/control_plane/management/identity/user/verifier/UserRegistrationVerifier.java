/**
 * Copyright 2025 Hirokazu Kobayashi
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 */
package org.idp.server.control_plane.management.identity.user.verifier;

import org.idp.server.control_plane.base.verifier.UserVerifier;
import org.idp.server.control_plane.base.verifier.VerificationResult;
import org.idp.server.control_plane.management.identity.user.UserRegistrationContext;

public class UserRegistrationVerifier {

  UserVerifier userVerifier;

  public UserRegistrationVerifier(UserVerifier userVerifier) {
    this.userVerifier = userVerifier;
  }

  public UserRegistrationVerificationResult verify(UserRegistrationContext context) {

    VerificationResult verificationResult = userVerifier.verify(context.tenant(), context.user());

    if (!verificationResult.isValid()) {
      return UserRegistrationVerificationResult.error(verificationResult, context.isDryRun());
    }

    return UserRegistrationVerificationResult.success(verificationResult, context.isDryRun());
  }
}
