/*
 * Copyright (c) 2018 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package ch.vorburger.dom2kv;

import java.util.Optional;

/**
 * Pair of Key &amp; Value.
 *
 * @author Michael Vorburger.ch
 */
public interface KeyValue<K, V> {

    K key();

    Optional<V> value();
}
