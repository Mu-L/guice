/*
 * Copyright (C) 2020 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.inject.internal.aop;

/** Defines dynamically generated classes. */
interface ClassDefiner {

  /** Defines a new class relative to the host. */
  Class<?> define(Class<?> hostClass, byte[] bytecode) throws Exception;

  /**
   * Defines a new class relative to the host. This class should be able to be independently
   * collected and need not be accessible by name.
   *
   * @param lifetimeOwner The object to whose lifetime the new class should be tied.
   */
  Class<?> defineCollectable(Object lifetimeOwner, Class<?> hostClass, byte[] bytecode)
      throws Exception;
}
