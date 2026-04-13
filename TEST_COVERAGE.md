# Test Coverage

Run all tests with:
```
./testall.sh
```
or directly:
```
./gradlew --no-daemon clean test jacocoTestReport
```

---

## TheFixSimulator

**Coverage gate:** JaCoCo enforces **100% METHOD and LINE** coverage on all non-excluded classes.
The gate is defined in `TheFixSimulator/build.gradle.kts` and runs automatically as part of `./gradlew check`.

### Covered (in-scope for the JaCoCo gate)

| Test class | What it covers |
|---|---|
| `SimulatorBootstrapCoverageTest` | `SimulatorBootstrap` startup/shutdown lifecycle |
| `AeronContextTest` | Aeron driver context creation helpers |
| `AeronRuntimeTuningTest` | CPU affinity + thread priority tuning helpers |
| `MetricsSubscriberTest` | Aeron metrics subscriber callbacks |
| `ClientCoverageTest` | `FixDemoClient` session management |
| `FixDemoClientConfigTest` | Demo-client config value parsing |
| `ConfigLoaderTest` | Property file loading, env-variable overrides |
| `SimulatorConfigTest` | Config record accessors and defaults |
| `ThreadWaitStrategySupportTest` | Per-thread wait strategy selection |
| `HandlerCoverageTest` | Disruptor event handler routing |
| `DemoClientEndToEndTest` | Full send→fill round-trip integration test |
| `EngineCoverageTest` | FIX engine connection lifecycle |
| `FillCoverageTest` | Fill domain path coverage |
| `FillProfileManagerTest` | Fill profile loading and selection |
| `RandomRejectCancelStrategyTest` | Reject/cancel probability logic |
| `ArtioDictionaryResolverTest` | FIX dictionary resolution |
| `MetricsRegistryTest` | Metric registration and snapshot |
| `ThroughputTrackerTest` | Throughput calculations |
| `OrderDomainCoverageTest` | Order-domain value objects |
| `WebHandlerCoverageTest` | HTTP handler routing stubs |

### Excluded from the JaCoCo gate (with rationale)

| Class pattern | Reason |
|---|---|
| `com/llexsimulator/sbe/**` | SBE-generated code — not hand-written |
| `uk/co/real_logic/artio/**` | Third-party Artio generated codec — not hand-written |
| `Main*`, `FixDemoClientMain*` | Process entry points — integration-only, no unit path |
| `AeronContext*` | OS-level driver lifecycle, requires real Aeron media driver |
| `MetricsPublisher*` | Aeron IPC publisher — requires live driver |
| `FixEngineManager*`, `FixOutboundSender*`, `FixSessionApplication*` | Artio engine wrappers — require live FIX engine |
| `WebServer*`, `WebSocketBroadcaster*` | Netty HTTP server wrappers — integration-only |
| `BenchmarkReportsHandler*` | Thin I/O delegate, no logic |
| `DisruptorPipeline*`, `OrderEventTranslator*`, `ExecutionReportHandler*` | Hot-path translators — covered by e2e test, not unit |
| `FixDemoClientApplication*`, `FixDemoClientConfig*` | Demo client thin wrappers |
| `ConfigLoader*`, `SimulatorConfig*` | Config records — properties-file binding only |
| `ArtioDictionaryResolver*` | Dictionary file I/O |
| `AeronRuntimeTuning*` | OS tuning helpers |
| `FixConnection*` | Artio connection lifecycle |
| `OrderIdGenerator*` | Thin UUID wrapper |
| `FillProfileDto*` | JSON DTO — no logic |

---

## TheFixClient

TheFixClient does **not** have a JaCoCo coverage gate, but all essential classes have dedicated test classes.

### Covered

| Test class | Tests | What it covers |
|---|---|---|
| `TheFixCucumberRunnerTest` | 21 | Gherkin parser, all step definitions, region-per-market fix, scenario outline expansion, error paths |
| `TheFixClientWorkbenchStateTest` | 16 | Workbench orchestration: order preview, region/market validation, profile management, send-order delegation |
| `TheFixFixVersionTest` | 14 | `fromCode`, `fromBeginString`, `toJson`, `options`, all enum accessors |
| `TheFixTagEntryTest` | 13 | `fromJsonArray` edge cases (null, empty, malformed), `hasValue`, `toJson` round-trip |
| `TheFixSessionProfileTest` | 12 | `defaultProfile`, `fromJson` sanitization (invalid times, unknown FIX version), derived accessors, `toJson` round-trip |
| `TheFixMessageTypeTest` | 11 | `fromCode`, domain flags (`isOrder`, `isCancel`, `isAmend`, `isExecutionReport`), `toJson`, `options` |
| `TheFixFixDictionaryCatalogTest` | 10 | Catalog snapshot structure, FIX 4.4 tag/message content, all-versions key set, copy independence |
| `TheFixBulkOptionsTest` | 10 | `normalized` clamping, `isBurstMode`, `describe` formatting |
| `TheFixClientLiveIntegrationTest` | 6 | HTTP server start, REST endpoints reachable, static asset serving (requires live JVM) |
| `TheFixSessionProfileStoreTest` | 4 | Profile CRUD persistence to disk |
| `TheFixClientFixServiceMessageClassificationTest` | 3 | FIX message type classification in the FIX service layer |
| `TheFixClientConfigTest` | 3 | Config value parsing and defaults |
| `TheFixMessageTemplateStoreTest` | 2 | Message template CRUD persistence |
| `TheFixOrderRequestTest` | 1 | `TheFixOrderRequest` builder and field accessors |
| `TheFixClientServerRoutingTest` | 1 | HTTP router wires all API paths correctly |
| `TheFixClientFixServiceBulkFlowDisconnectTest` | 1 | Bulk flow stops cleanly on FIX session disconnect |
| `TheFixClientApplicationTest` | 1 | Application context wires up without error |

### Known gaps (not unit-tested)

| Class | Reason not covered |
|---|---|
| `TheFixClientFixService` — live FIX send/receive paths | Requires a live Artio FIX engine and counterpart session; covered by manual/integration testing only |
| `TheFixClientServer` — full HTTP request/response bodies | Routing is tested; full request-body parsing and response-body content is not exercised in unit tests |
| `TheFixClientApplication` — `main()` launch | Entry-point only; wired-up context is tested by `TheFixClientApplicationTest` |
| `TheFixMessageTemplate` — `fromJson` edge cases | Simple data record; basic accessors exercised via `TheFixMessageTemplateStoreTest` |
