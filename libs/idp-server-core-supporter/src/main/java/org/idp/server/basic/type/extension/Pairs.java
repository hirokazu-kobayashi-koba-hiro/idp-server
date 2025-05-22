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


package org.idp.server.basic.type.extension;

public class Pairs<L, R> {
  L left;
  R right;

  public Pairs(L left, R right) {
    this.left = left;
    this.right = right;
  }

  public static <L, R> Pairs<L, R> of(L left, R right) {
    return new Pairs<>(left, right);
  }

  public L getLeft() {
    return this.left;
  }

  public R getRight() {
    return this.right;
  }
}
