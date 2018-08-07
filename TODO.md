
- [X] make EtcdWatcher continuously read DataTree state... does it need a revision?
- [X] shall watch filter out our own operations, or do we **not apply and only watch**?
- [X] remove initialLoad(), or just make private and call from constructor?

- [X] study jetcd Txn and make class Etcd put() etc. transactional
- [X] Watcher's updates must be applied properly transactionally instead of each individually
- [ ] extend 1 byte O/C prefix to 2 bytes DO and DC ?  Or just watch the ENTIRE tree root?
- [ ] fix still failing tests... it must await the latest revision?
- [ ] fix InterruptedException and reactivate LogCaptureRule
- [ ] add txn.if(...) in EtcdKV.EtcdTxn

- [ ] update README to document architecture better
- [ ] publicize etc.
- [ ] ODL repo and carry on there

- [ ] optimize RevAwaiter
- [ ] EntityOwnershipService EOS ?

- [ ] make etcd clustering tests (start several EtcdLauncher, not just clients)

- [ ] git filter out the (un-used) dom2kv/ sub-project into a separate repo

- [ ] refactor code to make generic non-etcd specific kv layer, pluggable for other KV stores
- [ ] is is worth adapting to others, see https://jepsen.io/analyses, say.. Redis?  Couch DB?  Infinispan?

- [ ] instead EtcdDataStore extends InMemoryDOMDataStore, discuss an upstream artifact for what is shared
      "you should just need an InMemoryDataTree. Pattern after ShardDataTree instead."
      https://git.opendaylight.org/gerrit/#/c/73208/

- [ ] instead org.opendaylight.etcd.ds.stream.copypaste, make it visible on ODL upstream
- [ ] instead of org.opendaylight.etcd.utils, move to jetcd upstream
- [ ] MUCH clean-up and other MANY TODOs ;)

- [ ] get rid of jetcd/ artifact (as jetcd already ships an OSGi bundle and Karaf feature, now; just not released...)

- [X] EtcdDOMDataBrokerWiring needs to refactor and move from testutils/ into ds/ to become runtime *Wiring

- [ ] build a JUnitRule for EtcdLauncher, like https://github.com/vorburger/MariaDB4j/pull/139 did for MariaDB4j

- [ ] add infrautils.metrics Meters & Timers to implementation

- [ ] etcd alarms should be logged via slf4j errors in ODL (just for convenience, just in case etcd is not monitored correctly)
- [ ] com.coreos.jetcd.Maintenance ?

- [ ] Karaf feature, using https://github.com/coreos/jetcd/pull/269 - or only support opendaylight-simple? :)

- [ ] jetcd Java client retry and failover, like Go client, see https://etcd.readthedocs.io/en/latest/client-architecture.html

- [ ] jetcd could optimize and always send to leader, dynamically adapt, to prevents extra hop from ODL to etcd follower to leader, see https://etcd.readthedocs.io/en/latest/faq.html#do-clients-have-to-send-requests-to-the-etcd-leader

- [ ] compaction could cause e.g. WatchOption.Builder.withRevision(long) to return ErrCompacted.. must handle?

- [ ] safe keys in a much more compact form; basically do compression, by keeping a dictionary (persisted in etcd) of all PathArgument

- [ ] compare performance of this VS CDS? But *DO* realize that real app performance issues are NOT because of slow datastore anyway..

- [ ] remote RPCs?  Still Akka.

- [ ] properly performance profile the code

- [ ] etcd new feature to keep certain sub-tress purely in-memory instead of persisted on disk (for operational VS configuration datastore); how does K8S do this?

- [ ] add to https://github.com/coreos/etcd/blob/master/Documentation/production-users.md ;) (AKA https://coreos.com/etcd/docs/latest/production-users.html)

