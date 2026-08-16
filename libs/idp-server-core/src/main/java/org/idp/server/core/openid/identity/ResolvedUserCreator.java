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

package org.idp.server.core.openid.identity;

/**
 * Builds the user an external source resolved on a 1st factor, for the case where that person
 * already has a record here.
 *
 * <p><b>Issue #1792:</b> the resolved user is what the authorization grant snapshots, and the grant
 * is what the tokens are built from. Returning only what the mapping produced means an attribute
 * the external source did not restate this time — a {@code custom_properties} key another
 * authentication method wrote, {@code roles}, {@code verified_claims} — is missing from that
 * session's tokens even though the database and UserInfo still have it. {@link UserRegistrator}
 * already merged this way when persisting, but that runs after the grant is taken, so the merge
 * came too late for the token.
 *
 * <p>Laying the mapping output over the stored user also makes the identifiers unreachable from
 * configuration: {@link User#updateWith(User)} treats {@code sub} / {@code provider_id} / {@code
 * external_user_id} as immutable, so they come from the stored row no matter how the mapping rules
 * are written.
 *
 * <p><b>Why this is one function and not two lines at each call site:</b> four paths resolve a 1st
 * factor from an external source, and the {@code status} rule below is a security rule. Repeating
 * it is how a fifth path ends up without it — the external-token path was in fact missed on the
 * first pass of #1792.
 *
 * @see UserRegistrator the same merge, applied when the row is finally written
 */
public class ResolvedUserCreator {

  /**
   * Lays the mapping output over the stored user.
   *
   * <p>{@code status} stays the stored one. Whether an account is usable is this server's decision,
   * not something an external source may hand back: {@link User#updateWith(User)} otherwise takes
   * {@code status} from the patch whenever the patch has one, so a mapping rule writing {@code
   * status} could revive a {@code LOCKED} account.
   *
   * <p><b>Cost:</b> the stored user is read on the 1st factor but written back only once the
   * authorization succeeds, and by then the result carries a value for every field {@link
   * User#updateWith(User)} takes from the patch. A management-API update landing mid-authentication
   * is therefore reverted to the value read here — including {@code roles}, {@code
   * authentication_devices} and {@code assigned_tenants}, which the mapping itself never produces.
   * The window is one authentication flow and the next management write restores the value.
   *
   * @param existingUser the stored user, already looked up by the resolved key
   * @param mapped what {@code user_mapping_rules} produced for this authentication
   * @return the stored user with the mapping output applied
   */
  public static User create(User existingUser, User mapped) {
    User resolved = existingUser.enrichWith(mapped);
    resolved.setStatus(existingUser.status());
    return resolved;
  }
}
