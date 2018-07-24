/*
 * Copyright (c) 2018 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.etcd.ds.impl;

import static com.google.common.truth.Truth.assertThat;

import com.coreos.jetcd.Client;
import com.coreos.jetcd.data.ByteSequence;
import com.google.common.io.ByteStreams;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.Mockito;
import org.opendaylight.controller.cluster.datastore.node.utils.stream.NormalizedNodeDataInput;
import org.opendaylight.controller.cluster.datastore.node.utils.stream.NormalizedNodeDataOutput;
import org.opendaylight.controller.cluster.datastore.node.utils.stream.NormalizedNodeInputOutput;
import org.opendaylight.etcd.utils.ByteSequences;
import org.opendaylight.yang.gen.v1.urn.opendaylight.etcd.test.rev180628.HelloWorldContainer;
import org.opendaylight.yangtools.yang.common.QName;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.PathArgument;

/**
 * Unit test for {@link EtcdKV}.
 *
 * @author Michael Vorburger.ch
 */
@SuppressWarnings("javadoc")
public class EtcdKVTest {

    @Test
    public void testYangInstanceIdentifierToAndFromByteSequence() throws EtcdException {
        @SuppressWarnings("resource") // because Client is just mocked anyway
        EtcdKV etcdKV = new EtcdKV(Mockito.mock(Client.class), (byte)'t');
        YangInstanceIdentifier path = YangInstanceIdentifier.EMPTY.node(new NodeIdentifier(HelloWorldContainer.QNAME));
        ByteSequence byteSequence = etcdKV.toByteSequence(path);
        YangInstanceIdentifier path2 = etcdKV.fromByteSequenceToYangInstanceIdentifier(byteSequence);
        assertThat(path).named(ByteSequences.asString(byteSequence)).isEqualTo(path2);
    }

    @Test
    @Ignore // TODO just remove this unneeded failing test (and the un-used methods they test)
    public void testPathArgumentToAndFromByteSequence() throws EtcdException {
        @SuppressWarnings("resource") // because Client is just mocked anyway
        EtcdKV etcdKV = new EtcdKV(Mockito.mock(Client.class), (byte)'t');
        PathArgument pathArgument = new NodeIdentifier(HelloWorldContainer.QNAME);
        ByteSequence byteSequence = etcdKV.toByteSequence(pathArgument);
        PathArgument pathArgument2 = etcdKV.fromByteSequenceToPathArgument(byteSequence);
        assertThat(pathArgument).named(ByteSequences.asString(byteSequence)).isEqualTo(pathArgument2);
    }

    @Test
    @Ignore // TODO just remove this unneeded failing test (and the un-used methods they test)
    public void testPathArgumentStreaming() throws IOException  {
        QName testQName = QName.create(
                "urn:opendaylight:params:xml:ns:yang:controller:md:sal:dom:store:test",
                "2014-03-13", "test");
        // PathArgument pathArgument = new NodeIdentifier(HelloWorldContainer.QNAME);
        PathArgument pathArgument = new NodeIdentifier(testQName);

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        NormalizedNodeDataOutput nnout = NormalizedNodeInputOutput.newDataOutput(ByteStreams.newDataOutput(bos));

        nnout.writePathArgument(pathArgument);

        byte[] bytes = bos.toByteArray();
        Assert.assertEquals(95, bytes.length);

        NormalizedNodeDataInput nnin = NormalizedNodeInputOutput.newDataInputWithoutValidation(ByteStreams.newDataInput(
            bytes));

        PathArgument newPathArgument = nnin.readPathArgument();
        Assert.assertEquals(pathArgument, newPathArgument);
    }

}
