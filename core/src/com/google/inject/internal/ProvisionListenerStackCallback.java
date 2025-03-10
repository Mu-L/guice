/*
 * Copyright (C) 2011 Google Inc.
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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.inject.Binding;
import com.google.inject.spi.Dependency;
import com.google.inject.spi.ProvisionListener;
import java.util.List;
import java.util.Set;

/**
 * Intercepts provisions with a stack of listeners.
 *
 * @author sameb@google.com (Sam Berlin)
 */
final class ProvisionListenerStackCallback<T> {

  private static final ProvisionListener[] EMPTY_LISTENER = new ProvisionListener[0];

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static final ProvisionListenerStackCallback<?> EMPTY_CALLBACK =
      new ProvisionListenerStackCallback(null /* unused, so ok */, ImmutableList.of());

  private final ProvisionListener[] listeners;
  private final Binding<T> binding;

  @SuppressWarnings("unchecked")
  public static <T> ProvisionListenerStackCallback<T> emptyListener() {
    return (ProvisionListenerStackCallback<T>) EMPTY_CALLBACK;
  }

  public ProvisionListenerStackCallback(Binding<T> binding, List<ProvisionListener> listeners) {
    this.binding = binding;
    if (listeners.isEmpty()) {
      this.listeners = EMPTY_LISTENER;
    } else {
      Set<ProvisionListener> deDuplicated = Sets.newLinkedHashSet(listeners);
      this.listeners = deDuplicated.toArray(new ProvisionListener[deDuplicated.size()]);
    }
  }

  public boolean hasListeners() {
    return listeners.length > 0;
  }

  @CanIgnoreReturnValue
  public T provision(
      InternalContext context, Dependency<?> dependency, ProvisionCallback<T> callable)
      throws InternalProvisionException {
    Provision provision = new Provision(context, dependency, callable);
    RuntimeException caught = null;
    try {
      provision.provision();
    } catch (RuntimeException t) {
      caught = t;
    }

    if (provision.exceptionDuringProvision != null) {
      throw provision.exceptionDuringProvision;
    } else if (caught != null) {
      Object listener =
          provision.erredListener != null ? provision.erredListener.getClass() : "(unknown)";
      throw InternalProvisionException.errorInUserCode(
          ErrorId.OTHER,
          caught,
          "Error notifying ProvisionListener %s of %s.\n Reason: %s",
          listener,
          binding.getKey(),
          caught);
    } else {
      return provision.result;
    }
  }

  interface ProvisionCallback<T> {
    T call(InternalContext context, Dependency<?> dependency) throws InternalProvisionException;
  }

  private final class Provision extends ProvisionListener.ProvisionInvocation<T> {
    final ProvisionCallback<T> callable;
    final InternalContext context;
    final Dependency<?> dependency;
    int index = -1;
    T result;
    InternalProvisionException exceptionDuringProvision;
    ProvisionListener erredListener;

    Provision(InternalContext context, Dependency<?> dependency, ProvisionCallback<T> callable) {
      this.context = context;
      this.dependency = dependency;
      this.callable = callable;
    }

    @Override
    public T provision() {
      index++;
      if (index == listeners.length) {
        try {
          result = callable.call(context, dependency);
        } catch (InternalProvisionException ipe) {
          exceptionDuringProvision = ipe;
          throw ipe.toProvisionException();
        }
      } else if (index < listeners.length) {
        int currentIdx = index;
        try {
          listeners[index].onProvision(this);
        } catch (RuntimeException re) {
          erredListener = listeners[currentIdx];
          throw re;
        }
        if (currentIdx == index) {
          // Our listener didn't provision -- do it for them.
          provision();
        }
      } else {
        throw new IllegalStateException("Already provisioned in this listener.");
      }
      return result;
    }

    @Override
    public Binding<T> getBinding() {
      // TODO(sameb): Because so many places cast directly to BindingImpl & subclasses,
      // we can't decorate this to prevent calling getProvider().get(), which means
      // if someone calls that they'll get strange errors.
      return binding;
    }
  }
}
