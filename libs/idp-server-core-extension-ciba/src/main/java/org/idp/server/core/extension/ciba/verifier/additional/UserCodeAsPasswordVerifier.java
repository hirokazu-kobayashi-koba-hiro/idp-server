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

package org.idp.server.core.extension.ciba.verifier.additional;

import org.idp.server.core.extension.ciba.CibaRequestContext;
import org.idp.server.core.extension.ciba.exception.BackchannelAuthenticationBadRequestException;
import org.idp.server.core.openid.identity.User;
import org.idp.server.core.openid.identity.authentication.PasswordVerificationDelegation;
import org.idp.server.core.openid.oauth.type.ciba.UserCode;

public class UserCodeAsPasswordVerifier implements CibaRequestAdditionalVerifier {

  PasswordVerificationDelegation passwordVerificationDelegation;

  public UserCodeAsPasswordVerifier(PasswordVerificationDelegation passwordVerificationDelegation) {
    this.passwordVerificationDelegation = passwordVerificationDelegation;
  }

  @Override
  public boolean shouldVerify(CibaRequestContext context, User user) {

    if (!context.isSupportedUserCode()) {
      return false;
    }
    if (!context.hasUserCode()) {
      return false;
    }

    return context.backchannelAuthUserCodeType().equals("password");
  }

  @Override
  public void verify(CibaRequestContext context, User user) {
    UserCode userCode = context.userCode();

    if (!passwordVerificationDelegation.verify(userCode.value(), user.hashedPassword())) {
      throw new BackchannelAuthenticationBadRequestException(
          "invalid_user_code", "The user code was invalid. Unmatch to user password.");
    }
  }
}
