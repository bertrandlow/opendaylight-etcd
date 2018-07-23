/*
 * Copyright (c) 2017 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.etcd.ds.impl;

import static java.util.concurrent.TimeUnit.SECONDS;

import com.coreos.jetcd.Client;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeoutException;
import javax.annotation.concurrent.ThreadSafe;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.mdsal.dom.store.inmemory.InMemoryDOMDataStore;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.tree.DataTreeCandidate;
import org.opendaylight.yangtools.yang.data.api.schema.tree.DataTreeCandidateNode;
import org.opendaylight.yangtools.yang.data.api.schema.tree.DataTreeModification;
import org.opendaylight.yangtools.yang.data.api.schema.tree.DataValidationFailedException;
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

    private final EtcdKV kv;
    private final EtcdWatcher watcher;

    public EtcdDataStore(LogicalDatastoreType type, ExecutorService dataChangeListenerExecutor,
            int maxDataChangeListenerQueueSize, Client client, boolean debugTransactions) {
        super(type.name(), dataChangeListenerExecutor, maxDataChangeListenerQueueSize, debugTransactions);

        byte prefix = type.equals(LogicalDatastoreType.CONFIGURATION) ? CONFIGURATION_PREFIX : OPERATIONAL_PREFIX;
        kv = new EtcdKV(client, prefix);

        watcher = new EtcdWatcher(client);
        watcher.watch(prefix, 0, watchEvent -> {
            // TODO actually update DataTree on watch notifications
            LOG.info("Watch: eventType={}, KV={}", watchEvent.getEventType(), watchEvent.getKeyValue());
        });
    }

    @Override
    public void close() {
        kv.close();
        watcher.close();
    }

    /**
     * On start-up, read back current persistent state from etcd as initial DataTree content.
     */
    // TODO make private; this is only temporarily public, for EtcdConcurrentDataBrokerTestCustomizer
    // The idea is to make this private again later, and have it called automatically whenever we're behind etcd
    public void initialLoad() throws EtcdException {
        // TODO requires https://git.opendaylight.org/gerrit/#/c/73482/ which makes dataTree protected instead of private
        DataTreeModification mod = dataTree.takeSnapshot().newModification();
        kv.readAllInto(mod);
        mod.ready();

        try {
            dataTree.validate(mod);
        } catch (DataValidationFailedException e) {
            throw new EtcdException("Initial load from etcd into (supposedly) empty data store caused "
                    + "unexpected DataValidationFailedException", e);
        }
        DataTreeCandidate candidate = dataTree.prepare(mod);
        dataTree.commit(candidate);

        // also requires https://git.opendaylight.org/gerrit/#/c/73482/ which adds a protected notifyListeners to InMemoryDOMDataStore
        notifyListeners(candidate);

        LOG.info("initialLoad: DataTreeModification={}, DataTreeCandidate={}", mod, candidate);
    }

    @Override
    // requires https://git.opendaylight.org/gerrit/#/c/73208/ :-( or figure out if we can hook into InMemoryDOMDataStore via a commit cohort?!
    protected synchronized void commit(DataTreeCandidate candidate) {
        if (!candidate.getRootPath().equals(YangInstanceIdentifier.EMPTY)) {
            LOG.error("DataTreeCandidate: YangInstanceIdentifier path={}", candidate.getRootPath());
            throw new IllegalArgumentException("I've not learnt how to deal with DataTreeCandidate where "
                    + "root path != YangInstanceIdentifier.EMPTY yet - will you teach me? ;)");
        }

        print("", candidate.getRootNode());

        // TODO make InMemoryDOMDataStore.commit(DataTreeCandidate) return ListenableFuture<Void> instead of void,
        // and then InMemoryDOMStoreThreePhaseCommitCohort.commit() return store.commit(candidate) instead of SUCCESS,
        // and then do this:
//        sendToEtcd(candidate.getRootNode()).thenRun(() -> super.commit(candidate)).exceptionally(throwable -> {
//            LOG.error("sendToEtcd failed", throwable);
//            return null;
//        });
        // but for now let's throw the entire nice async-ity over board and just do:
        try {
            sendToEtcd(candidate.getRootNode()).toCompletableFuture().get(1, SECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            // This is ugly, wrong, and just temporary
            throw new RuntimeException(e);
        }
        super.commit(candidate);
    }

    private CompletionStage<Void> sendToEtcd(DataTreeCandidateNode node) {
        List<CompletableFuture<Void>> futures = new ArrayList<>(1 + node.getChildNodes().size());

        // TODO filter and take more modificationType into account...
        if (node.getModificationType().equals(ModificationType.WRITE)) {
            add(futures, kv.put(node.getIdentifier(),
                    node.getDataAfter().orElseThrow(() -> new IllegalArgumentException("No dataAfter: " + node))));
        }

        for (DataTreeCandidateNode childNode : node.getChildNodes()) {
            add(futures, sendToEtcd(childNode));
        }

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }

    private void add(List<CompletableFuture<Void>> futures, CompletionStage<?> future) {
        futures.add(future.thenApply(whatever -> (Void)null).toCompletableFuture());
    }

    private void print(String indent, DataTreeCandidateNode node) {
        LOG.info("{}DataTreeCandidateNode: modificationType={}, PathArgument identifier={}",
                indent, node.getModificationType(), getIdentifierAsString(node));
        // LOG.info("{}  dataBefore= {}", indent, node.getDataBefore());
        LOG.info("{}  dataAfter = {}", indent, node.getDataAfter());

        for (DataTreeCandidateNode childNode : node.getChildNodes()) {
            print(indent + "    ", childNode);
        }
    }

    private static String getIdentifierAsString(DataTreeCandidateNode node) {
        try {
            return node.getIdentifier().toString();
        } catch (IllegalStateException e) {
            // just debugging code; not intended for production
            return "-ROOT-";
        }
    }
}
