/*
 * Copyright (c) 2017 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.etcd.ds.impl;

import com.coreos.jetcd.Client;
import java.util.concurrent.ExecutorService;
import javax.annotation.concurrent.ThreadSafe;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.dom.store.impl.InMemoryDOMDataStore;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.tree.DataTreeCandidate;
import org.opendaylight.yangtools.yang.data.api.schema.tree.DataTreeCandidateNode;
import org.opendaylight.yangtools.yang.data.api.schema.tree.ModificationType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ODL DOM Data Store implementation based on etcd.
 *
 * @author Michael Vorburger.ch
 */
@ThreadSafe
public class EtcdDataStore extends InMemoryDOMDataStore {

    private static final Logger LOG = LoggerFactory.getLogger(EtcdDataStore.class);

    private static final byte CONFIGURATION_PREFIX = 67; // 'C'
    private static final byte OPERATIONAL_PREFIX   = 79; // 'O'

    private final Etcd etcd;

    public EtcdDataStore(LogicalDatastoreType type, ExecutorService dataChangeListenerExecutor,
            int maxDataChangeListenerQueueSize, Client client, boolean debugTransactions) {
        super(type.name(), type, dataChangeListenerExecutor, maxDataChangeListenerQueueSize, debugTransactions);

        byte prefix = type.equals(LogicalDatastoreType.CONFIGURATION) ? CONFIGURATION_PREFIX : OPERATIONAL_PREFIX;

        this.etcd = new Etcd(client, prefix);

        // TODO need to read back current persistent state from etcd on start-up...
    }

    @Override
    public void close() {
        etcd.close();
    }

    @Override
    // requires https://git.opendaylight.org/gerrit/#/c/73208/ :-( or figure out if we can hook into InMemoryDOMDataStore via a commit cohort?!
    protected synchronized void commit(DataTreeCandidate candidate) {
        print(candidate);
        // TODO transform DataTreeCandidate into etcd operations...
        super.commit(candidate);
    }

    private void print(DataTreeCandidate candidate) {
        if (!candidate.getRootPath().equals(YangInstanceIdentifier.EMPTY)) {
            LOG.error("DataTreeCandidate: YangInstanceIdentifier path={}", candidate.getRootPath());
            throw new IllegalArgumentException("I've not learnt how to deal with DataTreeCandidate where "
                    + "root path != YangInstanceIdentifier.EMPTY yet - will you teach me? ;)");
        }
        print("", candidate.getRootNode());
    }

    private void print(String indent, DataTreeCandidateNode node) {
        LOG.info("{}DataTreeCandidateNode: modificationType={}, PathArgument identifier={}",
                indent, node.getModificationType(), getIdentifierAsString(node));
        // LOG.info("{}  dataBefore= {}", indent, node.getDataBefore());
        LOG.info("{}  dataAfter = {}", indent, node.getDataAfter());
        // TODO filter and take more modificationType into account...
        if (node.getModificationType().equals(ModificationType.WRITE)) {
            // TODO must handle returned CompletionStage and make commit await that!
            etcd.put(node.getIdentifier(),
                    node.getDataAfter().orElseThrow(() -> new IllegalArgumentException("No dataAfter: " + node)));
        }
        for (DataTreeCandidateNode childNode : node.getChildNodes()) {
            print(indent + "    ", childNode);
        }
    }

    private String getIdentifierAsString(DataTreeCandidateNode node) {
        try {
            return node.getIdentifier().toString();
        } catch (IllegalStateException e) {
            // just debugging code; not intended for production
            return "-ROOT-";
        }
    }
}
