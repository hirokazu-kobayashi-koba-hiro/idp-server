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

import java.util.List;

/**
 * {@link Rule} を List で受け取って順次評価する基底 verifier。
 *
 * <p>従来の {@code throwExceptionIfXxx()} メソッド爆発に対し、各検証を独立した {@link Rule} クラスとして 表現することで:
 *
 * <ul>
 *   <li>各 rule のユニットテストが verifier 全体のセットアップ無しで書ける
 *   <li>プロファイル間で rule を再利用可能 (例: FAPI 1.0 Adv で使ったものを FAPI 2.0 でも流用)
 *   <li>新仕様の検証追加は「Rule クラスを 1 個書いて List に足すだけ」
 * </ul>
 *
 * <p>例外型は各 Rule 実装に委ねる (本クラスは throw された例外をそのまま伝播)。
 *
 * @param <C> 検証コンテキストの型
 */
public abstract class RuleBasedVerifier<C> {

  /**
   * 評価する rule のリスト。順序通りに評価し、最初に違反した rule で停止する (fail-fast)。
   *
   * <p>サブクラスはこのメソッドで自分の rule セットを返す。
   */
  protected abstract List<Rule<C>> rules();

  /**
   * 全 rule を順次評価。{@link Rule#appliesTo(Object)} が false のルールはスキップ。
   *
   * @throws RuntimeException 最初に違反した rule が throw した例外
   */
  public void verifyRules(C context) {
    for (Rule<C> rule : rules()) {
      if (rule.appliesTo(context)) {
        rule.verify(context);
      }
    }
  }
}
