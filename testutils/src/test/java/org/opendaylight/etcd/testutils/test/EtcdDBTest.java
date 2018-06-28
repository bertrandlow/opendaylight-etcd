/*
 * Copyright (c) 2018 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.etcd.testutils.test;

import static com.google.common.truth.Truth.assertThat;
import static org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType.CONFIGURATION;
import static org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType.OPERATIONAL;
import static org.opendaylight.controller.md.sal.test.model.util.ListsBindingUtils.TOP_FOO_KEY;
import static org.opendaylight.controller.md.sal.test.model.util.ListsBindingUtils.path;
import static org.opendaylight.controller.md.sal.test.model.util.ListsBindingUtils.topLevelList;

import ch.vorburger.exec.ManagedProcessException;
import com.coreos.jetcd.Client;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Comparator;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.etcd.launcher.EtcdLauncher;
import org.opendaylight.etcd.testutils.TestEtcdDataBrokersProvider;
import org.opendaylight.infrautils.testutils.LogRule;
import org.opendaylight.yang.gen.v1.urn.opendaylight.etcd.test.rev180628.HelloWorldContainer;
import org.opendaylight.yang.gen.v1.urn.opendaylight.etcd.test.rev180628.HelloWorldContainerBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.controller.md.sal.test.augment.rev140709.TreeComplexUsesAugment;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.controller.md.sal.test.augment.rev140709.TreeComplexUsesAugmentBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.controller.md.sal.test.augment.rev140709.complex.from.grouping.ContainerWithUsesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.controller.md.sal.test.list.rev140701.Top;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.controller.md.sal.test.list.rev140701.TopBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.controller.md.sal.test.list.rev140701.two.level.list.TopLevelList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.controller.md.sal.test.list.rev140701.two.level.list.TopLevelListBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.controller.md.sal.test.list.rev140701.two.level.list.TopLevelListKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.controller.md.sal.test.list.rev140701.two.level.list.top.level.list.NestedList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.controller.md.sal.test.list.rev140701.two.level.list.top.level.list.NestedListBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.controller.md.sal.test.list.rev140701.two.level.list.top.level.list.NestedListKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tests the etcd-based DataBroker.
 *
 * @author Michael Vorburger.ch
 */
public class EtcdDBTest {

    private static final Logger LOG = LoggerFactory.getLogger(EtcdDBTest.class);

    private static final InstanceIdentifier<Top> TOP_PATH = InstanceIdentifier.create(Top.class);

    private static EtcdLauncher etcdServer;
    private static Client client;

    private DataBroker dataBroker;

    public @Rule LogRule logRule = new LogRule();

    @BeforeClass
    public static void beforeClass() throws ManagedProcessException, IOException {
        // TODO delete content of etcd tree before() not beforeClass() instead of this
        // TODO work clean up (and custom data dir) into EtcdLauncher
        deleteDirectory(Paths.get("target/etcd"));
        etcdServer = new EtcdLauncher();
        etcdServer.start();
    }

    @Before
    public void before() throws Exception {
        client = Client.builder().endpoints(etcdServer.getEndpointURL()).build();
        recreateFreshDataBrokerClient();
    }

    private void recreateFreshDataBrokerClient() throws Exception {
        LOG.info("recreateFreshDataBrokerClient()");
        dataBroker = new TestEtcdDataBrokersProvider(client).getDataBroker();
    }

    @After
    public void after() throws ManagedProcessException {
        dataBroker = null;
        client.close();
        client = null;
    }

    @AfterClass
    public static void afterClass() throws ManagedProcessException {
        etcdServer.close();
        etcdServer = null;
    }

    @Test
    public void testDataBrokerIsNotNull() {
        assertThat(dataBroker).isNotNull();
    }

    @Test
    public void testSimpleTestModelIntoDataStoreReadItBackAndDelete() throws Exception {
        InstanceIdentifier<HelloWorldContainer> iid = InstanceIdentifier.create(HelloWorldContainer.class);
        WriteTransaction initialTx = dataBroker.newWriteOnlyTransaction();
        initialTx.put(OPERATIONAL, iid, new HelloWorldContainerBuilder().setName("hello, world").build());
        initialTx.commit().get();

        recreateFreshDataBrokerClient();

        try (ReadOnlyTransaction readTx = dataBroker.newReadOnlyTransaction()) {
            assertThat(readTx.read(OPERATIONAL, iid).get().get().getName()).isEqualTo("hello, world");
        }

        // TODO delete
    }

    // as in org.opendaylight.controller.md.sal.binding.test.tests.AbstractDataBrokerTestTest

    @Test
    public void testPutSomethingMoreComplexIntoDataStoreReadItBackAndDelete() throws Exception {
        writeInitialState();
        recreateFreshDataBrokerClient();

        assertThat(isTopInDataStore()).isTrue();
        assertThat(isTopInDataStore(CONFIGURATION)).isFalse();
        recreateFreshDataBrokerClient();

        // TODO modify what we just wrote and read back to make sure value changed in etcd

        // TODO write Top to Oper instead Config, delete Config's, ensure it's gone but Oper's still there

        deleteTop();
        assertThat(isTopInDataStore()).isFalse();
    }

    @Test
    public void testPutSomethingMoreComplexForSubTreeIntoDSReadItBackAndDelete() throws Exception {
        NestedList nl1 = new NestedListBuilder().withKey(new NestedListKey("nested1"))
                .setName("nested1").setType("type1").build();
        TopLevelList tl1 = new TopLevelListBuilder().withKey(new TopLevelListKey("top1"))
                .setName("top1").setNestedList(Arrays.asList(nl1)).build();
        WriteTransaction writeTx = dataBroker.newWriteOnlyTransaction();
        writeTx.put(OPERATIONAL, TOP_PATH, new TopBuilder().setTopLevelList(Arrays.asList(tl1)).build());
        writeTx.submit().get();

        try (ReadOnlyTransaction readTx = dataBroker.newReadOnlyTransaction()) {
            assertThat(readTx.read(OPERATIONAL, path(new TopLevelListKey("top1"))).get().isPresent()).isTrue();
        }

        deleteTop();
        try (ReadOnlyTransaction readTx = dataBroker.newReadOnlyTransaction()) {
            assertThat(readTx.read(OPERATIONAL, path(new TopLevelListKey("top1"))).get().isPresent()).isFalse();
        }
    }

    @Test
    @Ignore // TODO think about how to best completely clear out external etcd between tests..
    public void testDataStoreIsEmptyInNewTest() throws ReadFailedException {
        assertThat(isTopInDataStore()).isFalse();
    }

    private void deleteTop() throws Exception {
        LOG.info("deleteTop()");
        WriteTransaction deleteTx = dataBroker.newWriteOnlyTransaction();
        deleteTx.delete(OPERATIONAL, TOP_PATH);
        deleteTx.commit().get();
    }

    private void writeInitialState() throws Exception {
        LOG.info("writeInitialState()");
        WriteTransaction initialTx = dataBroker.newWriteOnlyTransaction();
        initialTx.put(OPERATIONAL, TOP_PATH, new TopBuilder().build());

        // TODO make the test actually care about (verify to assert it's there), and implement mapping...
        TreeComplexUsesAugment fooAugment = new TreeComplexUsesAugmentBuilder()
                .setContainerWithUses(new ContainerWithUsesBuilder().setLeafFromGrouping("foo").build()).build();
        initialTx.put(OPERATIONAL, path(TOP_FOO_KEY), topLevelList(TOP_FOO_KEY, fooAugment));

        initialTx.submit().get();
    }

    private boolean isTopInDataStore() throws ReadFailedException {
        return isTopInDataStore(OPERATIONAL);
    }

    private boolean isTopInDataStore(LogicalDatastoreType type) throws ReadFailedException {
        try (ReadOnlyTransaction readTx = dataBroker.newReadOnlyTransaction()) {
            return readTx.read(type, TOP_PATH).checkedGet().isPresent();
        }
    }

    @SuppressWarnings("checkstyle:AvoidHidingCauseException")
    private static void deleteDirectory(Path directory) throws IOException {
        if (!directory.toFile().exists()) {
            LOG.info("Directory to delete did not exist: {}", directory);
            return;
        }
        try {
            Files.walk(directory).sorted(Comparator.reverseOrder()).forEach(t -> {
                try {
                    Files.delete(t);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
            LOG.info("Successfully deleted directory: {}", directory);
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    // TODO add more as in org.opendaylight.controller.md.sal.dom.broker.impl.DOMBrokerTest & Co.

}
