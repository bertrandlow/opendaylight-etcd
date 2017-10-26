/*
 * Copyright (c) 2017 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.etcd.ds.impl;

import com.coreos.jetcd.KV;
import com.coreos.jetcd.Watch;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker.DataChangeScope;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataChangeListener;
import org.opendaylight.controller.md.sal.dom.api.DOMDataTreeChangeListener;
import org.opendaylight.controller.sal.core.spi.data.DOMStore;
import org.opendaylight.controller.sal.core.spi.data.DOMStoreReadTransaction;
import org.opendaylight.controller.sal.core.spi.data.DOMStoreReadWriteTransaction;
import org.opendaylight.controller.sal.core.spi.data.DOMStoreTransactionChain;
import org.opendaylight.controller.sal.core.spi.data.DOMStoreTreeChangePublisher;
import org.opendaylight.controller.sal.core.spi.data.DOMStoreWriteTransaction;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;

/**
 * ODL DOM Data Store implementation based on etcd.
 *
 * @author Michael Vorburger.ch
 */
public class EtcdDataStore implements DOMStore, DOMStoreTreeChangePublisher, AutoCloseable {
    // TODO implements SchemaContextListener, do we care? If yes, then un-comment
    // registerSchemaContextListener() in EtcdConcurrentDataBrokerTestCustomizer

    private final KV etcdKV;
    private final Watch etcdWatch;
    private final boolean debugTransactions;

    public EtcdDataStore(KV etcdKV, Watch etcdWatch, boolean debugTransactions) {
        super();
        this.etcdKV = etcdKV;
        this.etcdWatch = etcdWatch;
        this.debugTransactions = debugTransactions;
    }

    @Override
    public DOMStoreReadTransaction newReadOnlyTransaction() {
        return new EtcdReadTransaction(TransactionIdentifier.next(), debugTransactions);
    }

    @Override
    public DOMStoreWriteTransaction newWriteOnlyTransaction() {
        return new EtcdWriteTransaction(TransactionIdentifier.next(), debugTransactions);
    }

    @Override
    public DOMStoreReadWriteTransaction newReadWriteTransaction() {
        return new EtcdReadWriteTransaction(TransactionIdentifier.next(), debugTransactions);
    }

    @Override
    public DOMStoreTransactionChain createTransactionChain() {
        throw new UnsupportedOperationException("TODO");
    }

    @Override
    public <L extends AsyncDataChangeListener<YangInstanceIdentifier, NormalizedNode<?, ?>>>
        ListenerRegistration<L> registerChangeListener(YangInstanceIdentifier path, L listener, DataChangeScope scope) {

        throw new UnsupportedOperationException("TODO");
    }

    @Override
    public <L extends DOMDataTreeChangeListener>
        ListenerRegistration<L> registerTreeChangeListener(YangInstanceIdentifier treeId, L listener) {

        throw new UnsupportedOperationException("TODO");
        // TODO etcdWatch.watch(prefix, options); ...
    }

    @Override
    public void close() throws Exception {
        etcdKV.close();
        etcdWatch.close();
    }

}
