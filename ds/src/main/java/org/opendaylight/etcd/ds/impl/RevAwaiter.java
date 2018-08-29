/*
 * Copyright (c) 2018 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.etcd.ds.impl;

import com.google.errorprone.annotations.Var;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.time.Duration;
import java.util.PriorityQueue;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.concurrent.ThreadSafe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Concurrency utility to await availability of certain revisions.
 *
 * @author Michael Vorburger.ch
 */
@ThreadSafe
class RevAwaiter {

    // TODO This must take possible long overflow of the long revision into account...

    // TODO is it better to use java.util.concurrent.locks.Condition instead of Object.wait() - why?

    private static final Logger LOG = LoggerFactory.getLogger(RevAwaiter.class);

    private static class AwaitableRev {
        final long rev;

        AwaitableRev(long rev) {
            this.rev = rev;
        }
    }

    // TODO this could just be a long now, and doesn't have to be an AtomicLong anymore?
    private final AtomicLong currentRev = new AtomicLong();
    private final PriorityQueue<AwaitableRev> pq = new PriorityQueue<>((o1, o2) -> Long.compare(o1.rev, o2.rev));
    private final String nodeName;

    RevAwaiter(String nodeName) {
        this.nodeName = nodeName;
    }

    @SuppressFBWarnings("NO_NOTIFY_NOT_NOTIFYALL")
    synchronized void update(long rev) {
        // Testing here is for debugging problems during development.
        // This IllegalStateException is not expected to ever happen in production,
        // if there are no logical design errors made in the code using this.
        currentRev.getAndUpdate(previous -> {
            if (rev <= previous) {
                throw new IllegalStateException(
                        nodeName + " update must be greater than current value: " + rev + " / " + previous);
            } else {
                return rev;
            }
        });

        for (AwaitableRev awaitable : pq) {
            if (rev >= awaitable.rev) {
                awaitable.notify();
            } else {
                break;
            }
        }

        LOG.info("{} update: {}", nodeName, rev);
    }

    @SuppressWarnings("checkstyle:AvoidHidingCauseException")
    synchronized void await(long rev, Duration maxWaitTime) throws TimeoutException, InterruptedException {
        if (currentRev.get() >= rev) {
            return;
        }
        AwaitableRev awaitable = new AwaitableRev(rev);
        pq.add(awaitable);
        synchronized (awaitable) {
            // account for possible spurious wake up
            @Var long now = System.nanoTime();
            long deadline = now + maxWaitTime.toNanos();
            while (currentRev.get() < rev && now < deadline) {
                // http://errorprone.info/bugpattern/WaitNotInLoop
                awaitable.wait((deadline - now) / 1000000);
                now = System.nanoTime();
            }
            if (now >= deadline) {
                throw new TimeoutException();
            }
            // else it's a real awaitable.notify(), not spurious nor timeout, and we return to caller.
        }
    }
}
