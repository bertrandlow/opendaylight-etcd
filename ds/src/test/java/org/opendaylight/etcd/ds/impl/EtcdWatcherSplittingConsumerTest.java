/*
 * Copyright (c) 2018 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.etcd.ds.impl;

import static com.google.common.truth.Truth.assertThat;
import static org.opendaylight.etcd.ds.impl.EtcdDataStore.CONFIGURATION_PREFIX;
import static org.opendaylight.etcd.ds.impl.EtcdDataStore.OPERATIONAL_PREFIX;
import static org.opendaylight.etcd.utils.ByteSequences.fromBytes;

import com.coreos.jetcd.data.ByteSequence;
import com.coreos.jetcd.data.KeyValue;
import com.coreos.jetcd.watch.WatchEvent;
import com.coreos.jetcd.watch.WatchEvent.EventType;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import org.junit.Test;

/**
 * Unit Test for EtcdWatcherSplittingConsumer.
 *
 * @author Michael Vorburger.ch
 */
public class EtcdWatcherSplittingConsumerTest {

    private final TestConsumer configConsumer = new TestConsumer();
    private final TestConsumer operConsumer = new TestConsumer();

    private final Map<ByteSequence, Consumer<List<WatchEvent>>> consumers = ImmutableMap
            .of(fromBytes(CONFIGURATION_PREFIX), configConsumer, fromBytes(OPERATIONAL_PREFIX), operConsumer);

    @Test
    public void testEmpty() throws EtcdException {
        EtcdWatcherSplittingConsumer splitter = new EtcdWatcherSplittingConsumer(new RevAwaiter(), consumers);
        splitter.accept(1L, Collections.emptyList());
        assertThat(configConsumer.counter.get()).isEqualTo(0L);
        assertThat(operConsumer.counter.get()).isEqualTo(0L);
    }

    @Test
    public void testOnlyConfig() throws EtcdException {
        EtcdWatcherSplittingConsumer splitter = new EtcdWatcherSplittingConsumer(new RevAwaiter(), consumers);
        splitter.accept(1L, Lists.newArrayList(newWatchEvent(fromBytes(CONFIGURATION_PREFIX, (byte)123))));
        assertThat(configConsumer.counter.get()).isEqualTo(1L);
        assertThat(operConsumer.counter.get()).isEqualTo(0L);
    }

    @Test
    public void testOnlyOper() throws EtcdException {
        EtcdWatcherSplittingConsumer splitter = new EtcdWatcherSplittingConsumer(new RevAwaiter(), consumers);
        splitter.accept(1L, Lists.newArrayList(newWatchEvent(fromBytes(OPERATIONAL_PREFIX, (byte)123))));
        assertThat(configConsumer.counter.get()).isEqualTo(0L);
        assertThat(operConsumer.counter.get()).isEqualTo(1L);
    }

    @Test
    public void testOnlyConfigAndOper() throws EtcdException {
        EtcdWatcherSplittingConsumer splitter = new EtcdWatcherSplittingConsumer(new RevAwaiter(), consumers);
        splitter.accept(1L, Lists.newArrayList(
                newWatchEvent(fromBytes(CONFIGURATION_PREFIX, (byte) 123)),
                newWatchEvent(fromBytes(OPERATIONAL_PREFIX, (byte) 123))));
        assertThat(configConsumer.counter.get()).isEqualTo(1L);
        assertThat(operConsumer.counter.get()).isEqualTo(1L);
    }

    @Test
    public void testOnlyConfigAndOperAndAnotherOneToIgnore() throws EtcdException {
        EtcdWatcherSplittingConsumer splitter = new EtcdWatcherSplittingConsumer(new RevAwaiter(), consumers);
        splitter.accept(1L, Lists.newArrayList(
                newWatchEvent(fromBytes(CONFIGURATION_PREFIX, (byte) 123)),
                newWatchEvent(fromBytes((byte) 234, (byte) 123)),
                newWatchEvent(fromBytes(OPERATIONAL_PREFIX, (byte) 123))));
        assertThat(configConsumer.counter.get()).isEqualTo(1L);
        assertThat(operConsumer.counter.get()).isEqualTo(1L);
    }

    private static WatchEvent newWatchEvent(ByteSequence key) {
        return new WatchEvent(
                new KeyValue(com.coreos.jetcd.api.KeyValue.newBuilder().setKey(key.getByteString()).build()), null,
                EventType.PUT);
    }

    private static class TestConsumer implements Consumer<List<WatchEvent>> {
        AtomicLong counter = new AtomicLong();

        @Override
        public void accept(List<WatchEvent> event) {
            counter.accumulateAndGet(event.size(), (current, add) -> current + add);
        }
    }
}
