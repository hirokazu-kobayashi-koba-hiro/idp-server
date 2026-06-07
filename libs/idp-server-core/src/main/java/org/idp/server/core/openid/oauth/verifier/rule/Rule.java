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

package org.idp.server.core.openid.oauth.verifier.rule;

/**
 * 検証ルールの最小単位。
 *
 * <p>OAuth/OIDC/FAPI 仕様に沿った検証ロジックを 1 ルール = 1 クラスで表現するための抽象。 各 {@code throwExceptionIfXxx}
 * メソッドの肥大化を抑え、独立してユニットテスト可能な粒度に分解するための土台となる。
 *
 * <p>ルールの典型構造:
 *
 * <ol>
 *   <li>{@link #appliesTo(Object)} で適用条件を判定 (省略時は常に true)
 *   <li>{@link #verify(Object)} で実際の検査を実行し、違反時は実行時例外を throw
 * </ol>
 *
 * <p>例外型は context (=エンドポイント) に応じて使い分ける:
 *
 * <ul>
 *   <li>Authorization endpoint / PAR 系 → {@code OAuthRedirectableBadRequestException} or {@code
 *       OAuthBadRequestException}
 *   <li>Token endpoint 系 → {@code TokenBadRequestException}
 *   <li>その他 → 個別の Protocol 例外
 * </ul>
 *
 * <p>本インターフェースは exception type を generics で固定せず、各 rule が context に応じた最も適切な型を投げる方針とする。
 *
 * @param <C> 検証コンテキストの型 (例: {@code OAuthRequestContext}, {@code TokenRequestContext} など)
 */
public interface Rule<C> {

  /**
   * このルールの安定識別子。ログ / メトリクス / テストで参照する。
   *
   * <p>慣習: {@code "<profile>.<endpoint>.<concern>"} (例: {@code "fapi2.par.response-type-code"})
   */
  RuleId id();

  /**
   * 適用条件。{@code false} を返した場合 {@link #verify(Object)} は呼ばれない。
   *
   * <p>例: ある rule が private_key_jwt の時だけ走る、特定スコープ要求時のみ走る、等の動的 ON/OFF を表現する。
   *
   * <p>デフォルトは常に適用 (true)。
   */
  default boolean appliesTo(C context) {
    return true;
  }

  /**
   * 検証本体。違反があれば例外を throw する。
   *
   * @throws RuntimeException 各 rule が context に応じて最も適切な protocol 例外を throw する
   */
  void verify(C context);
}
