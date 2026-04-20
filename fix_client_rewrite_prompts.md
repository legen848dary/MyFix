# HsbcFixClient Rewrite Prompts

> **How to use these prompts**
> These prompts are designed to be executed **sequentially** by a coding agent in this
> repository (legen848dary/MyFix). Each prompt is self-contained and builds on the
> artefacts produced by the previous one. After completing every prompt the agent **must
> pause and ask the operator** "Prompt N is complete — please confirm to proceed with
> Prompt N+1." before continuing.
>
> The prompts rewrite the existing `TheFixClient` Gradle module as a brand-new module
> called **`HsbcFixClient`** using the package `com.hsbc.fix.client`. No simulator code
> is changed. All class, variable, thread, cookie, property and environment-variable
> names are updated consistently (see the name-mapping table below).
>
> **Run the full build (`./gradlew --no-daemon clean build test`) and ensure it is GREEN
> before beginning, and again after every prompt.**

---

## Global Name-Mapping Reference

| Old name (TheFixClient) | New name (HsbcFixClient) |
|---|---|
| Module dir `TheFixClient/` | `HsbcFixClient/` |
| Gradle module `:TheFixClient` | `:HsbcFixClient` |
| Package `com.insoftu.thefix.client` | `com.hsbc.fix.client` |
| `TheFixClientApplication` | `HsbcFixClientApplication` |
| `TheFixClientConfig` | `HsbcFixClientConfig` |
| `TheFixClientServer` | `HsbcFixClientServer` |
| `TheFixClientWorkbenchState` | `HsbcFixClientWorkbenchState` |
| `TheFixClientFixService` | `HsbcFixClientFixService` |
| `TheFixSessionProfile` | `HsbcFixSessionProfile` |
| `TheFixSessionProfileStore` | `HsbcFixSessionProfileStore` |
| `TheFixOrderStore` | `HsbcFixOrderStore` |
| `TheFixMessageTemplate` | `HsbcFixMessageTemplate` |
| `TheFixMessageTemplateStore` | `HsbcFixMessageTemplateStore` |
| `TheFixOrderRequest` | `HsbcFixOrderRequest` |
| `TheFixTagEntry` | `HsbcFixTagEntry` |
| `TheFixMessageType` | `HsbcFixMessageType` |
| `TheFixFixVersion` | `HsbcFixVersion` |
| `TheFixFixDictionaryCatalog` | `HsbcFixDictionaryCatalog` |
| `TheFixBulkOptions` | `HsbcFixBulkOptions` |
| `TheFixCucumberRunner` | `HsbcFixCucumberRunner` |
| `DemoAuthModule` | `HsbcDemoAuthModule` |
| `AuthModule` | `AuthModule` (unchanged — public interface) |
| `AuthenticatedUser` | `AuthenticatedUser` (unchanged — public record) |
| `UserSession` | `UserSession` (unchanged) |
| `UserSessionRegistry` | `UserSessionRegistry` (unchanged) |
| Main class `com.insoftu.thefix.client.TheFixClientApplication` | `com.hsbc.fix.client.HsbcFixClientApplication` |
| HTTP port default `8081` | `8081` (unchanged) |
| Auth cookie `thefix-token` | `hsbc-token` |
| Auth header `X-Auth-Token` | `X-Auth-Token` (unchanged) |
| Property prefix `thefix.client.*` / `thefix.fix.*` | `hsbc.client.*` / `hsbc.fix.*` |
| Env-var prefix `THEFIX_CLIENT_*` / `THEFIX_FIX_*` | `HSBC_CLIENT_*` / `HSBC_FIX_*` |
| Legacy property fallbacks `fix.demo.*` | Remove — no legacy fallbacks needed |
| Log dir `logs/thefixclient/quickfixj` | `logs/hsbcfixclient/quickfixj` |
| DB basename `thefixclient-orders` | `hsbcfixclient-orders` |
| DB basename `thefixclient-message-templates` | `hsbcfixclient-message-templates` |
| Thread name prefix `thefixclient-` | `hsbcfixclient-` |
| Docker image `thefixclient:1.0-SNAPSHOT` | `hsbcfixclient:1.0-SNAPSHOT` |
| Docker container name `thefixclient` | `hsbcfixclient` |
| Compose service name `thefixclient` | `hsbcfixclient` |
| PID file `thefixclient.pid` | `hsbcfixclient.pid` |
| Log file `thefixclient.log` | `hsbcfixclient.log` |
| Runtime dir `thefixclient/` | `hsbcfixclient/` |
| Script display string `TheFixClient` | `HsbcFixClient` |

---

## Prompt 1 — Gradle Module Scaffold

**Context:** You are working on the `legen848dary/MyFix` monorepo. The existing FIX client module is called `TheFixClient`. Your task is to create a brand-new Gradle module called `HsbcFixClient` that is a complete rewrite of `TheFixClient`. Do **not** modify `TheFixSimulator` or any simulator source code. After finishing this prompt ask the operator to confirm before proceeding.

**Task:**

1. Create the directory tree for the new module at `HsbcFixClient/` in the repository root:
   ```
   HsbcFixClient/
   ├── build.gradle.kts
   ├── config/
   │   └── target-hsbcfixclient.properties
   ├── Dockerfile
   └── src/
       ├── main/
       │   ├── java/com/hsbc/fix/client/         (empty — populated by later prompts)
       │   └── resources/
       │       └── web/
       │           └── index.html                (copy from TheFixClient/src/main/resources/web/index.html if it exists, otherwise create a placeholder)
       └── test/
           └── java/com/hsbc/fix/client/         (empty — populated by later prompts)
   ```

2. Write `HsbcFixClient/build.gradle.kts` as a faithful translation of `TheFixClient/build.gradle.kts`:
   - `application` plugin, Java 21 toolchain.
   - Same dependency block (Vert.x 5, SLF4J, Log4j2, H2, QuickFIX/J 3.x for FIX42/44/50/50SP2, JUnit 5 for tests).
   - Keep `implementation(project(":TheFixSimulator"))` — `HsbcFixClient` reuses the simulator's `FixDemoClientConfig` and `NoOpQuickFixLogFactory` classes exactly as `TheFixClient` does.
   - `mainClass = "com.hsbc.fix.client.HsbcFixClientApplication"`.
   - Keep the same `applicationDefaultJvmArgs` (`--add-exports`, `--add-opens`) and `tasks.test` JVM args.

3. Register the new module in `settings.gradle.kts` by adding `include("HsbcFixClient")` (keep `:TheFixClient` included — both modules coexist).

4. Write `HsbcFixClient/config/target-hsbcfixclient.properties`:
   ```
   # Native runtime target profile for HsbcFixClient
   #
   # java.heap.min and java.heap.max map to JVM -Xms / -Xmx for the client launcher.
   # cpu.pinning is optional. Leave blank to disable pinning. Examples: 0, 1-2, 0,2-3

   java.heap.min=256m
   java.heap.max=512m
   cpu.pinning=
   ```

5. Write `HsbcFixClient/Dockerfile` translated from `TheFixClient/Dockerfile`:
   - Base image: `eclipse-temurin:21-jre-jammy`.
   - `COPY build/install/HsbcFixClient/ /app/`.
   - `RUN chmod +x /app/bin/HsbcFixClient && mkdir -p /app/logs/hsbcfixclient/quickfixj`.
   - `EXPOSE 8081`.
   - `CMD ["sh", "-c", "exec /app/bin/HsbcFixClient"]`.

6. Verify the scaffold compiles cleanly:
   ```bash
   ./gradlew --no-daemon :HsbcFixClient:compileJava
   ```
   (It will succeed trivially because there are no Java sources yet — that is expected at this stage.)

**Done?** Commit all new files. Then ask: "Prompt 1 is complete — please confirm to proceed with Prompt 2."

---

## Prompt 2 — Core Domain Model Types

**Context:** You are continuing the sequential rewrite of `TheFixClient` as `HsbcFixClient`. Prompt 1 created the Gradle module scaffold. Now create the six small, pure-Java domain model types in `src/main/java/com/hsbc/fix/client/`. Each is a direct translation of its `TheFixClient` counterpart with all names updated per the global mapping table. Do **not** modify any simulator code. After finishing ask the operator to confirm before proceeding.

**Task:** Create the following six source files, faithfully translating every method and every line of logic from the original, applying all name substitutions from the mapping table:

### 2.1 `HsbcFixBulkOptions.java`
Translate `TheFixBulkOptions` record. Fields: `mode`, `ratePerSecond`, `burstSize`, `burstIntervalMs`, `totalOrders`. Methods: `normalized(int defaultRatePerSecond)`, `isBurstMode()`, `describe()`. Logic is identical to the original.

### 2.2 `HsbcFixTagEntry.java`
Translate `TheFixTagEntry` record. Fields: `tag`, `name`, `value`, `custom`. Methods: `fromJsonArray(JsonArray)`, `toJson()`, `hasValue()`, private `parseInt`. Logic is identical to the original.

### 2.3 `HsbcFixMessageType.java`
Translate `TheFixMessageType` enum. Constants: `NEW_ORDER_SINGLE`, `ORDER_CANCEL_REPLACE_REQUEST`, `ORDER_CANCEL_REQUEST` — same codes, labels, msgType chars, flags. Methods: `code()`, `label()`, `shortLabel()`, `msgType()`, `requiresOrigClOrdId()`, `supportsBulk()`, `toJson()`, `fromCode(String)`, `options()`. Logic is identical to the original.

### 2.4 `HsbcFixVersion.java`
Translate `TheFixFixVersion` enum. Constants: `FIX_42`, `FIX_44`, `FIX_50`, `FIX_52` — same beginString and defaultApplVerId values. Methods: `code()`, `label()`, `beginString()`, `defaultApplVerId()`, `dictionaryResource()`, `toJson()`, `fromCode(String)`, `fromBeginString(String, String)`, `options()`. Logic is identical to the original. **Note:** class is renamed `HsbcFixVersion`; all internal references to the old enum name are updated.

### 2.5 `HsbcFixMessageTemplate.java`
Translate `TheFixMessageTemplate` record. Fields: `id`, `profileName`, `name`, `messageType`, `draft`, `autoSaved`, `updatedAt`. Method: `toJson()`. Logic is identical to the original.

### 2.6 `HsbcFixOrderRequest.java`
Translate `TheFixOrderRequest` record. Fields identical to original. All internal references to `TheFixMessageType` → `HsbcFixMessageType`, `TheFixTagEntry` → `HsbcFixTagEntry`. Methods: `bulkVariant(long)`, `summary()`, `messageType()`, `outboundClOrdIdOr(String)`, `fixSide()`, `fixTimeInForce()`, `fixOrdType()`, `fixPriceType()`, `requiresLimitPrice()`, `requiresStopPrice()`, and all private helpers. Logic is identical to the original.

**Verification:**
```bash
./gradlew --no-daemon :HsbcFixClient:compileJava
```
Must be GREEN (no compilation errors).

**Done?** Commit all new files. Then ask: "Prompt 2 is complete — please confirm to proceed with Prompt 3."

---

## Prompt 3 — Persistence Layer

**Context:** You are continuing the sequential rewrite of `TheFixClient` as `HsbcFixClient`. Prompts 1–2 created the module scaffold and core domain models. Now create the two H2-backed persistence stores. Do **not** modify any simulator code. After finishing ask the operator to confirm before proceeding.

**Task:** Create the following two source files in `src/main/java/com/hsbc/fix/client/`.

### 3.1 `HsbcFixOrderStore.java`
Translate `TheFixOrderStore`. Key changes:
- Class name: `HsbcFixOrderStore`.
- Constructor accepting `HsbcFixClientConfig` uses config field names from Prompt 8 (written now as forward references; the config record will supply `quickFixLogDir()` and `orderDataRetentionDays()` — keep the same accessor names as in the original).
- DB file basename constant: `"hsbcfixclient-orders"`.
- All Javadoc updated to reference `HsbcFixClientFixService` instead of `TheFixClientFixService`.
- Method signatures, SQL, H2 JDBC URL pattern, schema DDL, `persist`, `load`, `purgeExpired`, `close`, and all private helpers are **identical** to the original — only type and class-name references are updated.
- Implements `AutoCloseable`.

### 3.2 `HsbcFixMessageTemplateStore.java`
Translate `TheFixMessageTemplateStore`. Key changes:
- Class name: `HsbcFixMessageTemplateStore`.
- DB file basename constant: `"hsbcfixclient-message-templates"`.
- Constructor accepting `HsbcFixClientConfig` keeps the same accessor `config.quickFixLogDir()`.
- Internal references to `TheFixMessageType` → `HsbcFixMessageType`, `TheFixSessionProfile.DEFAULT_PROFILE_NAME` → `HsbcFixSessionProfile.DEFAULT_PROFILE_NAME`, `TheFixMessageTemplate` → `HsbcFixMessageTemplate`.
- Method signatures, SQL, H2 URL pattern, schema DDL, `snapshot`, `saveManualTemplate`, `autoSaveTemplate`, `upsertTemplate`, `findByName`, `initializeSchema`, `openConnection`, `readTemplate`, `sanitizeDraft`, `sanitizeProfile`, and all private helpers are **identical** — only type references are updated.
- Implements `AutoCloseable`.

**Verification:**
```bash
./gradlew --no-daemon :HsbcFixClient:compileJava
```
Must be GREEN.

**Done?** Commit all new files. Then ask: "Prompt 3 is complete — please confirm to proceed with Prompt 4."

---

## Prompt 4 — Session Profile Management

**Context:** You are continuing the sequential rewrite of `TheFixClient` as `HsbcFixClient`. Prompts 1–3 are done. Now create the session profile value type and its file-backed store. Do **not** modify any simulator code. After finishing ask the operator to confirm before proceeding.

**Task:** Create the following two source files in `src/main/java/com/hsbc/fix/client/`.

### 4.1 `HsbcFixSessionProfile.java`
Translate `TheFixSessionProfile` record. Key changes:
- Class name: `HsbcFixSessionProfile`.
- `DEFAULT_PROFILE_NAME = "Default profile"` (unchanged).
- Static factory `defaultProfile(HsbcFixClientConfig config)` — same field mapping, references `HsbcFixVersion.fromBeginString(...)`.
- Static factory `fromJson(JsonObject json, HsbcFixClientConfig config)` — same parsing logic, references `HsbcFixVersion.fromCode(...)`.
- Methods: `fixVersion()` → `HsbcFixVersion`, `beginString()`, `defaultApplVerId()`, `storeDir()`, `rawLogDir()`, `toJson()`, plus private helpers `sanitizeText`, `parsePort`, `parsePositiveInt`, `sanitizeSessionTime`.
- All internal references updated: `TheFixFixVersion` → `HsbcFixVersion`, `TheFixClientConfig` → `HsbcFixClientConfig`.
- Logic is identical to the original.

### 4.2 `HsbcFixSessionProfileStore.java`
Translate `TheFixSessionProfileStore`. Key changes:
- Class name: `HsbcFixSessionProfileStore`.
- All internal references updated: `TheFixClientConfig` → `HsbcFixClientConfig`, `TheFixSessionProfile` → `HsbcFixSessionProfile`, `TheFixFixVersion` → `HsbcFixVersion`.
- Methods: `activeProfile()`, `profile(String)`, `snapshot()`, `workspaceSnapshot()`, `saveProfile(JsonObject)`, `activateProfile(String)`, `deleteProfile(String)`, `updateStoragePath(String)` — logic identical.
- Private helpers: `loadOrInitialize`, `seedDefaults`, `persistQuietly`, `writeStore`, `storeFile`, `normalizeName` — logic identical.
- Store file name constant `"profiles.json"` unchanged.

**Verification:**
```bash
./gradlew --no-daemon :HsbcFixClient:compileJava
```
Must be GREEN.

**Done?** Commit all new files. Then ask: "Prompt 4 is complete — please confirm to proceed with Prompt 5."

---

## Prompt 5 — FIX Dictionary Catalog and FIX Engine Service

**Context:** You are continuing the sequential rewrite of `TheFixClient` as `HsbcFixClient`. Prompts 1–4 are done. Now create the FIX dictionary catalog and the core QuickFIX/J initiator service — the two largest and most complex classes in the module. Do **not** modify any simulator code. After finishing ask the operator to confirm before proceeding.

**Task:** Create the following two source files in `src/main/java/com/hsbc/fix/client/`.

### 5.1 `HsbcFixDictionaryCatalog.java`
Translate `TheFixFixDictionaryCatalog`. Key changes:
- Class name: `HsbcFixDictionaryCatalog`.
- All internal references updated: `TheFixFixVersion` → `HsbcFixVersion`, `TheFixMessageType` → `HsbcFixMessageType`.
- The XML parsing logic (`buildSnapshot`, `loadVersion`, `parseFields`, `parseComponents`, `parseMessages`, `collectFieldNames`, `firstElement`, `parseInt`) is **identical** to the original.
- Security-hardened XML parsing is preserved: `setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)` and `setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)`.

### 5.2 `HsbcFixClientFixService.java`
Translate `TheFixClientFixService`. This is the most complex class — translate every line with care. Key changes:
- Class name: `HsbcFixClientFixService`. Implements `Application`, `AutoCloseable`.
- Package: `com.hsbc.fix.client`.
- All internal class references updated per the mapping table: `TheFixClientConfig` → `HsbcFixClientConfig`, `TheFixSessionProfile` → `HsbcFixSessionProfile`, `TheFixOrderStore` → `HsbcFixOrderStore`, `TheFixOrderRequest` → `HsbcFixOrderRequest`, `TheFixBulkOptions` → `HsbcFixBulkOptions`, `TheFixMessageType` → `HsbcFixMessageType`, `TheFixTagEntry` → `HsbcFixTagEntry`.
- Thread names: `"thefixclient-auto-flow"` → `"hsbcfixclient-auto-flow"`, `"thefixclient-persist"` → `"hsbcfixclient-persist"`.
- Log messages that mention "TheFixClient" updated to "HsbcFixClient".
- All other logic — QuickFIX/J session wiring, `connect()`, `disconnect()`, `startAutoFlow()`, `stopAutoFlow()`, `sendOrder()`, `amendOrder()`, `cancelOrder()`, `resetSequenceNumbers()`, `onLogon()`, `onLogout()`, `fromApp()`, `toApp()`, `fromAdmin()`, `toAdmin()`, `onMessage()` callbacks, order blotter management (`OrderView`, `EventItem`, `FixMessageRecord` inner types), `kpiSnapshot()`, `sessionSnapshot()`, `recentEventsJson()`, `recentOrdersJson()`, `recentFixMessages(int, int)`, `isLoggedOn()`, `isIdle()`, `buildSessionSettings()`, `ensureQuickFixDirectories()`, `loadPersistedOrders()`, `persistOrders()`, and all private helpers — must be **identical** to the original.
- The `ActionOutcome` inner record is preserved.
- The `NoOpQuickFixLogFactory` import from `com.llexsimulator.client` is kept unchanged (it is a simulator class reused by the client).

**Verification:**
```bash
./gradlew --no-daemon :HsbcFixClient:compileJava
```
Must be GREEN.

**Done?** Commit all new files. Then ask: "Prompt 5 is complete — please confirm to proceed with Prompt 6."

---

## Prompt 6 — Authentication and Session Management

**Context:** You are continuing the sequential rewrite of `TheFixClient` as `HsbcFixClient`. Prompts 1–5 are done. Now create the authentication and session management layer. These classes are the security-critical components. Do **not** modify any simulator code. After finishing ask the operator to confirm before proceeding.

**Task:** Create the following five source files in `src/main/java/com/hsbc/fix/client/`.

### 6.1 `AuthModule.java` (public interface — name unchanged)
Translate verbatim from `TheFixClient`. The interface is public and its name stays `AuthModule`. Update only the package declaration to `com.hsbc.fix.client` and the Javadoc example to reference `UserSessionRegistry`.

### 6.2 `AuthenticatedUser.java` (public record — name unchanged)
Translate verbatim. Public record with canonical constructor validation (username, displayName, role not blank; username normalised to lower-case). Package: `com.hsbc.fix.client`.

### 6.3 `HsbcDemoAuthModule.java`
Translate `DemoAuthModule`. Key changes:
- Class name: `HsbcDemoAuthModule` (public final).
- Implements `AuthModule`.
- Preconfigured demo accounts remain: `admin/admin (ADMIN)`, `trader1/trader1 (TRADER)`, `trader2/trader2 (TRADER)`, `trader3/trader3 (TRADER)`.
- `name()` returns `"Demo"` (unchanged — it is a display string, not a type name).
- Authentication logic (case-insensitive username, case-sensitive password) is **identical** to the original.
- Javadoc references `HsbcDemoAuthModule`, `UserSessionRegistry`.

### 6.4 `UserSession.java` (name unchanged)
Translate verbatim. Record fields: `token`, `user` (`AuthenticatedUser`), `workbenchState` (`HsbcFixClientWorkbenchState`), `expiresAt`. Canonical constructor validation unchanged. Methods: `withExpiresAt(Instant)`, `isExpired()`. Package: `com.hsbc.fix.client`.

### 6.5 `UserSessionRegistry.java` (name unchanged)
Translate from `TheFixClient`. Key changes:
- Package: `com.hsbc.fix.client`.
- Internal references: `TheFixClientConfig` → `HsbcFixClientConfig`, `TheFixClientWorkbenchState` → `HsbcFixClientWorkbenchState`, `TheFixSessionProfileStore` → `HsbcFixSessionProfileStore`, `TheFixMessageTemplateStore` → `HsbcFixMessageTemplateStore`, `TheFixOrderStore` → `HsbcFixOrderStore`, `DemoAuthModule` is not directly referenced here (it is injected at construction in `HsbcFixClientServer`).
- `createWorkbenchState(String username)` uses `HsbcFixClientConfig.withQuickFixLogDir(...)` and instantiates `HsbcFixSessionProfileStore`, `HsbcFixMessageTemplateStore`, `HsbcFixOrderStore`, `HsbcFixClientWorkbenchState`.
- Path-traversal prevention logic (SAFE_USERNAME_PATTERN, baseDir.resolve check) is preserved **exactly**.
- `maskTokenForLog` static helper preserved.
- All other logic (`login`, `validate`, `logout`, `activeSessions`, `close`, `evict`, `closeQuietly`, `nextExpiry`) is **identical** to the original.

**Security note:** Preserve all security-critical behaviour:
- `SAFE_USERNAME_PATTERN` (`^[a-zA-Z0-9_-]{1,64}$`) must remain unchanged.
- The resolved user directory must be validated to start with the base directory (`userBase.startsWith(baseDir)`).
- `maskTokenForLog` must never log the full token.

**Verification:**
```bash
./gradlew --no-daemon :HsbcFixClient:compileJava
```
Must be GREEN.

**Done?** Commit all new files. Then ask: "Prompt 6 is complete — please confirm to proceed with Prompt 7."

---

## Prompt 7 — Workbench State Orchestration and Cucumber Runner

**Context:** You are continuing the sequential rewrite of `TheFixClient` as `HsbcFixClient`. Prompts 1–6 are done. Now create the central workbench state class and the Cucumber runner. These are the two remaining large classes. Do **not** modify any simulator code. After finishing ask the operator to confirm before proceeding.

**Task:** Create the following two source files in `src/main/java/com/hsbc/fix/client/`.

### 7.1 `HsbcFixClientWorkbenchState.java`
Translate `TheFixClientWorkbenchState`. Key changes:
- Class name: `HsbcFixClientWorkbenchState`. Implements `AutoCloseable`.
- All internal references updated: `TheFixClientConfig` → `HsbcFixClientConfig`, `TheFixSessionProfile` → `HsbcFixSessionProfile`, `TheFixSessionProfileStore` → `HsbcFixSessionProfileStore`, `TheFixMessageTemplateStore` → `HsbcFixMessageTemplateStore`, `TheFixOrderStore` → `HsbcFixOrderStore`, `TheFixClientFixService` → `HsbcFixClientFixService`, `TheFixFixDictionaryCatalog` → `HsbcFixDictionaryCatalog`, `TheFixBulkOptions` → `HsbcFixBulkOptions`, `TheFixOrderRequest` → `HsbcFixOrderRequest`, `TheFixMessageType` → `HsbcFixMessageType`, `TheFixMessageTemplate` → `HsbcFixMessageTemplate`, `TheFixCucumberRunner` → `HsbcFixCucumberRunner`.
- Static `FIX_DICTIONARY_CATALOG` field uses `HsbcFixDictionaryCatalog`.
- `snapshot()` JSON field `"applicationName"` value: `"HsbcFixClient"`.
- `snapshot()` JSON field `"subtitle"` value: `"Electronic execution workstation"` (unchanged).
- `snapshot()` JSON field `"environment"` value: `"Live simulator-linked FIX order workstation"` (unchanged).
- All constructors, `MARKETS`, `REGION_LABELS`, `SIDE_OPTIONS`, `ORDER_TYPES`, `PRICE_TYPES`, `TIME_IN_FORCE_OPTIONS`, `BULK_MODES`, validation sets are **identical** to the original.
- All public synchronized methods (`connect`, `disconnect`, `pulseTest`, `resetSequenceNumbers`, `sendOrder`, `amendBlotterOrder`, `cancelBlotterOrder`, `startOrderFlow`, `stopOrderFlow`, `previewOrder`, `saveSettingsProfile`, `activateSettingsProfile`, `deleteSettingsProfile`, `updateSettingsStoragePath`, `saveMessageTemplate`, `fixMetadataSnapshot`, `settingsSnapshot`, `sessionProfilesSnapshot`, `templateSnapshot`, `runCucumber`, `recentFixMessages`, `close`) and all private helpers are **identical** — only type references are updated.
- Inner value types (`MarketDefinition`, `OptionDefinition`, `OrderTypeDefinition`, `OrderPreview`) are preserved unchanged.

### 7.2 `HsbcFixCucumberRunner.java`
Translate `TheFixCucumberRunner`. Key changes:
- Class name: `HsbcFixCucumberRunner`.
- Constructor accepts `HsbcFixClientWorkbenchState`.
- All internal references updated: `TheFixClientWorkbenchState` → `HsbcFixClientWorkbenchState`.
- Class Javadoc updated to reference `HsbcFixCucumberRunner` and `HsbcFixClientWorkbenchState`.
- All step definitions, Gherkin parser, `run(String, String)`, `executeScenario`, `executeStep`, `buildStepDefinitions`, `parseFeature`, all inner types (`ParsedFeature`, `ParsedScenario`, `StepDefinition`, `StepResult`, `RunContext`) are **identical** — no logic changes.

**Verification:**
```bash
./gradlew --no-daemon :HsbcFixClient:compileJava
```
Must be GREEN.

**Done?** Commit all new files. Then ask: "Prompt 7 is complete — please confirm to proceed with Prompt 8."

---

## Prompt 8 — HTTP Server, Configuration, and Application Bootstrap

**Context:** You are continuing the sequential rewrite of `TheFixClient` as `HsbcFixClient`. Prompts 1–7 are done. Now create the three remaining production classes: the application configuration record, the Vert.x HTTP server, and the main entry point. After finishing ask the operator to confirm before proceeding.

**Task:** Create the following three source files in `src/main/java/com/hsbc/fix/client/`.

### 8.1 `HsbcFixClientConfig.java`
Translate `TheFixClientConfig`. Key changes:
- Record name: `HsbcFixClientConfig`.
- Default port constant: `DEFAULT_PORT = 8081` (unchanged).
- Property and environment variable names updated throughout:
  - `thefix.client.port` → `hsbc.client.port`
  - `THEFIX_CLIENT_PORT` → `HSBC_CLIENT_PORT`
  - `thefix.fix.host` → `hsbc.fix.host`
  - `THEFIX_FIX_HOST` → `HSBC_FIX_HOST`
  - `thefix.fix.port` → `hsbc.fix.port`
  - `THEFIX_FIX_PORT` → `HSBC_FIX_PORT`
  - `thefix.fix.beginString` → `hsbc.fix.beginString`
  - `THEFIX_FIX_BEGIN_STRING` → `HSBC_FIX_BEGIN_STRING`
  - `thefix.fix.senderCompId` → `hsbc.fix.senderCompId`
  - `THEFIX_FIX_SENDER_COMP_ID` → `HSBC_FIX_SENDER_COMP_ID`
  - `thefix.fix.targetCompId` → `hsbc.fix.targetCompId`
  - `THEFIX_FIX_TARGET_COMP_ID` → `HSBC_FIX_TARGET_COMP_ID`
  - `thefix.fix.defaultApplVerId` → `hsbc.fix.defaultApplVerId`
  - `THEFIX_FIX_DEFAULT_APPL_VER_ID` → `HSBC_FIX_DEFAULT_APPL_VER_ID`
  - `thefix.fix.heartBtInt` → `hsbc.fix.heartBtInt`
  - `THEFIX_FIX_HEARTBTINT` → `HSBC_FIX_HEARTBTINT`
  - `thefix.fix.reconnectIntervalSec` → `hsbc.fix.reconnectIntervalSec`
  - `THEFIX_FIX_RECONNECT_INTERVAL_SEC` → `HSBC_FIX_RECONNECT_INTERVAL_SEC`
  - `thefix.fix.defaultRatePerSecond` → `hsbc.fix.defaultRatePerSecond`
  - `THEFIX_FIX_DEFAULT_RATE` → `HSBC_FIX_DEFAULT_RATE`
  - `thefix.fix.logDir` → `hsbc.fix.logDir`
  - `THEFIX_FIX_LOG_DIR` → `HSBC_FIX_LOG_DIR`
  - `thefix.fix.rawLoggingEnabled` → `hsbc.fix.rawLoggingEnabled`
  - `THEFIX_FIX_RAW_LOGGING_ENABLED` → `HSBC_FIX_RAW_LOGGING_ENABLED`
  - `thefix.client.sessionTimeoutMinutes` → `hsbc.client.sessionTimeoutMinutes`
  - `THEFIX_CLIENT_SESSION_TIMEOUT_MINUTES` → `HSBC_CLIENT_SESSION_TIMEOUT_MINUTES`
  - `thefix.client.orderDataRetentionDays` → `hsbc.client.orderDataRetentionDays`
  - `THEFIX_CLIENT_ORDER_DATA_RETENTION_DAYS` → `HSBC_CLIENT_ORDER_DATA_RETENTION_DAYS`
- **Remove** all legacy fallback property / env-var lookups (the `fix.demo.*` and `FIX_CLIENT_*` / `FIX_DEMO_*` fallback arguments). The `resolveString` and `resolvePositiveInt` helpers still accept four parameters for primary property, primary env, fallback property, fallback env — pass `null` for both fallback arguments for all fields.
- Default `senderCompId`: `"HSBC_TRDR01"` (was `"THEFIX_TRDR01"`).
- Default `quickFixLogDir`: `"logs/hsbcfixclient/quickfixj"` (was `"logs/thefixclient/quickfixj"`).
- `withQuickFixLogDir(String newLogDir)` copy-helper is preserved.
- `localUrl()`, `toFixDemoClientConfig()`, `resolvePort()`, `parsePort(String)`, `resolveString`, `resolvePositiveInt`, `resolveBoolean`, `firstNonBlank` private helpers are preserved with the same logic.

### 8.2 `HsbcFixClientServer.java`
Translate `TheFixClientServer`. Key changes:
- Class name: `HsbcFixClientServer`.
- Auth cookie constant: `AUTH_TOKEN_COOKIE = "hsbc-token"` (was `"thefix-token"`).
- `AUTH_TOKEN_HEADER = "X-Auth-Token"` (unchanged).
- All internal references updated: `TheFixClientConfig` → `HsbcFixClientConfig`, `UserSessionRegistry`, `DemoAuthModule` → `HsbcDemoAuthModule`, `TheFixClientWorkbenchState` → `HsbcFixClientWorkbenchState`, `UserSession` (unchanged).
- Primary constructor: `HsbcFixClientServer(HsbcFixClientConfig config)` delegates to the two-arg constructor with `new UserSessionRegistry(config, new HsbcDemoAuthModule())`.
- Health endpoint response: `"application"` field value `"HsbcFixClient"`, all other fields identical.
- Log messages mentioning `"TheFixClient"` updated to `"HsbcFixClient"`.
- All routes, middleware, auth logic, `writeJson`, `bodyJson`, `parseIntParam`, `resolveSession`, `resolveToken`, `workbench` helpers are **identical** — only type references updated.
- `start()`, `stop()`, `actualPort()` are preserved with identical logic.
- SPA route regex and trailing-slash redirect are **identical** to the original.

### 8.3 `HsbcFixClientApplication.java`
Translate `TheFixClientApplication`. Key changes:
- Class name: `HsbcFixClientApplication`.
- All internal references updated: `HsbcFixClientConfig`, `HsbcFixClientServer`.
- Shutdown hook thread name: `"hsbcfixclient-shutdown"` (was `"thefix-client-shutdown"`).
- `startupMessage` returns: `"HsbcFixClient trader workstation is available at " + config.localUrl()`.

**Verification:**
```bash
./gradlew --no-daemon :HsbcFixClient:compileJava :HsbcFixClient:classes
```
Must be GREEN.

**Done?** Commit all new files. Then ask: "Prompt 8 is complete — please confirm to proceed with Prompt 9."

---

## Prompt 9 — Unit Tests

**Context:** You are continuing the sequential rewrite of `TheFixClient` as `HsbcFixClient`. Prompts 1–8 are done and the production code compiles. Now create the complete test suite that mirrors the `TheFixClient` test suite. Do **not** modify any simulator code. After finishing ask the operator to confirm before proceeding.

**Task:** In `src/test/java/com/hsbc/fix/client/` create the following 20 test classes, each faithfully translated from its `TheFixClient` counterpart. Apply all name substitutions. All import statements, `@Test` methods, assertions, helper methods, `@BeforeEach`/`@AfterEach` setup/teardown, and test data must be **identical** in logic and coverage to the originals.

| New test class | Source |
|---|---|
| `HsbcFixBulkOptionsTest` | `TheFixBulkOptionsTest` |
| `HsbcFixTagEntryTest` | `TheFixTagEntryTest` |
| `HsbcFixMessageTypeTest` | `TheFixMessageTypeTest` |
| `HsbcFixVersionTest` | `TheFixFixVersionTest` |
| `HsbcFixDictionaryCatalogTest` | `TheFixFixDictionaryCatalogTest` |
| `HsbcFixOrderRequestTest` | `TheFixOrderRequestTest` |
| `HsbcFixSessionProfileTest` | `TheFixSessionProfileTest` |
| `HsbcFixSessionProfileStoreTest` | `TheFixSessionProfileStoreTest` |
| `HsbcFixMessageTemplateStoreTest` | `TheFixMessageTemplateStoreTest` |
| `HsbcFixOrderStoreTest` | `TheFixOrderStoreTest` |
| `HsbcFixClientConfigTest` | `TheFixClientConfigTest` |
| `HsbcFixClientWorkbenchStateTest` | `TheFixClientWorkbenchStateTest` |
| `HsbcFixClientServerRoutingTest` | `TheFixClientServerRoutingTest` |
| `HsbcFixClientApplicationTest` | `TheFixClientApplicationTest` |
| `HsbcFixClientFixServiceMessageClassificationTest` | `TheFixClientFixServiceMessageClassificationTest` |
| `HsbcFixClientFixServiceBulkFlowDisconnectTest` | `TheFixClientFixServiceBulkFlowDisconnectTest` |
| `HsbcFixClientLiveIntegrationTest` | `TheFixClientLiveIntegrationTest` |
| `HsbcFixCucumberRunnerTest` | `TheFixCucumberRunnerTest` |
| `UserSessionRegistryTest` | `UserSessionRegistryTest` |
| `HsbcDemoAuthModuleTest` | `DemoAuthModuleTest` |

**Important details:**
- In `HsbcFixClientConfigTest`: update all constructor call sites to use the new record (no legacy fallback args), updated default values (`"HSBC_TRDR01"` senderCompId, `"logs/hsbcfixclient/..."` paths), and new property/env-var names.
- In `HsbcFixClientServerRoutingTest`: update the `TheFixClientConfig` constructor call to `HsbcFixClientConfig` with the new field defaults; update login credentials test data if needed; update `AUTH_TOKEN_COOKIE` reference to `"hsbc-token"`.
- In `UserSessionRegistryTest`: keep all test method names and logic; update instantiation to `new UserSessionRegistry(config, new HsbcDemoAuthModule())`.
- In `HsbcFixClientApplicationTest`: update `startupMessage` assertion to match `"HsbcFixClient trader workstation..."`.

**Verification:**
```bash
./gradlew --no-daemon :HsbcFixClient:test
```
Must be GREEN — all tests pass.

**Done?** Commit all new test files. Then ask: "Prompt 9 is complete — please confirm to proceed with Prompt 10."

---

## Prompt 10 — Deployment Scripts and Full Integration Verification

**Context:** You are completing the sequential rewrite of `TheFixClient` as `HsbcFixClient`. Prompts 1–9 are done and all `HsbcFixClient` tests pass. Now create the deployment scripts for the new module and perform a full repository build verification. Do **not** modify any simulator code or `TheFixClient` scripts. After finishing ask the operator to confirm.

**Task:**

### 10.1 New deployment scripts

Create the following five scripts in `scripts/`. Base each on its `TheFixClient` counterpart but update all names per the mapping table. Each new script must coexist with the old scripts — do **not** delete or modify the existing `start_fix_client.sh`, `stop_fix_client.sh`, `start_fix_client_docker.sh`, `stop_fix_client_docker.sh`.

#### `scripts/start_hsbc_fix_client.sh`
Translate `start_fix_client.sh`. Key changes:
- All banner/display strings: `"TheFixClient"` → `"HsbcFixClient"`.
- Gradle build command: `${GRADLEW_BIN} --no-daemon :HsbcFixClient:clean :HsbcFixClient:installDist -x :HsbcFixClient:test`.
- Distribution directory: `${PROJECT_ROOT}/HsbcFixClient/build/install/HsbcFixClient`.
- Binary name: `./bin/HsbcFixClient`.
- Environment variable names: `HSBC_CLIENT_PORT`, `HSBC_FIX_HOST`, `HSBC_FIX_PORT`, `HSBC_FIX_LOG_DIR`, `HSBC_FIX_RAW_LOGGING_ENABLED`, `HSBC_CLIENT_JAVA_XMS`, `HSBC_CLIENT_JAVA_XMX`.
- PID file: use a new variable `HSBC_CLIENT_PID_FILE` pointing to `${PID_DIR}/hsbcfixclient.pid`.
- Log file: use `HSBC_CLIENT_LOG_FILE` pointing to `${LOG_DIR}/hsbcfixclient.log`.
- QuickFIX log dir: `${CLIENT_RUNTIME_DIR}/hsbcfixclient/quickfixj` (create with `mkdir -p`).
- Port default: `${WEB_STACK_CLIENT_PORT}` (same `web_stack_common.sh` variable, default 8081).
- Health check URL: `http://localhost:${CLIENT_PORT}/api/health`.
- Help text updated to reference `HsbcFixClient` throughout.
- Heap defaults: `HSBC_CLIENT_TARGET_HEAP_XMS` / `HSBC_CLIENT_TARGET_HEAP_XMX` (from `native_runtime_targets.sh`).
- Native runtime targets property file: `target-hsbcfixclient.properties` (instead of `target-fixclient.properties`).

#### `scripts/stop_hsbc_fix_client.sh`
Translate `stop_fix_client.sh`. Key changes:
- Banner: `"Stopping HsbcFixClient (Direct JVM)"`.
- Uses `HSBC_CLIENT_PID_FILE`.
- `stop_pidfile_process "HsbcFixClient" "${HSBC_CLIENT_PID_FILE}"`.
- `stop_listener_on_port "HsbcFixClient" "${CLIENT_PORT}"`.

#### `scripts/start_hsbc_fix_client_docker.sh`
Translate `start_fix_client_docker.sh`. Key changes:
- All banner/display strings: `"HsbcFixClient"`.
- Container name default: `"hsbcfixclient"`.
- Docker image name: `hsbcfixclient:1.0-SNAPSHOT`.
- Gradle build command: `${GRADLEW_BIN} --no-daemon :HsbcFixClient:installDist -x :HsbcFixClient:test`.
- Docker build command: `docker build -t hsbcfixclient:1.0-SNAPSHOT ${PROJECT_ROOT}/HsbcFixClient`.
- Environment variables in `docker run`: `HSBC_CLIENT_PORT`, `HSBC_FIX_HOST`, `HSBC_FIX_PORT`, `HSBC_FIX_LOG_DIR=/app/logs/hsbcfixclient/quickfixj`, `HSBC_FIX_RAW_LOGGING_ENABLED`, `JAVA_OPTS`.
- Health check: `curl -sf http://localhost:${CLIENT_PORT}/api/health`.
- Log dir default: `${PROJECT_ROOT}/HsbcFixClient/logs`.
- Help text updated.

#### `scripts/stop_hsbc_fix_client_docker.sh`
Translate `stop_fix_client_docker.sh`. Key changes:
- All display strings: `"HsbcFixClient"`.
- Default container name: `"hsbcfixclient"`.

Make all four new scripts executable: `chmod +x scripts/start_hsbc_fix_client.sh scripts/stop_hsbc_fix_client.sh scripts/start_hsbc_fix_client_docker.sh scripts/stop_hsbc_fix_client_docker.sh`.

### 10.2 Update `scripts/native_runtime_targets.sh`

In `native_runtime_targets.sh`, add a section that loads `HsbcFixClient/config/target-hsbcfixclient.properties` and exports:
- `HSBCCLIENT_TARGET_HEAP_XMS`
- `HSBCCLIENT_TARGET_HEAP_XMX`
- `HSBCCLIENT_TARGET_CPU_PINNING`

Mirror the existing pattern used for `FIXCLIENT_TARGET_*` variables.

### 10.3 Full repository build verification

Run the full build and ensure it is GREEN:
```bash
./gradlew --no-daemon clean build test
```

This verifies that:
- Both `:TheFixSimulator` and `:TheFixClient` are unchanged and still pass.
- `:HsbcFixClient` compiles, and all 20 tests pass.
- The JaCoCo coverage gate for `:TheFixSimulator` remains at 100%.

If any tests fail, diagnose and fix them before marking this prompt complete.

### 10.4 Smoke-test the distribution (optional but recommended)

```bash
./gradlew --no-daemon :HsbcFixClient:installDist
# Verify the binary exists
ls HsbcFixClient/build/install/HsbcFixClient/bin/HsbcFixClient
```

**Done?** Commit all scripts, the `native_runtime_targets.sh` update, and any fixes. Then state: "Prompt 10 is complete — the HsbcFixClient rewrite is finished. All tests are GREEN."
