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

package org.idp.server.platform.mapper.functions;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.Test;

public class KanaFunctionTest {

  private final KanaFunction function = new KanaFunction();

  @Test
  void hiraganaBecomesKatakanaByDefault() {
    assertEquals("ヤマダ タロウ", function.apply("やまだ たろう", Map.of()));
    assertEquals("ヤマダ タロウ", function.apply("やまだ たろう", null));
  }

  @Test
  void katakanaBecomesHiragana() {
    assertEquals("やまだ たろう", function.apply("ヤマダ タロウ", Map.of("to", "hiragana")));
  }

  @Test
  void voicedAndSmallLettersAndIterationMarksAreConverted() {
    assertEquals("ガギグゲゴ ァィゥェォ ッャュョ ヽヾ", function.apply("がぎぐげご ぁぃぅぇぉ っゃゅょ ゝゞ", Map.of()));
    assertEquals("ゔ ゕゖ", function.apply("ヴ ヵヶ", Map.of("to", "hiragana")));
  }

  @Test
  void prolongedSoundMarkAndOtherScriptsAreKept() {
    assertEquals("じょーんず", function.apply("ジョーンズ", Map.of("to", "hiragana")));
    assertEquals("山田 Taro ヤマダ", function.apply("山田 Taro やまだ", Map.of()));
  }

  @Test
  void halfwidthKatakanaIsLeftForNormalize() {
    assertEquals("ﾔﾏﾀﾞ", function.apply("ﾔﾏﾀﾞ", Map.of()));
  }

  @Test
  void katakanaWithoutHiraganaCounterpartIsKept() {
    // U+30F7..U+30FA have no hiragana letter.
    assertEquals("ヷヸヹヺ", function.apply("ヷヸヹヺ", Map.of("to", "hiragana")));
  }

  @Test
  void nullAndEmpty() {
    assertNull(function.apply(null, Map.of()));
    assertEquals("", function.apply("", Map.of()));
  }

  @Test
  void unknownTargetIsAnErrorWhateverTheInput() {
    for (Object input : new Object[] {"やまだ", "", null}) {
      assertThrows(
          IllegalArgumentException.class, () -> function.apply(input, Map.of("to", "romaji")));
    }
  }
}
