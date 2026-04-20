# HsbcFixClient — Complete Rewrite Prompts

> **Agent Instructions — Read First**
>
> You are working on a multi-prompt task to build a complete, production-quality FIX workstation
> application called **HsbcFixClient** from scratch.  The work is split into **15 sequential prompts**.
> Each prompt builds on the previous ones, so always ensure that work from earlier prompts is in
> place before implementing the current prompt.
>
> **After you finish the work described in each prompt, stop and explicitly ask the user:**
> _"Prompt N complete. Ready for Prompt N+1?"_ — where N is the number of the prompt you just
> finished.  Do not proceed to the next prompt until the user confirms.
>
> **Context you must keep in mind for every prompt:**
> * Project name: `HsbcFixClient`
> * Java package root: `com.hsbc.fixclient`
> * Main class: `com.hsbc.fixclient.HsbcFixClientApplication`
> * HTTP port default: `8081`
> * All names follow the `Hsbc` prefix pattern (classes, config keys, env vars, file names)
> * This is a **standalone** Java 21 Gradle project — it has no dependency on any other local module
> * The FIX protocol library is **QuickFIX/J 3.x** (initiator mode only)
> * The web framework is **Vert.x 5.x**
> * The embedded database is **H2 2.x**
> * Logging: SLF4J API + Log4j2 implementation

---

## Prompt 1 — Project Foundation: Gradle, Packages, and JVM Configuration

### Goal
Set up the standalone Gradle project from scratch with the correct structure, dependencies, and JVM configuration.

### Deliverables

**1.1 Repository structure**

Create a standalone Gradle project (not a multi-module build) with the following directory layout:

```
HsbcFixClient/
  build.gradle.kts
  settings.gradle.kts
  gradlew  (Gradle wrapper)
  gradlew.bat
  gradle/wrapper/gradle-wrapper.jar
  gradle/wrapper/gradle-wrapper.properties
  config/
    target-hsbcfixclient.properties
  src/
    main/
      java/com/hsbc/fixclient/   (source root — empty for now)
      resources/
        log4j2.xml
        web/
          index.html   (placeholder: just a minimal HTML shell — full UI comes later)
    test/
      java/com/hsbc/fixclient/   (test root — empty for now)
```

**1.2 `settings.gradle.kts`**

```kotlin
rootProject.name = "HsbcFixClient"
```

**1.3 `build.gradle.kts`**

Apply the `application` plugin.

Java toolchain: Java 21.

Dependencies (use exact versions):
* `io.vertx:vertx-stack-depchain:5.0.8` (BOM/platform)
* `io.vertx:vertx-core`
* `io.vertx:vertx-web`
* `org.slf4j:slf4j-api:2.0.17`
* `org.apache.logging.log4j:log4j-core:2.25.3`
* `org.apache.logging.log4j:log4j-slf4j2-impl:2.25.3`
* `com.h2database:h2:2.2.224`
* `org.quickfixj:quickfixj-core:3.0.0`
* `org.quickfixj:quickfixj-messages-fix42:3.0.0`
* `org.quickfixj:quickfixj-messages-fix44:3.0.0`
* `org.quickfixj:quickfixj-messages-fix50:3.0.0`
* `org.quickfixj:quickfixj-messages-fix50sp2:3.0.0`

Test dependencies:
* `org.junit:junit-bom:5.10.0` (BOM)
* `org.junit.jupiter:junit-jupiter`
* `org.junit.platform:junit-platform-launcher` (runtimeOnly)

`application` block:
* `mainClass = "com.hsbc.fixclient.HsbcFixClientApplication"`
* `applicationDefaultJvmArgs` must include:
  * `--add-exports=java.base/jdk.internal.misc=ALL-UNNAMED`
  * `--add-opens=java.base/sun.nio.ch=ALL-UNNAMED`
  * `--add-opens=java.base/java.nio=ALL-UNNAMED`
  * `--add-opens=java.base/java.lang=ALL-UNNAMED`

`tasks.test` block:
* `useJUnitPlatform()`
* `minHeapSize = "512m"`, `maxHeapSize = "2g"`
* Same JVM args as the application block above

**1.4 `config/target-hsbcfixclient.properties`**

```properties
# Native runtime target profile for HsbcFixClient
# java.heap.min and java.heap.max map to JVM -Xms / -Xmx for the launcher.
# cpu.pinning is optional. Leave blank to disable. Examples: 0, 1-2, 0,2-3

java.heap.min=256m
java.heap.max=512m
cpu.pinning=
```

**1.5 `src/main/resources/log4j2.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<Configuration status="WARN">
    <Appenders>
        <Console name="Console" target="SYSTEM_OUT">
            <PatternLayout pattern="%d{HH:mm:ss.SSS} %-5level [%t] %c{1} - %msg%n"/>
        </Console>
    </Appenders>
    <Loggers>
        <Root level="INFO">
            <AppenderRef ref="Console"/>
        </Root>
    </Loggers>
</Configuration>
```

**1.6 Placeholder `HsbcFixClientApplication.java`**

Create `src/main/java/com/hsbc/fixclient/HsbcFixClientApplication.java` with a minimal class that just has a `public static void main(String[] args)` method that prints `"HsbcFixClient starting..."` so that `./gradlew run` compiles and runs without error.

**1.7 Verify**

Run `./gradlew clean build` and confirm there are no compilation errors.

---

## Prompt 2 — Configuration Layer

### Goal
Implement the full application configuration record with layered property/env-var resolution.

### Deliverables

Create `src/main/java/com/hsbc/fixclient/HsbcFixClientConfig.java`.

The class is a **Java record** with the following components:

| Field | Type | Description |
|---|---|---|
| `host` | `String` | HTTP server bind address |
| `port` | `int` | HTTP server port |
| `fixHost` | `String` | FIX acceptor hostname |
| `fixPort` | `int` | FIX acceptor TCP port |
| `beginString` | `String` | FIX protocol begin-string (e.g. `"FIX.4.4"`) |
| `senderCompId` | `String` | FIX SenderCompID |
| `targetCompId` | `String` | FIX TargetCompID |
| `defaultApplVerId` | `String` | Default application version (FIXT.1.1 sessions) |
| `heartBtIntSec` | `int` | FIX heartbeat interval seconds |
| `reconnectIntervalSec` | `int` | QuickFIX/J reconnect interval seconds |
| `defaultRatePerSecond` | `int` | Default bulk-flow orders per second |
| `quickFixLogDir` | `String` | Root log/storage directory path |
| `rawMessageLoggingEnabled` | `boolean` | Enable QuickFIX/J file-log for raw FIX messages |
| `sessionTimeoutMinutes` | `int` | Inactivity timeout for bearer token sessions |
| `orderDataRetentionDays` | `int` | Days to keep persisted order blotter data |

**Resolution pattern** — every property uses a 4-tier fallback:
`(primarySystemProperty, primaryEnvVar, legacySystemProperty, legacyEnvVar, hardcodedDefault)`

Static factory: `HsbcFixClientConfig.fromSystemProperties()` resolves all values using the table below:

| Field | Primary property | Primary env | Legacy property | Legacy env | Default |
|---|---|---|---|---|---|
| `port` | `hsbc.client.port` | `HSBC_CLIENT_PORT` | — | — | `8081` |
| `fixHost` | `hsbc.fix.host` | `HSBC_FIX_HOST` | `fix.demo.host` | `FIX_CLIENT_HOST` | `localhost` |
| `fixPort` | `hsbc.fix.port` | `HSBC_FIX_PORT` | `fix.demo.port` | `FIX_CLIENT_PORT` | `9880` |
| `beginString` | `hsbc.fix.beginString` | `HSBC_FIX_BEGIN_STRING` | `fix.demo.beginString` | `FIX_CLIENT_BEGIN_STRING` | `FIX.4.4` |
| `senderCompId` | `hsbc.fix.senderCompId` | `HSBC_FIX_SENDER_COMP_ID` | `fix.demo.senderCompId` | `FIX_CLIENT_SENDER_COMP_ID` | `HSBC_TRDR01` |
| `targetCompId` | `hsbc.fix.targetCompId` | `HSBC_FIX_TARGET_COMP_ID` | `fix.demo.targetCompId` | `FIX_CLIENT_TARGET_COMP_ID` | `FIXSIM` |
| `defaultApplVerId` | `hsbc.fix.defaultApplVerId` | `HSBC_FIX_DEFAULT_APPL_VER_ID` | `fix.demo.defaultApplVerId` | `FIX_CLIENT_DEFAULT_APPL_VER_ID` | `FIX.4.4` |
| `heartBtIntSec` | `hsbc.fix.heartBtInt` | `HSBC_FIX_HEARTBTINT` | `fix.demo.heartBtInt` | `FIX_CLIENT_HEARTBTINT` | `30` |
| `reconnectIntervalSec` | `hsbc.fix.reconnectIntervalSec` | `HSBC_FIX_RECONNECT_INTERVAL_SEC` | `fix.demo.reconnectIntervalSec` | `FIX_CLIENT_RECONNECT_INTERVAL_SEC` | `5` |
| `defaultRatePerSecond` | `hsbc.fix.defaultRatePerSecond` | `HSBC_FIX_DEFAULT_RATE` | `fix.demo.rate` | `FIX_DEMO_RATE` | `25` |
| `quickFixLogDir` | `hsbc.fix.logDir` | `HSBC_FIX_LOG_DIR` | `fix.demo.logDir` | — | `logs/hsbcfixclient/quickfixj` |
| `rawMessageLoggingEnabled` | `hsbc.fix.rawLoggingEnabled` | `HSBC_FIX_RAW_LOGGING_ENABLED` | `fix.demo.rawLoggingEnabled` | `FIX_CLIENT_RAW_LOGGING_ENABLED` | `false` |
| `sessionTimeoutMinutes` | `hsbc.client.sessionTimeoutMinutes` | `HSBC_CLIENT_SESSION_TIMEOUT_MINUTES` | — | — | `480` |
| `orderDataRetentionDays` | `hsbc.client.orderDataRetentionDays` | `HSBC_CLIENT_ORDER_DATA_RETENTION_DAYS` | — | — | `1` |

The `host` field is always `"0.0.0.0"`.

**Additional methods:**

* `String localUrl()` — returns `"http://localhost:" + port`
* `HsbcFixClientConfig withQuickFixLogDir(String newLogDir)` — returns a copy of this record with only `quickFixLogDir` replaced (used to create per-user storage isolation)

**Port parsing rules:**
* Value must be 1–65535; otherwise fall back to the default
* Non-numeric input falls back to the default

**Integer parsing rules:**
* Parsed value must be > 0; otherwise fall back to the default

**Boolean parsing:**
* Uses `Boolean.parseBoolean(raw.trim())` — any value other than `"true"` (case-insensitive) is `false`

Write a test class `HsbcFixClientConfigTest` that:
* Verifies `fromSystemProperties()` returns the hardcoded defaults when no properties are set
* Verifies `parsePort` returns the default for invalid and out-of-range inputs
* Verifies `withQuickFixLogDir` returns a copy with only the log dir changed

---

## Prompt 3 — Authentication Layer

### Goal
Implement the pluggable authentication module, authenticated user model, session value type, and the concurrent session registry.

### Deliverables

**3.1 `AuthModule.java`** — public interface

```
String name()
Optional<AuthenticatedUser> authenticate(String username, String password)
```

**3.2 `AuthenticatedUser.java`** — public record

Fields: `String username`, `String displayName`, `String role`.
Compact constructor must:
* Reject blank username/displayName/role with `IllegalArgumentException`
* Normalise username to lowercase (Locale.ROOT)
* Trim displayName
* Uppercase role (Locale.ROOT)

**3.3 `HsbcDemoAuthModule.java`** — public final class implementing `AuthModule`

Hardcoded demo users (username : password → displayName, role):
* `admin` : `admin` → "System Administrator", ADMIN
* `trader1` : `trader1` → "Alex Trader", TRADER
* `trader2` : `trader2` → "Blake Trader", TRADER
* `trader3` : `trader3` → "Casey Trader", TRADER

`name()` returns `"Demo"`.
`authenticate(username, password)` is case-insensitive for username, case-sensitive for password.
Return `Optional.empty()` if credentials do not match.

**3.4 `UserSession.java`** — package-private record

Fields: `String token`, `AuthenticatedUser user`, `HsbcWorkbenchState workbenchState`, `Instant expiresAt`.
Compact constructor rejects null/blank token, null user, null workbenchState, null expiresAt.
`UserSession withExpiresAt(Instant newExpiresAt)` — copy with updated expiry.
`boolean isExpired()` — returns `!Instant.now().isBefore(expiresAt)`.

**3.5 `UserSessionRegistry.java`** — package-private final class implementing `AutoCloseable`

Responsibilities:
* One active session per username; a second login refreshes the existing session rather than creating a duplicate
* Bearer token is a `UUID.randomUUID().toString()`
* Sliding expiry: every successful `validate()` call resets the timer
* Stale sessions are reaped lazily on lookup and eagerly on logout
* Per-user storage paths are isolated under `<quickFixLogDir>/users/<username>/`
* **Path-traversal protection**: username must match `^[a-zA-Z0-9_-]{1,64}$`; reject anything else with `IllegalArgumentException`

Constructor: `UserSessionRegistry(HsbcFixClientConfig config, AuthModule authModule)`

Public methods:
* `Optional<UserSession> login(String username, String password)` — authenticate; create or refresh session
* `Optional<UserSession> validate(String token)` — look up, slide expiry, return session or empty
* `boolean logout(String token)` — invalidate token, close workbench state
* `int activeSessions()` — current count
* `void close()` — close all sessions, clear maps

Internal helpers:
* `static String maskTokenForLog(String token)` — returns first 4 + "..." + last 4 chars; `"***"` for tokens ≤ 8 chars; `"<null>"` / `"<empty>"` for null/blank. **Never log tokens in plaintext.**
* Validate that `baseDir.resolve(username)` does not escape `baseDir` (belt-and-suspenders path check after `normalize()`).

Concurrency: use `ConcurrentHashMap` for the token→session map and a second `ConcurrentHashMap` for username→token.  The `login` method must use `compute()` on the username key to make session create/refresh atomic per user.

Write tests (`UserSessionRegistryTest`, `DemoAuthModuleTest`) covering login, re-login token refresh, logout, expiry, and path-traversal rejection.

---

## Prompt 4 — FIX Version Enum and Session Profile Model

### Goal
Implement the FIX protocol version enum, session profile value type, and the persistent profile store.

### Deliverables

**4.1 `HsbcFixVersion.java`** — package-private enum

Constants and their attributes:

| Constant | code | label | beginString | defaultApplVerId | dictionaryResource |
|---|---|---|---|---|---|
| `FIX_42` | `"FIX_42"` | `"FIX 4.2"` | `"FIX.4.2"` | `"FIX.4.2"` | `"FIX42.xml"` |
| `FIX_44` | `"FIX_44"` | `"FIX 4.4"` | `"FIX.4.4"` | `"FIX.4.4"` | `"FIX44.xml"` |
| `FIX_50` | `"FIX_50"` | `"FIX 5.0"` | `"FIXT.1.1"` | `"FIX.5.0"` | `"FIX50.xml"` |
| `FIX_52` | `"FIX_52"` | `"FIX 5.2"` | `"FIXT.1.1"` | `"FIX.5.0SP2"` | `"FIX50SP2.xml"` |

Methods:
* `code()`, `label()`, `beginString()`, `defaultApplVerId()`, `dictionaryResource()`
* `JsonObject toJson()` — all five fields
* `static HsbcFixVersion fromCode(String rawCode)` — case-insensitive, `"FIX_50_SP2"` maps to `FIX_52`, unknown defaults to `FIX_44`
* `static HsbcFixVersion fromBeginString(String beginString, String defaultApplVerId)` — `"FIXT.1.1"` + SP2 hint → `FIX_52`, `"FIXT.1.1"` → `FIX_50`, `"FIX.4.2"` → `FIX_42`, `"FIX.4.4"` → `FIX_44`, default → `FIX_44`
* `static List<HsbcFixVersion> options()` — all values as list

**4.2 `HsbcSessionProfile.java`** — package-private record

Fields: `name`, `senderCompId`, `targetCompId`, `fixHost`, `fixPort`, `fixVersionCode`, `resetOnLogon`, `sessionStartTime`, `sessionEndTime`, `heartBtIntSec`, `reconnectIntervalSec`, `quickFixLogDir`, `rawMessageLoggingEnabled`.

Constants: `DEFAULT_PROFILE_NAME = "Default profile"`.

Static factories:
* `defaultProfile(HsbcFixClientConfig config)` — seeds a profile from config defaults; resetOnLogon=true, sessionStart="00:00:00", sessionEnd="00:00:00"
* `fromJson(JsonObject json, HsbcFixClientConfig config)` — parses all fields from JSON; falls back to `defaultProfile` values for missing/blank entries; port must be 1–65535

Derived methods:
* `HsbcFixVersion fixVersion()` — from code
* `String beginString()` — from fixVersion
* `String defaultApplVerId()` — from fixVersion
* `Path storeDir()` — `Path.of(quickFixLogDir, "store")`
* `Path rawLogDir()` — `Path.of(quickFixLogDir, "messages")`
* `JsonObject toJson()` — all fields including `fixVersionLabel`, `beginString`, `defaultApplVerId`

Session time validation: `HH:mm:ss` format only (regex `^([01]\d|2[0-3]):[0-5]\d:[0-5]\d$`); invalid values fall back to the default.

**4.3 `HsbcSessionProfileStore.java`** — package-private final class

Persistent store backed by a JSON file (`profiles.json`) in the configured storage directory.

Constructor: `HsbcSessionProfileStore(HsbcFixClientConfig config)`.
* Default storage path: `<quickFixLogDir>/profiles` (absolute, normalised)
* On construction, attempt to load the store file; if absent or unreadable, seed with the default profile and persist

Public methods:
* `HsbcSessionProfile activeProfile()` — returns the currently active profile; seeds defaults if needed
* `HsbcSessionProfile profile(String profileName)` — returns named profile or falls back to active
* `JsonObject snapshot()` — `{storagePath, defaultStoragePath, activeProfileName, profiles: [...], fixVersionOptions: [...], profileDraft: {...}}`
* `JsonObject workspaceSnapshot()` — `{storagePath, defaultStoragePath, profileCount}`
* `void saveProfile(JsonObject request)` — creates or renames a profile; activates by default unless `"activate": false` is set; persists
* `void activateProfile(String profileName)` — sets active profile; persists
* `boolean deleteProfile(String profileName)` — refuses if only one profile remains; persists on success
* `void updateStoragePath(String requestedPath)` — switches to the new directory, loading existing profiles or seeding defaults

File format: `{"activeProfileName": "...", "profiles": [...]}`

All methods that mutate state must be `synchronized`.

Write a test class `HsbcSessionProfileStoreTest` covering default seeding, save/rename/activate/delete, persistence round-trip, and storage path switching.

---

## Prompt 5 — Order and Message Models

### Goal
Implement the FIX message type enum, bulk-flow options, custom tag entry, and the full order request value type with bulk-variant generation.

### Deliverables

**5.1 `HsbcMessageType.java`** — package-private enum

| Constant | code | label | shortLabel | msgType | requiresOrigClOrdId | supportsBulk |
|---|---|---|---|---|---|---|
| `NEW_ORDER_SINGLE` | `"NEW_ORDER_SINGLE"` | `"New Order Single"` | `"NOS"` | `"D"` | false | true |
| `ORDER_CANCEL_REPLACE_REQUEST` | `"ORDER_CANCEL_REPLACE_REQUEST"` | `"Amend / Cancel Replace"` | `"AMEND"` | `"G"` | true | false |
| `ORDER_CANCEL_REQUEST` | `"ORDER_CANCEL_REQUEST"` | `"Order Cancel Request"` | `"CANCEL"` | `"F"` | true | false |

Methods: getters, `JsonObject toJson()`, `static HsbcMessageType fromCode(String rawCode)` (defaults to `NEW_ORDER_SINGLE`), `static List<HsbcMessageType> options()`.

**5.2 `HsbcBulkOptions.java`** — package-private record

Fields: `String mode`, `int ratePerSecond`, `int burstSize`, `int burstIntervalMs`, `int totalOrders`.

`HsbcBulkOptions normalized(int defaultRatePerSecond)` — clamps mode to `"BURST"` or `"FIXED_RATE"`, ensures positive values, ensures `totalOrders >= 0`.

`boolean isBurstMode()` — `"BURST".equals(mode)`.

`String describe()` — e.g. `"25 orders/sec continuously"` or `"10 orders every 500 ms for 200 orders"`.

**5.3 `HsbcTagEntry.java`** — package-private record

Fields: `int tag`, `String name`, `String value`, `boolean custom`.

`static List<HsbcTagEntry> fromJsonArray(JsonArray array)` — parses `tag` (Number or String), `name`, `value`, `custom` from each element; returns unmodifiable list.

`JsonObject toJson()`.

`boolean hasValue()` — value is non-null and non-blank.

**5.4 `HsbcOrderRequest.java`** — package-private record

Fields: `messageTypeCode`, `clOrdId`, `origClOrdId`, `symbol`, `side`, `quantity` (int), `price` (double), `stopPrice` (double), `timeInForce`, `orderType`, `priceType`, `region`, `market`, `currency`, `List<HsbcTagEntry> additionalTags`.

Compact constructor copies `additionalTags` to unmodifiable list.

**`HsbcOrderRequest bulkVariant(long seed)`** — generates a randomised variant for bulk flow:
* Only applies when `messageType() == NEW_ORDER_SINGLE`; returns `this` otherwise
* Uses the 64-bit Murmur3-style finaliser mix to derive independent seeds for each field:
  ```
  long mix(long v) {
      v ^= (v >>> 33); v *= 0xff51afd7ed558ccdL;
      v ^= (v >>> 33); v *= 0xc4ceb9fe1a85ec53L;
      v ^= (v >>> 33); return v;
  }
  ```
* XOR each field's seed with a distinct constant before mixing:
  * symbol:     `seed ^ 0x9E3779B97F4A7C15L`
  * side:       `seed ^ 0xC2B2AE3D27D4EB4FL`
  * quantity:   `seed ^ 0x165667B19E3779F9L`
  * orderType:  `seed ^ 0x85EBCA77C2B2AE63L`
  * tif:        `seed ^ 0x27D4EB2F165667C5L`
  * price:      `seed ^ 0x94D049BB133111EBL`
  * stopPrice:  `seed ^ 0x2545F4914F6CDD1DL`
* Symbol pool per market (pick by index `floorMod(Long.remainderUnsigned(seed, poolSize), poolSize)`):

  | Market MIC | Symbols |
  |---|---|
  | XNAS | AAPL, MSFT, NVDA, AMZN |
  | XNYS | IBM, GS, KO, JNJ |
  | XLON | BP.L, VOD.L, HSBA.L, AZN.L |
  | XPAR | AIR.PA, BNP.PA, MC.PA, OR.PA |
  | XETR | SAP.DE, SIE.DE, BMW.DE, ADS.DE |
  | XSWX | NESN.SW, NOVN.SW, ROG.SW, UBSG.SW |
  | XHKG | 0700.HK, 9988.HK, 1299.HK, 2318.HK |
  | XTKS | 7203.T, 6758.T, 9984.T, 8035.T |
  | XSES | D05.SI, O39.SI, U11.SI, C38U.SI |
  | XSHG | 600519.SS, 601318.SS, 600036.SS, 601988.SS |
  | XTSE | RY.TO, SHOP.TO, TD.TO, BNS.TO |
  | BVMF | PETR4, VALE3, ITUB4, ABEV3 |

* Side pool: `["BUY", "SELL", "SELL_SHORT"]`
* Order type pool: `["MARKET", "LIMIT", "STOP", "STOP_LIMIT"]`
* Time-in-force pool: `["DAY", "IOC", "FOK", "GTC"]`
* Quantity pool: `[100, 200, 300, 500, 750, 1000, 1500, 2000, 2500, 5000]`
* Limit price: anchor × (0.92 + normalizedFraction × 0.18), rounded to 2 d.p., only when order type requires limit price
* Stop price: anchor × (0.96 + normalizedFraction × 0.10), min 0.01, only when order type requires stop price
* `normalizedFraction(seed)` = `(seed & Long.MAX_VALUE) % 10_000L / 10_000d`
* `roundPrice(value)` = `Math.round(Math.max(value, 0.01d) * 100d) / 100d`
* `clOrdId` and `origClOrdId` are empty string in the variant (generated by the service later)

**Other record methods:**
* `String summary()` — human-readable description of the order
* `HsbcMessageType messageType()` — from code
* `String outboundClOrdIdOr(String fallback)` — returns clOrdId if non-blank, else fallback
* `char fixSide()` — BUY→`Side.BUY`, SELL→`Side.SELL`, SELL_SHORT→`Side.SELL_SHORT`
* `char fixTimeInForce()` — DAY→`'0'`, IOC→`'3'`, FOK→`'4'`, GTC→`'1'`, GTD→`'6'`, OPG→`'2'`
* `char fixOrdType()` — MARKET→`OrdType.MARKET`, STOP→`OrdType.STOP`, STOP_LIMIT→`OrdType.STOP_LIMIT`, MARKET_ON_CLOSE→`OrdType.MARKET_ON_CLOSE`, LIMIT_ON_CLOSE→`OrdType.LIMIT_ON_CLOSE`, PEGGED→`OrdType.PEGGED`, default→`OrdType.LIMIT`
* `int fixPriceType()` — PERCENTAGE→1, FIXED_AMOUNT→3, YIELD→9, SPREAD→6, default→2
* `boolean requiresLimitPrice()`, `boolean requiresStopPrice()`

Write `HsbcOrderRequestTest` covering `bulkVariant` determinism, `summary`, `fixSide/fixOrdType/fixTimeInForce` mappings, and `requiresLimitPrice/requiresStopPrice`.

---

## Prompt 6 — Persistence Layer

### Goal
Implement the H2-backed order blotter persistence store and the message template store.

### Deliverables

**6.1 `HsbcMessageTemplate.java`** — package-private record

Fields: `long id`, `String profileName`, `String name`, `String messageType`, `JsonObject draft`, `boolean autoSaved`, `Instant updatedAt`.

`JsonObject toJson()` — all fields; draft returns a copy; updatedAt as ISO string.

**6.2 `HsbcOrderStore.java`** — package-private final class implementing `AutoCloseable`

H2 embedded database (file-based, no auto-server) for per-user order blotter persistence.

Constructor `HsbcOrderStore(HsbcFixClientConfig config)`:
* Database path: `<quickFixLogDir>/orders/hsbcfixclient-orders` (absolute, normalised)
* Create parent directories on construction
* JDBC URL: `jdbc:h2:file:<path>;AUTO_SERVER=FALSE;DB_CLOSE_DELAY=0;DATABASE_TO_UPPER=false`
* `retentionDays = Math.max(1, config.orderDataRetentionDays())`
* Call `initializeSchema()` on construction

Second constructor: `HsbcOrderStore(Path databaseBasePath, int retentionDays)` for tests.

Schema (DDL executed inside `initializeSchema()`):
```sql
CREATE TABLE IF NOT EXISTS user_orders (
    id           BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    username     VARCHAR(255)  NOT NULL,
    profile_name VARCHAR(255)  NOT NULL,
    orders_json  CLOB          NOT NULL,
    updated_at   TIMESTAMP     NOT NULL
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_user_orders_uk
    ON user_orders(username, profile_name);
```

Methods:
* `synchronized void persist(String username, String profileName, JsonArray ordersJson)` — MERGE INTO (upsert by username+profileName); no-op if username/profileName blank; log warnings on SQLException
* `synchronized JsonArray load(String username, String profileName)` — purge expired first; load JSON; discard corrupted rows (delete + return empty array); return `new JsonArray()` for missing rows
* `synchronized void purgeExpired(String username)` — DELETE WHERE username=? AND updated_at < (now minus retentionDays); log deleted count at DEBUG
* `void close()` — no-op (connections opened per-operation)
* JDBC credential: username `"sa"`, password `""`

**6.3 `HsbcMessageTemplateStore.java`** — package-private final class implementing `AutoCloseable`

H2 embedded database for FIX message templates.

Constructor `HsbcMessageTemplateStore(HsbcFixClientConfig config)`:
* Database path: `<quickFixLogDir>/templates/hsbcfixclient-message-templates`

Second constructor: `HsbcMessageTemplateStore(Path databaseBasePath)` for tests.

Schema:
```sql
CREATE TABLE IF NOT EXISTS message_templates (
    id            BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    profile_name  VARCHAR(255) NOT NULL,
    template_name VARCHAR(255) NOT NULL,
    message_type  VARCHAR(64)  NOT NULL,
    template_json CLOB         NOT NULL,
    auto_saved    BOOLEAN      NOT NULL,
    updated_at    TIMESTAMP    NOT NULL
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_message_templates_profile_name
    ON message_templates(profile_name, template_name);
```

Methods:
* `synchronized JsonObject snapshot(String profileName)` — SELECT all templates for profile ordered by `updated_at DESC, template_name ASC`; returns `{profileName, items: [...]}`
* `synchronized HsbcMessageTemplate saveManualTemplate(String profileName, String requestedName, JsonObject draft)` — blank name throws `IllegalArgumentException`; MERGE by profileName+name
* `synchronized HsbcMessageTemplate autoSaveTemplate(String profileName, JsonObject draft)` — name = `"Auto · yyyy-MM-dd HH:mm:ss.SSS · <msgType> · <symbol>"` (system timezone); MERGE by name
* `void close()` — no-op
* Private `sanitizeDraft(JsonObject draft)` — copies draft, sets defaults for all required fields (messageType=NEW_ORDER_SINGLE, region=AMERICAS, market=XNAS, symbol=AAPL, side=BUY, orderType=LIMIT, priceType=PER_UNIT, timeInForce=DAY, currency=USD, quantity=100, price=100.25, stopPrice=0.0, additionalTags=[])

Write `HsbcOrderStoreTest` and `HsbcMessageTemplateStoreTest` using `@TempDir`.

---

## Prompt 7 — FIX Dictionary Catalog

### Goal
Implement the runtime FIX dictionary catalog that loads, parses, and serves structured metadata from the QuickFIX/J XML dictionary resources.

### Deliverables

**`HsbcFixDictionaryCatalog.java`** — package-private final class

Constructor: parses all four FIX XML dictionaries at startup and caches the result as a `JsonObject snapshot`.

`JsonObject snapshot()` — returns a defensive copy of the pre-built snapshot.

The snapshot structure:
```json
{
  "versions": [ { "code", "label", "beginString", "defaultApplVerId", "tags": [...], "messages": { "NEW_ORDER_SINGLE": [...], ... } }, ... ],
  "messageTypes": [ { "code", "label", "shortLabel", "msgType", "requiresOrigClOrdId", "supportsBulk" }, ... ]
}
```

**Dictionary loading:**

For each `HsbcFixVersion`, load the resource from `Thread.currentThread().getContextClassLoader().getResourceAsStream(version.dictionaryResource())`.  If the stream is null, return a partial object with empty tags and messages.

XML parsing must use a **secure** `DocumentBuilderFactory`:
* `factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)`
* `factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)`
* `factory.setNamespaceAware(false)`

**Field parsing** (`<fields>` → `<field>`):
* Collect `{tag: int, name: String, type: String, enumValues: [{code, label}]}` for each `<field>` element
* Sort by tag number ascending in the output array

**Component parsing** (`<components>` → `<component>`):
* Build `Map<String, Element>` for later reference during message field collection

**Message field collection** for `NewOrderSingle`, `OrderCancelReplaceRequest`, `OrderCancelRequest`:
* Recursively collect field names from `<field>`, `<component>` (expand inline), and `<group>` elements
* Use `LinkedHashSet` for deduplication; look up each field in the field map; sort by tag ascending

**Error handling:** if any exception occurs loading a version, return a partial object with `"error": exception.getMessage()`.

Write `HsbcFixDictionaryCatalogTest` that asserts the snapshot has at least one version entry with a non-empty tags array, and that `messageTypes` contains exactly the three supported types.

---

## Prompt 8 — FIX Service: Session Lifecycle and Auto-Flow

### Goal
Implement the core `HsbcFixService` class: QuickFIX/J `Application` lifecycle, session management, auto-flow scheduling, and the inner support types.

### Deliverables

**`HsbcFixService.java`** — package-private final class implementing `quickfix.Application` and `AutoCloseable`

This is the most complex class in the project.  Implement it in two parts (this prompt covers lifecycle + auto-flow; the next covers order routing + blotter).

**Inner support types** (all package-private, defined as static nested records/classes inside `HsbcFixService`):

*`EventItem`* — record with fields: `String severity`, `String title`, `String detail`, `String timestamp` (formatted as `HH:mm:ss` in the system timezone).
* `JsonObject toJson()` — all four fields.

*`FixMessageRecord`* — record with fields: `long seq`, `String direction` (SENT/RECEIVED), `String channel` (ADMIN/APPLICATION), `String msgType`, `String msgTypeLabel`, `String rawMessage`, `String preview`, `String capturedAt` (ISO instant string).
* `JsonObject toJson()` — all fields.

*`ActionOutcome`* — record with `boolean success`, `String message`.

**Constants:**
* `MAX_EVENTS = 30` (recent events ring buffer)
* `MAX_ORDERS = 40` (blotter LRU limit)
* `MAX_FIX_MESSAGES = 200` (FIX tape ring buffer)
* `PREVIEW_MAX_LENGTH = 120`

**FIX message type label map** — `Map<String, String>` mapping raw MsgType values to human-readable labels:
```
"0"→Heartbeat, "1"→Test Request, "2"→Resend Request, "3"→Reject,
"4"→Sequence Reset, "5"→Logout, "8"→Execution Report,
"9"→Order Cancel Reject, "A"→Logon, "D"→New Order Single,
"F"→Order Cancel Request, "G"→Order Cancel/Replace,
"j"→Business Message Reject, "H"→Order Status Request,
"R"→Quote Request, "S"→Quote, "V"→Market Data Request,
"W"→Market Data Snapshot, "X"→Market Data Incremental Refresh,
"Y"→Market Data Request Reject, "AE"→Trade Capture Report,
"BE"→User Request, "BF"→User Response
```

**Instance state:**
* `String username`
* `HsbcFixClientConfig config`
* `HsbcSessionProfile runtimeProfile`
* `HsbcOrderStore orderStore` (nullable)
* `SessionID activeSessionId`
* Counters: `sequence`, `sentCount`, `execReportCount`, `cancelCount`, `rejectCount`, `sendFailureCount`
* `ScheduledExecutorService autoFlowExecutor` — single thread, daemon, name `"hsbcfixclient-auto-flow"`
* `ExecutorService persistenceExecutor` — single thread, daemon, name `"hsbcfixclient-persist"`
* `Deque<EventItem> recentEvents` — `ArrayDeque`
* `LinkedHashMap<String, OrderView> recentOrders`
* `Map<String, String> clOrdIdAliases` — tracks amend/cancel clOrdId chains
* `Set<String> hiddenClOrdIds` — suppresses superseded orders from blotter
* `ConcurrentLinkedDeque<FixMessageRecord> recentFixMessages`
* `AtomicLong fixMessageSequence`
* `SocketInitiator initiator` (nullable)
* `ScheduledFuture<?> autoFlowFuture` (nullable)
* Flags: `loggedOn`, `connectionRequested`, `autoFlowActive`
* `Instant connectedAt` (nullable)
* `String sessionStatus` — display string (initial: "Standby")
* `HsbcSessionProfile connectedProfile` (nullable — set on connect)
* `HsbcOrderRequest autoFlowTemplate`, `HsbcBulkOptions autoFlowOptions`, `long autoFlowRemaining`

**Constructors:**
* `HsbcFixService(HsbcFixClientConfig config, HsbcSessionProfile runtimeProfile)` — username from `config.senderCompId()`
* `HsbcFixService(HsbcFixClientConfig config, HsbcSessionProfile runtimeProfile, HsbcOrderStore orderStore)`
* `HsbcFixService(String username, HsbcFixClientConfig config, HsbcSessionProfile runtimeProfile, HsbcOrderStore orderStore)` — primary
* `HsbcFixService(HsbcFixClientConfig config, HsbcSessionProfileStore profileStore)` — convenience for single-profile usage

Each constructor emits an initial INFO event: `"FIX service ready"` / `"QuickFIX/J initiator wiring is ready for operator commands for profile <name>."`.

**`synchronized void connect()`:**
* If `initiator != null` and `loggedOn`: emit WARN "Session already connected"
* If `initiator != null` and not `loggedOn`: emit INFO "Connection already in progress"
* Otherwise: call `ensureQuickFixDirectories(runtimeProfile)`, build `SessionSettings`, create `SocketInitiator` (with `MemoryStoreFactory`; use `FileLogFactory` if `rawMessageLoggingEnabled`, else use a no-op `LogFactory`), start initiator, set `connectionRequested=true`, `connectedProfile=runtimeProfile`, `sessionStatus="Connecting"`, emit INFO "Connection requested"
* On exception: reset `initiator=null`, emit WARN, log at WARN level

**No-op LogFactory** — implement an inner static class (or package-private class) `HsbcNoOpLogFactory` implementing `quickfix.LogFactory` and returning a no-op `quickfix.Log` that does nothing on all calls.

**`void disconnect()`:**
* Must capture and null out `initiator` reference under the lock before calling `stop(false)` on it (to prevent deadlock with QuickFIX/J callbacks)
* Call `stopAutoFlowInternal(autoFlowActive)` first
* Reset `connectionRequested`, `loggedOn`, `connectedAt`, `connectedProfile`, `activeSessionId`, `sessionStatus="Standby"`
* After releasing the lock: call `initiatorToStop.stop(false)` then re-acquire lock to emit INFO "Session disconnected"

**`synchronized void pulseTest()`** — emit INFO "Pulse check executed" with appropriate message based on `loggedOn`.

**`synchronized ActionOutcome resetSequenceNumbers()`:**
* Guard: must be loggedOn and have activeSessionId
* Look up `Session.lookupSession(sessionId)` (suppress resource warning)
* Call `session.reset()`, emit SUCCESS, return ActionOutcome(true, ...)
* On exception: emit WARN, return ActionOutcome(false, ...)

**`synchronized void startAutoFlow(HsbcOrderRequest template, HsbcBulkOptions requestedOptions)`:**
* Normalise options; store template, options, remaining count (-1 for unlimited, else totalOrders)
* Set `autoFlowActive=true`; update sessionStatus
* If not yet connected, call `connect()`
* Cancel and replace any existing `autoFlowFuture`
* For BURST mode: `scheduleAtFixedRate` using `burstIntervalMs` (MILLISECONDS)
* For FIXED_RATE mode: `scheduleAtFixedRate` using `1_000_000_000L / ratePerSecond` (NANOSECONDS, clamped to ≥ 1)
* Emit SUCCESS "Bulk flow started" if already logged on, else INFO "Bulk flow armed"

**`synchronized void stopAutoFlow()`** — delegates to `stopAutoFlowInternal(true)`.

**`synchronized String profileName()`** — returns `runtimeProfile.name()`.

**`synchronized boolean isIdle()`** — `!loggedOn && !connectionRequested && initiator == null && !autoFlowActive`.

**`JsonObject sessionSnapshot(HsbcSessionProfile configuredProfile)`** (synchronized) — returns:
```
{connected, status, host, port, beginString, senderCompId, targetCompId,
 profileName (effective), activeProfileName (configured), pendingProfileChange,
 fixVersionCode, fixVersionLabel, mode="QuickFIX/J initiator",
 uptime, autoFlowActive, autoFlowRate, autoFlowMode, autoFlowBurstSize,
 autoFlowBurstIntervalMs, autoFlowTotalOrders, autoFlowRemaining,
 autoFlowDescriptor, rawMessageLoggingEnabled}
```
`uptime` from `formatUptime()` — calculates duration since `connectedAt` in `"Xh Ym Zs"` or `"Xm Zs"` etc.; `"Not connected"` if null.

**`synchronized JsonObject kpiSnapshot()`:**
```
{readyState, openOrders, sentOrders, executionReports, cancels, rejects, sendFailures, sessionUptime}
```
`readyState`: `"FIX live"` if loggedOn, `"Connecting"` if initiator != null, `"UI ready"` otherwise.

**QuickFIX/J Application callbacks:**
* `onCreate` — emit INFO "Session created"
* `onLogon` — set `activeSessionId`, `loggedOn=true`, `connectedAt=Instant.now()`, update `sessionStatus`; emit SUCCESS "Logon complete"
* `onLogout` — clear sessionId if matches, `loggedOn=false`, `connectedAt=null`; `sessionStatus` = "Reconnecting" if `connectionRequested`, else "Standby"; emit WARN "Session logged out"
* `toAdmin` — log at DEBUG; call `captureFixMessage("SENT","ADMIN",message)`
* `fromAdmin` — log at DEBUG; call `captureFixMessage("RECEIVED","ADMIN",message)`
* `toApp` — log at DEBUG; call `captureFixMessage("SENT","APPLICATION",message)`
* `fromApp` (synchronized) — call `captureFixMessage`; dispatch to `handleExecutionReport` or `handleReject` based on MsgType

**`close()` (AutoCloseable):**
1. Call `disconnect()`
2. Under lock: if `orderStore != null`, call `orderStore.persist(username, runtimeProfile.name(), recentOrdersJson())`
3. `autoFlowExecutor.shutdownNow()`; `persistenceExecutor.shutdown()`
4. `awaitTermination(5, SECONDS)` for both; log WARN on timeout or interrupt (restore interrupt flag)

**Private helpers:**
* `void addEvent(String severity, String title, String detail)` — prepend to `recentEvents` deque; trim to `MAX_EVENTS`
* `void captureFixMessage(String direction, String channel, Message message)` — build `FixMessageRecord`; push to `recentFixMessages`; trim to `MAX_FIX_MESSAGES` by polling from tail when over limit
* `String nextClOrdId(String prefix)` — `"HSBC-" + prefix + "-" + ++sequence + "-" + System.currentTimeMillis()`
* `void stopAutoFlowInternal(boolean addEvent)` — cancel future, clear state, emit INFO if requested, update sessionStatus
* `void completeAutoFlow()` — call `stopAutoFlowInternal(false)`, emit SUCCESS "Bulk flow complete"
* `void sendAutoFlowOrders()` — called on the scheduled thread; reads template + options under lock; dispatches `ordersPerTick` orders

**`SessionSettings buildSessionSettings(HsbcSessionProfile profile)`:**

Build a QuickFIX/J `SessionSettings` object programmatically:
```
[DEFAULT]
ConnectionType=initiator
FileStorePath=<profile.storeDir()>
FileLogPath=<profile.rawLogDir()>
StartTime=<profile.sessionStartTime()>
EndTime=<profile.sessionEndTime()>
HeartBtInt=<heartBtIntSec>
ReconnectInterval=<reconnectIntervalSec>
ResetOnLogon=<Y/N>

[SESSION]
BeginString=<beginString>
SenderCompID=<senderCompId>
TargetCompID=<targetCompId>
SocketConnectHost=<fixHost>
SocketConnectPort=<fixPort>
DefaultApplVerID=<defaultApplVerId>   (only for FIXT sessions)
DataDictionary=<dictionaryResource>   (only for FIX.4.x sessions)
TransportDataDictionary=FIXT11.xml    (only for FIXT sessions)
AppDataDictionary=<dictionaryResource> (only for FIXT sessions)
```

---

## Prompt 9 — FIX Service: Order Routing and Blotter

### Goal
Continue `HsbcFixService` by implementing the order routing methods, the execution-report handler, and the inner `OrderView` model.

### Deliverables

**Inner class `OrderView`** (defined inside `HsbcFixService`, non-record mutable state holder):

Fields: `String clOrdId`, `String origClOrdId`, `String symbol`, `String side`, `int quantity`, `double price`, `String orderType`, `String timeInForce`, `String market`, `String currency`, `String messageTypeCode`, `String status`, `String ordStatus`, `double avgPx`, `double leavesQty`, `double cumQty`, `String rejectReason`, `Instant submittedAt`, `boolean autoFlow`.

Static factories:
* `OrderView.submitted(String clOrdId, HsbcOrderRequest request, boolean autoFlow)` — status "Submitted"
* `OrderView.fromExecution(Message message)` — status from ExecType/OrdStatus fields; extracts ClOrdID, Symbol, Side, etc.
* `OrderView.rejected(String clOrdId)` — status "Rejected"
* `static OrderView fromJson(JsonObject item)` — reconstructs from persisted JSON (null if item is null)

Methods:
* `boolean canAmend()` — status is "Submitted", "Sent", "Accepted", or "PartialFill" and not auto-flow
* `boolean canCancel()` — same conditions as canAmend
* `void markSent()` — status "Sent"
* `void markFailure(String code, String detail)`
* `void markRejected(String reason, String code)`
* `void updateFromExecutionReport(Message message)` — update ordStatus, avgPx, leavesQty, cumQty, status label
* `void applyAmendSubmission(String newClOrdId, int newQty, double newPrice)`
* `HsbcOrderRequest toAmendRequest(int quantity, double price)` — returns an `ORDER_CANCEL_REPLACE_REQUEST` with origClOrdId = current clOrdId
* `HsbcOrderRequest toCancelRequest()` — returns an `ORDER_CANCEL_REQUEST`
* `JsonObject toJson()` — all visible fields
* `int pendingOrQuantity()` — returns leavesQty as int

**Order routing methods:**

`synchronized void sendOrder(HsbcOrderRequest request)` — calls `sendOrderInternal(request, false, true)`.

`synchronized boolean amendOrder(String clOrdId, int quantity, double price)`:
* Find tracked order; check `canAmend()`; generate new clOrdId with prefix "AMEND"
* Build and send amend message via `sendBlotterAction()`
* On success: remove old clOrdId from `recentOrders`, add to `hiddenClOrdIds`; call `applyAmendSubmission`; `rememberOrder`; `persistOrders`; return true

`synchronized boolean cancelOrder(String clOrdId)`:
* Find tracked order; check `canCancel()`; generate new clOrdId with prefix "CANCEL"
* Build and send cancel message; on success: increment `cancelCount`; remove both clOrdIds from `recentOrders`; add both to `hiddenClOrdIds`; emit INFO "Order removed"; `persistOrders`; return true

**`private boolean sendOrderInternal(HsbcOrderRequest request, boolean autoFlowOrder, boolean addManualEvent)`:**
* Guard: `loggedOn` and `activeSessionId != null`
* Look up QuickFIX/J session; if not found or not logged on: increment `sendFailureCount`, emit WARN, return false
* Assign clOrdId (`request.outboundClOrdIdOr(nextClOrdId(...)`)
* Create `OrderView.submitted(...)`, call `rememberOrder(orderView)`
* Call `Session.sendToTarget(buildOutboundMessage(clOrdId, request), sessionId)`
* On success: `sentCount++`, `orderView.markSent()`, emit SUCCESS if manual, persist if manual
* On `SessionNotFound` or generic exception: `sendFailureCount++`, `orderView.markFailure(...)`, emit WARN, return false

**`private boolean sendBlotterAction(String outboundClOrdId, HsbcOrderRequest request, String successTitle, String successDetail)`** — similar guard; calls `buildOutboundMessage`; increments `sentCount`; emits the given SUCCESS title/detail; handles exceptions.

**`private Message buildOutboundMessage(String clOrdId, HsbcOrderRequest request)`:**

Build the correct QuickFIX/J message type (`quickfix.fix44.NewOrderSingle`, `quickfix.fix44.OrderCancelReplaceRequest`, or `quickfix.fix44.OrderCancelRequest`) based on `request.messageType()`.

For `NEW_ORDER_SINGLE`:
* Set fields: ClOrdID, HandlInst('1'), Symbol, SecurityExchange, Side, TransactTime, OrderQty, OrdType, TimeInForce, Currency, PriceType
* If requiresLimitPrice: set Price
* If requiresStopPrice: set StopPx
* For any `additionalTags` that `hasValue()`: set `new quickfix.StringField(tag, value)`

For `ORDER_CANCEL_REPLACE_REQUEST`:
* Set fields: OrigClOrdID, ClOrdID, HandlInst('1'), Symbol, SecurityExchange, Side, TransactTime, OrderQty, OrdType, TimeInForce, Price (if applicable), StopPx (if applicable), Currency

For `ORDER_CANCEL_REQUEST`:
* Set fields: OrigClOrdID, ClOrdID, Symbol, Side, TransactTime, OrderQty

**Execution report and reject handlers:**

`private void handleExecutionReport(Message message)`:
* Determine if rejected (`ExecType.FIELD == "8"` or `OrdStatus.FIELD == "8"`)
* Increment `rejectCount` or `execReportCount`
* Resolve ClOrdID through alias chain; skip if `hiddenClOrdIds` contains it
* `recentOrders.computeIfAbsent(clOrdId, ...)` → `OrderView.fromExecution(message)`; call `updateFromExecutionReport`; `trimOrders()`
* If rejected: emit WARN with reason from Text, OrdRejReason

`private void handleReject(Message message, String messageType)`:
* Increment `rejectCount`; resolve clOrdId; skip if hidden
* `computeIfAbsent` → `OrderView.rejected(clOrdId)`; call `markRejected`; `trimOrders()`; emit WARN

**Blotter helpers:**
* `private void rememberOrder(OrderView)` — put to `recentOrders`; call `trimOrders()`
* `private void trimOrders()` — remove eldest entries until `recentOrders.size() <= MAX_ORDERS`
* `private OrderView findTrackedOrder(String clOrdId)` — resolve through alias chain, look up in `recentOrders`
* `private String resolveTrackedClOrdId(String clOrdId)` — follow `clOrdIdAliases` chain with cycle detection using a seen-set
* `int pendingOrders()` — count entries in `recentOrders` where `!autoFlow` and status is not terminal

**Persistence helpers:**
* `synchronized void loadPersistedOrders(JsonArray ordersJson)` — reconstruct `OrderView` entries from JSON into `recentOrders`; call `trimOrders()`
* `private void persistOrders()` — if `orderStore != null`: snapshot `recentOrdersJson()` under lock; submit to `persistenceExecutor`

**Public accessor methods:**
* `synchronized JsonArray recentEventsJson()` — all events as JsonArray
* `synchronized JsonArray recentOrdersJson()` — reversed list (newest first)
* `synchronized boolean isLoggedOn()`
* `synchronized JsonObject recentFixMessagesJson(int limit, int offset)` — paginate `recentFixMessages` deque; return `{total, limit, offset, messages: [...]}`

Write `HsbcFixServiceBulkFlowDisconnectTest` and `HsbcFixServiceMessageClassificationTest` following standard JUnit 5 patterns (no Mockito — use test helpers and in-process QuickFIX/J if needed).

---

## Prompt 10 — Workbench State (Orchestration Layer)

### Goal
Implement `HsbcWorkbenchState` — the central orchestration class that ties together profiles, fix services, templates, orders, and reference data.

### Deliverables

**`HsbcWorkbenchState.java`** — package-private final class implementing `AutoCloseable`

**Market reference data** (static final `List<MarketDefinition>`):

Define inner record `MarketDefinition(String region, String mic, String city, String exchange, String currency, String assetClass, List<String> symbols)`.

Markets:
```
ASIA / XHKG / Hong Kong / HKEX / HKD / Equities / [0700.HK, 9988.HK]
ASIA / XTKS / Tokyo / JPX / JPY / Equities / [7203.T, 6758.T]
ASIA / XSES / Singapore / SGX / SGD / Equities / [D05.SI, O39.SI]
ASIA / XSHG / Shanghai / SSE / CNY / Equities / [600519.SS, 601318.SS]
EMEA / XLON / London / LSEG / GBP / Equities / [BP.L, VOD.L]
EMEA / XPAR / Paris / Euronext Paris / EUR / Equities / [AIR.PA, BNP.PA]
EMEA / XETR / Frankfurt / Xetra / EUR / Equities / [SAP.DE, SIE.DE]
EMEA / XSWX / Zurich / SIX / CHF / Equities / [NESN.SW, NOVN.SW]
AMERICAS / XNAS / New York / NASDAQ / USD / Equities / [AAPL, MSFT]
AMERICAS / XNYS / New York / NYSE / USD / Equities / [IBM, GS]
AMERICAS / XTSE / Toronto / TSX / CAD / Equities / [RY.TO, SHOP.TO]
AMERICAS / BVMF / Sao Paulo / B3 / BRL / Equities / [PETR4, VALE3]
```

Region labels map: `{ASIA→"Asia Pacific", EMEA→"Europe, Middle East & Africa", AMERICAS→"Americas"}`.

**Option definitions** (static final lists of inner record `OptionDefinition(String code, String label, String description)`):

Sides: BUY, SELL, SELL_SHORT (with descriptions)
Order types: MARKET, LIMIT, STOP, STOP_LIMIT, MARKET_ON_CLOSE, LIMIT_ON_CLOSE, PEGGED (with `requiresLimitPrice`, `requiresStopPrice` flags on inner `OrderTypeDefinition` record)
Price types: PER_UNIT, PERCENTAGE, FIXED_AMOUNT, YIELD, SPREAD
Time-in-force: DAY, IOC, FOK, GTC, GTD, OPG
Bulk modes: FIXED_RATE, BURST

**Instance state:**
* `String username`
* `HsbcFixClientConfig config`
* `HsbcSessionProfileStore profileStore`
* `HsbcMessageTemplateStore templateStore`
* `HsbcOrderStore orderStore`
* `LinkedHashMap<String, HsbcFixService> fixServices`
* `int pulseChecks`
* Static final `HsbcFixDictionaryCatalog FIX_DICTIONARY_CATALOG`

**Constructors** (four overloads allowing injection of stores for testing):
* Default: `HsbcWorkbenchState(HsbcFixClientConfig config)` — creates all stores in default locations
* Progressively more injectable overloads for testing

**Core snapshot method:**
`synchronized JsonObject snapshot(String selectedProfileName)` — the "overview" payload:
```json
{
  "applicationName": "HsbcFixClient",
  "subtitle": "Electronic execution workstation",
  "environment": "Live simulator-linked FIX order workstation",
  "generatedAt": "<ISO instant>",
  "session": { ... },
  "runtimeSessions": [ ... ],
  "kpis": { "pulseChecks": N, ... },
  "recentEvents": [ ... ],
  "recentOrders": [ ... ],
  "referenceData": { ... },
  "settings": { ... },
  "sessionProfiles": { ... },
  "defaults": { "defaultFlowRate": N, "bulkMode": "FIXED_RATE", "order": { ... } }
}
```

**`buildReferenceData()`** — builds:
```json
{
  "regions": [ { "code", "label", "markets": [ { "code", "mic", "city", "exchange", "currency", "assetClass", "symbols": [...] } ] } ],
  "sides": [...],
  "orderTypes": [...],
  "priceTypes": [...],
  "timeInForce": [...],
  "bulkModes": [...]
}
```

**Action methods** (all synchronized, accept `JsonObject request` with optional `profileName` key):
* `connect(JsonObject)` — guard session identity conflict; call `serviceForProfile(...).connect()`
* `disconnect(JsonObject)` — disconnect service if exists
* `pulseTest(JsonObject)` — increment `pulseChecks`, call service `pulseTest`
* `resetSequenceNumbers(JsonObject)` — call service; return snapshot with `actionResult`
* `sendOrder(JsonObject)` — build preview; if no warnings and service connected: `service.sendOrder(preview.toOrderRequest())`
* `amendBlotterOrder(JsonObject)` — parse clOrdId, quantity, price; call `service.amendOrder`; return snapshot with `actionResult`
* `cancelBlotterOrder(JsonObject)` — parse clOrdId; call `service.cancelOrder`; return snapshot with `actionResult`
* `startOrderFlow(JsonObject)` — build preview; build bulk options; call `service.startAutoFlow`
* `stopOrderFlow(JsonObject)` — call `service.stopAutoFlow`
* `saveSettingsProfile(JsonObject)` — delegate to profileStore
* `activateSettingsProfile(JsonObject)` — delegate; return snapshot
* `deleteSettingsProfile(JsonObject)` — guard against deleting profile with active non-idle service; delegate; close and remove the service
* `updateSettingsStoragePath(JsonObject)` — delegate
* `saveMessageTemplate(JsonObject)` — call `templateStore.saveManualTemplate`; return template snapshot with `savedTemplateId`
* `previewOrder(JsonObject)` — build `OrderPreview` from request fields; return `previewJson(loggedOn)`
* `fixMetadataSnapshot()` — return `FIX_DICTIONARY_CATALOG.snapshot()`
* `settingsSnapshot()` — `{settings: profileStore.workspaceSnapshot()}`
* `sessionProfilesSnapshot()` — `{sessionProfiles: profileStore.snapshot()}`
* `templateSnapshot(String profileName)` — `{templates: templateStore.snapshot(...)}`
* `recentFixMessages(int limit, int offset, String profileName)` — delegate to service

**`JsonObject runCucumber(JsonObject request)`** — non-synchronized; instantiates `HsbcScenarioRunner(this)` and calls `run(featureText, resolvedProfileName)`.

**Session identity conflict detection:**
* `rejectOrResolveSessionIdentityConflict(HsbcSessionProfile, String actionType)` — if any other *active* (non-idle) service has the same BeginString+SenderCompID+TargetCompID, disconnect and remove it if the selected profile is already running; otherwise return a conflict error JSON
* `sameSessionIdentity(left, right)` — comparison on the three fields

**`close()`** — close all fix services; clear map; close templateStore.

**`OrderPreview` inner class** (or record) — carries validated fields plus list of warnings; has `toOrderRequest()` and `toJson(boolean loggedOn)` methods.

**Private `serviceForProfile(String profileName)`** — `computeIfAbsent` in `fixServices`; load persisted orders from `orderStore` on first creation.

**`defaultOrderJson()`** — `JsonObject` with default draft field values matching sanitizeDraft defaults.

Write `HsbcWorkbenchStateTest` with tests for snapshot content, profile save/activate/delete lifecycle, and order preview building.

---

## Prompt 11 — Gherkin Scenario Runner

### Goal
Implement a lightweight, dependency-free Gherkin feature-file runner that executes scenarios directly against the live workbench state.

### Deliverables

**`HsbcScenarioRunner.java`** — package-private final class

No external Cucumber library.  The parser and step-definition matching are built-in.

**Constructor:** `HsbcScenarioRunner(HsbcWorkbenchState workbenchState)`

**`JsonObject run(String featureText, String profileName)`:**

Parse and execute the feature; return:
```json
{
  "status": "PASSED|FAILED|UNDEFINED",
  "featureName": "...",
  "totalScenarios": N,
  "passed": N,
  "failed": N,
  "skipped": N,
  "scenarios": [ { "name": "...", "status": "PASSED|FAILED|SKIPPED", "steps": [ ... ] } ]
}
```

**Parser:**
* Split on `\r?\n`
* Recognise: `Feature:`, `Background:`, `Scenario:`, `Scenario Outline:` / `Scenario Template:`, `Examples:` / `Scenarios:`, table rows (`|`-delimited)
* Step keywords: `Given `, `When `, `Then `, `And `, `But `
* Blank lines, `#` comments, `@` tags: skip
* Scenario Outline: expand each `Examples` table row by substituting `<placeholder>` patterns; name each expanded scenario as `"<template name> · example N"`

**Step executor:**
* Background steps run before each scenario's steps
* If a previous step FAILED, remaining steps are SKIPPED
* Each step returns `{keyword, text, status: PASSED|FAILED|SKIPPED, message}`

**Step definitions** (match by regex against step text after the keyword):

| Pattern | Action |
|---|---|
| `the FIX session is connected` | `workbench.connect(profile)` |
| `the FIX session is disconnected` | `workbench.disconnect(profile)` |
| `I send a New Order Single for (\d+) shares of "([^"]+)"` | NOS, market buy, DAY |
| `I send a New Order Single to buy (\d+) shares of "([^"]+)" at ([\d.]+)` | NOS, BUY, LIMIT |
| `I send a New Order Single to sell (\d+) shares of "([^"]+)" at ([\d.]+)` | NOS, SELL, LIMIT |
| `I send a New Order Single to sell short (\d+) shares of "([^"]+)" at ([\d.]+)` | NOS, SELL_SHORT, LIMIT |
| `I send a (\w+) order to buy (\d+) shares of "([^"]+)" at stop ([\d.]+)` | NOS, BUY, STOP (stop price) |
| `I send a (\w+) order to buy (\d+) shares of "([^"]+)" at limit ([\d.]+) stop ([\d.]+)` | NOS, BUY, STOP_LIMIT |
| `I send a New Order Single to buy (\d+) shares of "([^"]+)" as (\w+) order` | NOS, BUY, order type from group 3 |
| `I send a BUY New Order Single for (\d+) shares of "([^"]+)" at ([\d.]+) with TIF (\w+)` | NOS, BUY, LIMIT, TIF from group 4 |
| `I send a New Order Single for (\d+) shares of "([^"]+)" on market "([^"]+)"` | NOS, BUY, MARKET, SecurityExchange from group 3 |
| `I start a fixed rate bulk flow at (\d+) orders per second` | `startOrderFlow` FIXED_RATE, no total |
| `I start a fixed rate bulk flow of (\d+) total orders at (\d+) orders per second` | `startOrderFlow` FIXED_RATE with total |
| `I start a burst bulk flow with (\d+) orders per burst every (\d+) ms` | `startOrderFlow` BURST, no total |
| `I start a burst bulk flow of (\d+) total orders with (\d+) per burst every (\d+) ms` | `startOrderFlow` BURST with total |
| `I stop the bulk order flow` | `stopOrderFlow` |
| `I wait (\d+) seconds` | `Thread.sleep(N * 1000)` |
| `the bulk flow should be running` | assert `snapshot.session.autoFlowActive == true` |
| `the bulk flow should not be running` | assert `snapshot.session.autoFlowActive == false` |
| `the session should be connected` | assert `snapshot.session.connected == true` |
| `the sent orders count should be at least (\d+)` | assert `kpis.sentOrders >= N` |
| `the execution report count should be at least (\d+)` | assert `kpis.executionReports >= N` |
| `the send failure count should be (\d+)` | assert `kpis.sendFailures == N` |
| `the reject count should be at most (\d+)` | assert `kpis.rejects <= N` |
| `the order blotter should contain at least (\d+) order` | assert `recentOrders.size() >= N` |
| `the order blotter should contain an order with symbol "([^"]+)"` | assert any order in `recentOrders` has symbol matching group 1 |
| `the FIX tape should contain at least (\d+) message` | assert `recentFixMessages.total >= N` |

Any step that does not match a known definition: return FAILED with "Undefined step: <text>".

Use a `RunContext` inner record to carry the active `profileName` across steps.  Default symbol for generic NOS: `"AAPL"`, price: `100.25`.

Write `HsbcScenarioRunnerTest` covering the parser (Background, Scenario Outline expansion), step matching, and PASSED/FAILED/SKIPPED propagation.

---

## Prompt 12 — HTTP Server

### Goal
Implement the Vert.x HTTP server that wires all workbench APIs to REST endpoints and serves the Vue.js SPA.

### Deliverables

**`HsbcFixClientServer.java`** — package-private final class

Constructor `HsbcFixClientServer(HsbcFixClientConfig config)` — creates `UserSessionRegistry` with `HsbcDemoAuthModule`.
Constructor `HsbcFixClientServer(HsbcFixClientConfig config, UserSessionRegistry sessionRegistry)` — for tests.

Both constructors create a `Vertx` instance with `VertxOptions().setPreferNativeTransport(true)`.

**`void start()`:**

Build a Vert.x `Router`.  Apply `BodyHandler.create()` to all `/api/*` routes.

**Public endpoints (no auth):**

* `GET /api/health` → `{status:"UP", application:"HsbcFixClient", mode:"live-fix-workstation", port: N}`
* `POST /api/auth/login` → parse `{username, password}`; call `sessionRegistry.login(...)`; return `{token, username, displayName, role, expiresAt, sessionTimeoutMinutes}` or 400/401

**Auth middleware** (applied to all `/api/*` routes after the two public ones):
* Skip if path is `/api/health` or `/api/auth/login`
* Extract token from: `Authorization: Bearer <token>` header first; then `X-Auth-Token` header; then cookie named `hsbc-token`
* Validate with `sessionRegistry.validate(token)`; if empty: respond 401 `{error:"Authentication required"}`
* Store session in `ctx.put("userSession", session)` and call `ctx.next()`

**Authenticated auth endpoints:**
* `POST /api/auth/logout` → `sessionRegistry.logout(token)` → `{loggedOut: true}`
* `GET /api/auth/me` → `{username, displayName, role, expiresAt}`

**Authenticated workbench API:**

| Method | Path | Handler call |
|---|---|---|
| GET | `/api/overview` | `workbench.snapshot(req.getParam("profileName"))` |
| GET | `/api/fix-metadata` | `workbench.fixMetadataSnapshot()` |
| GET | `/api/settings` | `workbench.settingsSnapshot()` |
| GET | `/api/session-profiles` | `workbench.sessionProfilesSnapshot()` |
| GET | `/api/templates` | `workbench.templateSnapshot(req.getParam("profileName"))` |
| POST | `/api/session/connect` | `workbench.connect(body)` |
| POST | `/api/session/disconnect` | `workbench.disconnect(body)` |
| POST | `/api/session/pulse-test` | `workbench.pulseTest(body)` |
| POST | `/api/session/reset-sequence` | `workbench.resetSequenceNumbers(body)` |
| POST | `/api/settings/profiles/save` | `workbench.saveSettingsProfile(body)` |
| POST | `/api/settings/profiles/activate` | `workbench.activateSettingsProfile(body)` |
| POST | `/api/settings/profiles/delete` | `workbench.deleteSettingsProfile(body)` |
| POST | `/api/session-profiles/save` | `workbench.saveSettingsProfile(body)` |
| POST | `/api/session-profiles/activate` | `workbench.activateSettingsProfile(body)` |
| POST | `/api/session-profiles/delete` | `workbench.deleteSettingsProfile(body)` |
| POST | `/api/settings/storage-path` | `workbench.updateSettingsStoragePath(body)` |
| POST | `/api/templates/save` | `workbench.saveMessageTemplate(body)` |
| POST | `/api/order-ticket/preview` | `workbench.previewOrder(body)` |
| POST | `/api/order-ticket/send` | `workbench.sendOrder(body)` |
| POST | `/api/orders/amend` | `workbench.amendBlotterOrder(body)` |
| POST | `/api/orders/cancel` | `workbench.cancelBlotterOrder(body)` |
| POST | `/api/order-flow/start` | `workbench.startOrderFlow(body)` |
| POST | `/api/order-flow/stop` | `workbench.stopOrderFlow(body)` |
| POST | `/api/cucumber/run` | **blockingHandler** `workbench.runCucumber(body)` |
| GET | `/api/fix-messages` | `workbench.recentFixMessages(limit, offset, profileName)` where limit=max(1,min(parsed,100)) defaulting to 20, offset defaulting to 0 |

**SPA routes** — reroute to `/index.html` (HTTP 200):
`/home`, `/create`, `/neworder`, `/order`, `/orders`, `/blotter`, `/settings`, `/session-profiles`, `/sessionprofiles`, `/recentfixmsgs`, `/cucumber`, `/about`

Trailing-slash variants (`/home/` etc.) → HTTP 308 redirect to path without trailing slash.

**Static files:**
`StaticHandler.create("web").setCachingEnabled(false).setIndexPage("index.html")` for all remaining routes.

**HTTP server options:** host from `config.host()`, port from `config.port()`, `TcpNoDelay=true`, `ReusePort=true`.

Start with `.listen().toCompletionStage().toCompletableFuture().get(30, TimeUnit.SECONDS)`.

**`void stop()`** — close session registry (log WARN on error); close HTTP server (10s timeout); close Vert.x (10s timeout).

**`int actualPort()`** — returns `httpServer.actualPort()` when running, else `config.port()`.

**Helper statics:** `writeJson(response, payload)`, `bodyJson(ctx)`, `parseIntParam(value, defaultValue)`, `resolveToken(ctx)`.

Write `HsbcFixClientServerRoutingTest` as an integration test that:
* Starts a server on port 0
* Logs in as `trader1/trader1` and captures the token
* Verifies that all SPA routes return HTTP 200 with `text/html` content type
* Verifies that trailing-slash SPA routes return HTTP 308
* Verifies that `/api/health` is accessible without auth
* Verifies that protected endpoints return 401 without a token
* Verifies that protected endpoints return 200 with a valid token
* Stops the server in `@AfterEach`

---

## Prompt 13 — Application Entry Point

### Goal
Replace the placeholder entry point with the real `HsbcFixClientApplication` class.

### Deliverables

**`HsbcFixClientApplication.java`**

```java
public final class HsbcFixClientApplication {
    private static final Logger log = LoggerFactory.getLogger(HsbcFixClientApplication.class);

    private HsbcFixClientApplication() { }

    public static void main(String[] args) {
        HsbcFixClientConfig config = HsbcFixClientConfig.fromSystemProperties();
        HsbcFixClientServer server = new HsbcFixClientServer(config);

        Runtime.getRuntime().addShutdownHook(
            Thread.ofPlatform().name("hsbcfixclient-shutdown").unstarted(server::stop)
        );

        server.start();
        log.info(startupMessage(config));
    }

    static String startupMessage(HsbcFixClientConfig config) {
        return "HsbcFixClient trader workstation is available at " + config.localUrl();
    }
}
```

Write `HsbcFixClientApplicationTest` that verifies `startupMessage` returns a string containing the port.

Run `./gradlew clean build` and confirm everything compiles and all tests pass.

---

## Prompt 14 — Frontend: Vue.js SPA

### Goal
Implement the single-page application — the HTML shell and the full Vue 3 JavaScript application — served from `src/main/resources/web/`.

### Deliverables

**`src/main/resources/web/index.html`**

Full HTML shell including:
* `<title>HsbcFixClient — Trader Workstation</title>`
* `<base href="/">` for SPA routing
* Google Fonts import: Inter (300/400/500/600/700) and JetBrains Mono (400/500)
* Four complete CSS themes using CSS custom properties:

  | Theme | `body` selector | Description |
  |---|---|---|
  | Light (default) | `:root` | `--bg: #f4f2ef`, `--brand: #d71920` (HSBC red) |
  | Light 2 | `body[data-theme='light2']` | Warmer ivory tones |
  | Dark | `body[data-theme='dark']` | Navy/slate dark — green accent |
  | HSBC Dark | `body[data-theme='hsbc-dark']` | Dark navy — HSBC red accent |

* CSS variables: `--bg`, `--bg-soft`, `--surface`, `--surface-strong`, `--surface-muted`, `--border`, `--border-strong`, `--text`, `--text-soft`, `--text-faint`, `--brand`, `--brand-strong`, `--brand-soft`, `--danger`, `--danger-soft`, `--success`, `--success-soft`, `--warn`, `--warn-soft`, `--info`, `--info-soft`, `--shadow`, `--radius-xl/lg/md/sm`
* Full application layout CSS: top navigation bar, left sidebar for profile/session rail, main content area, responsive typography
* `<div id="app"></div>` mount point
* `<script type="module" src="app.js"></script>`

**`src/main/resources/web/app.js`**

Vue 3 SPA imported from CDN: `https://unpkg.com/vue@3/dist/vue.esm-browser.prod.js`

**Application state (reactive):**
* Auth: `token`, `username`, `displayName`, `role`, `sessionExpiresAt`
* UI: `theme`, `currentPage`, `refreshSeconds`, `loading`, `error`
* Data: `overview`, `fixMetadata`, `sessionProfiles`, `templates`, `fixMessages`
* Forms: `orderDraft`, `profileDraft`, `featureText`
* Misc: `actionResult`, `cucumberResult`, `pulseChecks`

**Theme management:**
* Persist in `localStorage` key `hsbcfixclient-theme`
* Options: `system`, `dark`, `light`, `light2`, `hsbc-dark`
* Apply by setting `document.body.dataset.theme` (or removing the attribute for `system`)
* Labels: System, Dark - Green, Light, Light2, Dark - RED

**Refresh frequency:**
* Persist in `localStorage` key `hsbcfixclient-refresh-frequency`
* Options: 1, 3, 5 seconds; default 3
* Auto-poll `/api/overview` at the configured frequency when logged in

**Pages** (rendered inside the main content area via `v-if`/`v-show`):

1. **Order Input** (`/create`, `/neworder`, `/order`) — complete order ticket:
   * Message type selector (NOS / Amend / Cancel)
   * Region + market picker (grouped by region) with currency auto-fill
   * Symbol input
   * Side selector (BUY/SELL/SELL_SHORT)
   * Quantity input
   * Order type selector; shows price/stop-price inputs conditionally
   * Time-in-force selector
   * Price type selector
   * Additional tags table (tag, name, value columns; add/remove rows)
   * Preview button → calls `/api/order-ticket/preview` and shows preview panel
   * Send button → calls `/api/order-ticket/send`
   * Bulk flow panel: mode (FIXED_RATE/BURST), rate/burst-size/burst-interval/total-orders inputs; Start/Stop buttons

2. **Order Blotter** (`/orders`, `/blotter`) — table of recent orders with:
   * Columns: ClOrdID, Symbol, Side, Qty, Price, Order Type, TIF, Status, Actions
   * Amend modal (editable qty + price)
   * Cancel button (with confirmation)
   * Color-coded status badges

3. **FIX In/Out** (`/recentfixmsgs`) — FIX tape:
   * Direction badge (SENT/RECEIVED), channel badge (ADMIN/APPLICATION)
   * MsgType label, preview, timestamp
   * Pagination (limit/offset)

4. **Session Profiles** (`/session-profiles`) — profile management:
   * List of profiles with active indicator
   * Edit form: name, SenderCompID, TargetCompID, FIX host, FIX port, FIX version, resetOnLogon, sessionStart/End time, heartbeat, reconnect interval, raw logging toggle, log dir
   * Save, Activate, Delete actions

5. **Scenario Runner** (`/cucumber`) — feature editor:
   * `<textarea>` for Gherkin feature text, pre-populated with sample steps
   * Run button → calls `/api/cucumber/run` (blockingHandler)
   * Results panel: overall status badge, scenario list with pass/fail/skip counts, step-level details with status icons

6. **Settings** (`/settings`) — workspace preferences:
   * Storage path display + update form
   * Profile count
   * Theme picker
   * Refresh frequency picker

7. **About** (`/about`) — component descriptions and capability list, HSBC-branded

**Top navigation bar:**
* Application name "HsbcFixClient"
* Links for each page
* Current user display (username, role)
* Logout button

**Session control rail** (right side panel or top bar section):
* Active profile selector (calls `/api/session-profiles/activate`)
* Connect / Disconnect buttons
* Pulse Test button
* Reset Sequence Numbers button
* Session status badge (Connected/Connecting/Standby)
* KPI mini-dashboard: open orders, sent, fills, cancels, rejects
* Bulk flow status indicator

**Login page** (shown when not authenticated):
* Username + password form
* Submit → `POST /api/auth/login` → store token in `localStorage` and in-memory
* Error display for 401/400

**API helpers:**
* All calls include `Authorization: Bearer <token>` header
* On 401: clear token, show login page
* Loading state management

**Include `src/main/resources/web/sample.feature`** with at least 10 representative Gherkin scenarios demonstrating all supported step definitions (connect, send NOS with various types and TIF values, bulk flow, assertions).

---

## Prompt 15 — Deployment Scripts

### Goal
Create all operational scripts for direct-JVM and Docker deployment.

### Deliverables

Place all scripts in a `scripts/` directory at the project root.  All scripts must be `#!/usr/bin/env bash` with `set -euo pipefail`.

**`scripts/common.sh`** — sourced by other scripts; set `MYFIX_HSBC_COMMON_SOURCED` guard to prevent double-sourcing

Constants:
```
SCRIPT_DIR, PROJECT_ROOT, GRADLEW_BIN
RUNTIME_DIR=<PROJECT_ROOT>/build/hsbcfixclient
PID_DIR=<RUNTIME_DIR>/pids
LOG_DIR=<RUNTIME_DIR>/logs
CLIENT_LOG_FILE=<LOG_DIR>/hsbcfixclient.log
CLIENT_PID_FILE=<PID_DIR>/hsbcfixclient.pid
CLIENT_RUNTIME_DIR=<RUNTIME_DIR>/hsbcfixclient
WEB_STACK_CLIENT_PORT="${WEB_STACK_CLIENT_PORT:-8081}"
WEB_STACK_FIX_PORT="${WEB_STACK_FIX_PORT:-9880}"
```

Color variables: `RED`, `GREEN`, `YELLOW`, `CYAN`, `BOLD`, `RESET`.

Functions:
* `info`, `success`, `warn`, `error`, `banner` — colour-coded output helpers
* `require_java` — check `java` in PATH
* `require_gradle_wrapper` — check gradlew is executable
* `require_docker` — check docker in PATH and daemon running
* `ensure_runtime_dirs` — `mkdir -p PID_DIR LOG_DIR CLIENT_RUNTIME_DIR`
* `read_pid_file <file>` — cat with whitespace stripped
* `is_pid_running <pid>` — `kill -0 <pid> 2>/dev/null`
* `port_listener_pid <port>` — `lsof -ti tcp:<port> -sTCP:LISTEN 2>/dev/null | head -n 1`
* `port_in_use <port>` — non-empty `port_listener_pid`
* `http_healthy <url>` — `curl -sf <url>`
* `wait_for_http <label> <url> <max_wait=60>` — poll until healthy with 2s intervals; print `.` progress; error on timeout
* `wait_for_pid_exit <pid> <max_wait=15>` — poll until process exits
* `stop_pidfile_process <label> <pid_file>` — SIGTERM; wait; SIGKILL if needed; remove pid file
* `stop_listener_on_port <label> <port>` — find listener; SIGTERM; SIGKILL if needed

**`scripts/native_runtime_targets.sh`** — sourced helper for reading `config/target-hsbcfixclient.properties`

Functions:
* `native_runtime_target_property <file> <key> <default>` — uses Python3 to parse Java `.properties` format
* `native_runtime_targets_load` — exports `HSBCCLIENT_TARGET_HEAP_XMS`, `HSBCCLIENT_TARGET_HEAP_XMX`, `HSBCCLIENT_TARGET_CPU_PINNING` from the properties file

**`scripts/start_hsbc_fix_client.sh`** — direct JVM start

Options (with defaults):
* `--port PORT` — HTTP port (default 8081)
* `--fix-host HOST` — FIX acceptor host (default 127.0.0.1)
* `--fix-port PORT` — FIX acceptor port (default 9880)
* `--heap-xms SIZE` — JVM initial heap (default 256m, overridable by target profile)
* `--heap-xmx SIZE` — JVM max heap (default 512m, overridable by target profile)
* `--cpu-pinning CPUS` — taskset CPU affinity (optional)
* `--no-build` — skip Gradle build
* `--raw-logging` — enable raw FIX message file logging
* `-h`, `--help` — show help

Behaviour:
1. Source `common.sh` and `native_runtime_targets.sh`
2. Call `require_java`, `require_gradle_wrapper`
3. Load target profile via `native_runtime_targets_load`
4. Check if already running (by PID file); exit 0 if so
5. Check port availability; exit 1 if in use
6. Call `ensure_runtime_dirs`
7. Build: `./gradlew --no-daemon :installDist -x test` (unless `--no-build`)
8. Launch: `nohup env JAVA_OPTS="-Xms<xms> -Xmx<xmx>" HSBC_CLIENT_PORT=<port> HSBC_FIX_HOST=<host> HSBC_FIX_PORT=<fixport> HSBC_FIX_LOG_DIR=<CLIENT_RUNTIME_DIR>/quickfixj HSBC_FIX_RAW_LOGGING_ENABLED=<raw> ./bin/HsbcFixClient > <CLIENT_LOG_FILE> 2>&1 &`
   * Wrap with `taskset -c <CPU_PINNING>` if cpu pinning is set and `taskset` is available
   * Write PID to `CLIENT_PID_FILE`
9. Call `wait_for_http "HsbcFixClient" "http://localhost:<port>/api/health" 90`
10. On success: print Web UI URL and log file path

Environment variables respected:
* `WEB_STACK_CLIENT_PORT`, `WEB_STACK_FIX_PORT`
* `HSBCCLIENT_JAVA_XMS`, `HSBCCLIENT_JAVA_XMX`
* `HSBC_FIX_RAW_LOGGING_ENABLED`

**`scripts/stop_hsbc_fix_client.sh`** — direct JVM stop

Options: `--port PORT` (default 8081), `-h/--help`

1. Source `common.sh`
2. `stop_pidfile_process "HsbcFixClient" "<CLIENT_PID_FILE>"`
3. `stop_listener_on_port "HsbcFixClient" "<CLIENT_PORT>"`

**`scripts/start_hsbc_fix_client_docker.sh`** — Docker start

Options:
* `--port PORT` — host port mapping (default 8081)
* `--fix-host HOST` — FIX host (default 127.0.0.1)
* `--fix-port PORT` — FIX port (default 9880)
* `--heap-xms SIZE`, `--heap-xmx SIZE`
* `--log-dir DIR` — host path for log volume (default `./logs`)
* `--network-mode MODE` — `bridge|host|none` (NAT treated as bridge; default bridge)
* `--container-name NAME` — Docker container name (default `hsbcfixclient`)
* `--no-build` — skip Gradle build and Docker image build
* `--raw-logging`
* `-h/--help`

Behaviour:
1. Source `common.sh`; call `require_docker`, `require_gradle_wrapper`
2. Normalise network mode (lowercase; NAT→bridge); validate
3. Check if container is already running; exit 0 if so
4. Check port availability (bridge mode only)
5. Unless `--no-build`:
   * `./gradlew --no-daemon :installDist -x test`
   * `docker build -t hsbcfixclient:1.0-SNAPSHOT .`
6. Remove stopped container with the same name if it exists
7. Assemble `docker run` args:
   * `--detach --name <name> --restart unless-stopped`
   * `--volume <log-dir>:/app/logs`
   * `--env HSBC_CLIENT_PORT=<port>`
   * `--env HSBC_FIX_HOST=<fix-host>`
   * `--env HSBC_FIX_PORT=<fix-port>`
   * `--env HSBC_FIX_LOG_DIR=/app/logs/hsbcfixclient/quickfixj`
   * `--env HSBC_FIX_RAW_LOGGING_ENABLED=<raw>`
   * `--env "JAVA_OPTS=-Xms<xms> -Xmx<xmx>"`
   * Health check: `curl -sf http://localhost:<port>/api/health`; interval 30s, timeout 5s, retries 3, start_period 20s
   * Bridge mode: `--publish <port>:<port>`; host mode: `--network host`; none: `--network none`
8. `docker run "<args>" hsbcfixclient:1.0-SNAPSHOT`
9. `wait_for_http "HsbcFixClient" "http://localhost:<port>/api/health" 90`

**`scripts/stop_hsbc_fix_client_docker.sh`** — Docker stop

Options: `--container-name NAME` (default `hsbcfixclient`), `--timeout SECONDS` (default 15), `-h/--help`

1. Source `common.sh`; call `require_docker`
2. If container not found: warn and exit 0
3. `docker stop --time <timeout> <name>`
4. `docker rm <name>`

**`Dockerfile`** (at project root):

```dockerfile
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

RUN apt-get update && \
    apt-get install -y --no-install-recommends curl && \
    rm -rf /var/lib/apt/lists/*

COPY build/install/HsbcFixClient/ /app/

RUN chmod +x /app/bin/HsbcFixClient && \
    mkdir -p /app/logs/hsbcfixclient/quickfixj

EXPOSE 8081

CMD ["sh", "-c", "exec /app/bin/HsbcFixClient"]
```

Make all `.sh` scripts executable (`chmod +x`).

After creating the scripts, run `./gradlew clean build` to confirm the project still builds and all tests pass.

---

*End of HsbcFixClient Rewrite Prompts*
