/*
 * Copyright (C) 2008 Google Inc.
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

package com.google.inject.internal;

import com.google.inject.Key;
import com.google.inject.spi.Dependency;
import com.google.inject.spi.PrivateElements;

/**
 * This factory exists in a parent injector. When invoked, it retrieves its value from a child
 * injector.
 */
final class ExposedKeyFactory<T> extends InternalFactory<T> implements CreationListener {
  private final Key<T> key;
  private final Object source;
  private final PrivateElements privateElements;
  private InternalFactory<T> delegate;

  ExposedKeyFactory(Key<T> key, Object source, PrivateElements privateElements) {
    this.key = key;
    this.source = source;
    this.privateElements = privateElements;
  }

  @Override
  public void notify(Errors errors) {
    InjectorImpl privateInjector = (InjectorImpl) privateElements.getInjector();
    BindingImpl<T> explicitBinding = privateInjector.getBindingData().getExplicitBinding(key);

    // validate that the child injector has its own factory. If the getInternalFactory() returns
    // this, then that child injector doesn't have a factory (and getExplicitBinding has returned
    // its parent's binding instead
    if (explicitBinding.getInternalFactory() == this) {
      errors.withSource(explicitBinding.getSource()).exposedButNotBound(key);
      return;
    }

    @SuppressWarnings("unchecked") // safe because InternalFactory<T> is covariant
    InternalFactory<T> delegate = (InternalFactory<T>) explicitBinding.getInternalFactory();
    this.delegate = delegate;
  }

  @Override
  public T get(InternalContext context, Dependency<?> dependency, boolean linked)
      throws InternalProvisionException {
    try {
      return delegate.get(context, dependency, linked);
    } catch (InternalProvisionException ipe) {
      throw ipe.addSource(source);
    }
  }

  @Override
  MethodHandleResult makeHandle(LinkageContext context, boolean linked) {
    return makeCachableOnLinkedSetting(
        InternalMethodHandles.catchInternalProvisionExceptionAndRethrowWithSource(
            this.delegate.getHandle(context, linked), source));
  }
}
