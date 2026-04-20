# HsbcFixClient Rewrite Prompts

## How to use this document

This document contains a series of **consecutive agent prompts** for rewriting the `TheFixClient` Gradle module into a new `HsbcFixClient` module, renaming all classes, packages, properties, environment variables, scripts, and web assets accordingly.

**Important instructions for the working agent:**

> You are working through a series of consecutive prompts. Each prompt is a self-contained task.  
> After you finish each prompt — including committing and pushing your changes via `report_progress` — **stop and ask the user: "Prompt N is complete. Please send me Prompt N+1 to continue."**  
> Do **not** proceed to the next prompt on your own. Wait for the user to provide it.

---

## Naming convention reference

The following table documents every name change applied throughout all prompts. Refer back to it at any time:

| Old name | New name |
|---|---|
| Gradle module directory | `TheFixClient/` → `HsbcFixClient/` |
| Gradle module name (settings) | `TheFixClient` → `HsbcFixClient` |
| Java package | `com.insoftu.thefix.client` → `com.hsbc.fix.client` |
| `TheFixClientApplication` | `HsbcFixClientApplication` |
| `TheFixClientConfig` | `HsbcFixClientConfig` |
| `TheFixClientServer` | `HsbcFixClientServer` |
| `TheFixClientWorkbenchState` | `HsbcFixClientWorkbenchState` |
| `TheFixClientFixService` | `HsbcFixClientFixService` |
| `TheFixBulkOptions` | `HsbcFixBulkOptions` |
| `TheFixCucumberRunner` | `HsbcFixCucumberRunner` |
| `TheFixFixDictionaryCatalog` | `HsbcFixDictionaryCatalog` |
| `TheFixFixVersion` | `HsbcFixVersion` |
| `TheFixMessageTemplate` | `HsbcFixMessageTemplate` |
| `TheFixMessageTemplateStore` | `HsbcFixMessageTemplateStore` |
| `TheFixMessageType` | `HsbcFixMessageType` |
| `TheFixOrderRequest` | `HsbcFixOrderRequest` |
| `TheFixOrderStore` | `HsbcFixOrderStore` |
| `TheFixSessionProfile` | `HsbcFixSessionProfile` |
| `TheFixSessionProfileStore` | `HsbcFixSessionProfileStore` |
| `TheFixTagEntry` | `HsbcFixTagEntry` |
| `TheFixBulkOptionsTest` | `HsbcFixBulkOptionsTest` |
| `TheFixClientApplicationTest` | `HsbcFixClientApplicationTest` |
| `TheFixClientConfigTest` | `HsbcFixClientConfigTest` |
| `TheFixClientFixServiceBulkFlowDisconnectTest` | `HsbcFixClientFixServiceBulkFlowDisconnectTest` |
| `TheFixClientFixServiceMessageClassificationTest` | `HsbcFixClientFixServiceMessageClassificationTest` |
| `TheFixClientLiveIntegrationTest` | `HsbcFixClientLiveIntegrationTest` |
| `TheFixClientServerRoutingTest` | `HsbcFixClientServerRoutingTest` |
| `TheFixClientWorkbenchStateTest` | `HsbcFixClientWorkbenchStateTest` |
| `TheFixCucumberRunnerTest` | `HsbcFixCucumberRunnerTest` |
| `TheFixFixDictionaryCatalogTest` | `HsbcFixDictionaryCatalogTest` |
| `TheFixFixVersionTest` | `HsbcFixVersionTest` |
| `TheFixMessageTemplateStoreTest` | `HsbcFixMessageTemplateStoreTest` |
| `TheFixMessageTypeTest` | `HsbcFixMessageTypeTest` |
| `TheFixOrderRequestTest` | `HsbcFixOrderRequestTest` |
| `TheFixOrderStoreTest` | `HsbcFixOrderStoreTest` |
| `TheFixSessionProfileStoreTest` | `HsbcFixSessionProfileStoreTest` |
| `TheFixSessionProfileTest` | `HsbcFixSessionProfileTest` |
| `TheFixTagEntryTest` | `HsbcFixTagEntryTest` |
| Config property `thefix.client.port` | `hsbcfix.client.port` |
| Config property `thefix.fix.*` | `hsbcfix.fix.*` |
| Config property `thefix.client.sessionTimeoutMinutes` | `hsbcfix.client.sessionTimeoutMinutes` |
| Config property `thefix.client.orderDataRetentionDays` | `hsbcfix.client.orderDataRetentionDays` |
| Env var `THEFIX_CLIENT_PORT` | `HSBCFIX_CLIENT_PORT` |
| Env var `THEFIX_FIX_*` | `HSBCFIX_FIX_*` |
| Env var `THEFIX_CLIENT_SESSION_TIMEOUT_MINUTES` | `HSBCFIX_CLIENT_SESSION_TIMEOUT_MINUTES` |
| Env var `THEFIX_CLIENT_ORDER_DATA_RETENTION_DAYS` | `HSBCFIX_CLIENT_ORDER_DATA_RETENTION_DAYS` |
| Default senderCompId | `THEFIX_TRDR01` → `HSBCFIX_TRDR01` |
| Auth cookie name | `thefix-token` → `hsbcfix-token` |
| Browser storage key (theme) | `thefixclient-theme` → `hsbcfixclient-theme` |
| Browser storage key (refresh frequency) | `thefixclient-refresh-frequency` → `hsbcfixclient-refresh-frequency` |
| Browser storage key (auth token) | `thefixclient-auth-token` → `hsbcfixclient-auth-token` |
| Log directory | `logs/thefixclient/quickfixj` → `logs/hsbcfixclient/quickfixj` |
| Docker container name | `thefixclient` → `hsbcfixclient` |
| Docker binary path | `/app/bin/TheFixClient` → `/app/bin/HsbcFixClient` |
| Gradle install path in Docker | `build/install/TheFixClient/` → `build/install/HsbcFixClient/` |
| Config file comment | `TheFixClient` → `HsbcFixClient` |
| Gradle `:TheFixClient:*` task paths | `:HsbcFixClient:*` |
| `docker-compose` service name | `thefixclient` → `hsbcfixclient` |

> **Classes without a `TheFix` prefix that are NOT renamed:**  
> `AuthModule`, `AuthenticatedUser`, `DemoAuthModule`, `UserSession`, `UserSessionRegistry`,  
> `DemoAuthModuleTest`, `UserSessionRegistryTest`  
> These are already neutral enough and do not carry the old brand prefix.

---

## Prompt 1 — Rename the Gradle module directory and root build configuration

You are working on the `legen848dary/MyFix` repository.

**Context:** The `TheFixClient` Gradle module must be renamed to `HsbcFixClient`. This prompt covers the Gradle skeleton — the directory rename, `settings.gradle.kts`, and the module-level `build.gradle.kts`. No Java source files are changed yet.

**Tasks:**

1. **Rename the module directory.** Use `git mv TheFixClient HsbcFixClient` so the rename is tracked by Git.

2. **Update `settings.gradle.kts`** (repository root) — change the include line from:
   ```
   include("TheFixClient", "TheFixSimulator")
   ```
   to:
   ```
   include("HsbcFixClient", "TheFixSimulator")
   ```

3. **Update `HsbcFixClient/build.gradle.kts`** (the file that was `TheFixClient/build.gradle.kts`):
   - Change the `mainClass` in the `application` block from `com.insoftu.thefix.client.TheFixClientApplication` to `com.hsbc.fix.client.HsbcFixClientApplication`.
   - Leave the dependency on `project(":TheFixSimulator")` unchanged.
   - Leave all other content unchanged for now.

4. **Update `docker-compose.web-stack.yml`** — wherever the service is referred to as `thefixclient` (service name, container name, image name, volume mounts, etc.) rename it to `hsbcfixclient`. Update any path references from `./TheFixClient/` to `./HsbcFixClient/`.

5. **Update `scripts/native_runtime_targets.sh`** — if it references `TheFixClient` or the config file `target-fixclient.properties`, update those paths to point to `HsbcFixClient/config/target-fixclient.properties` (the properties file itself keeps its filename for now; only the module directory path changes).

6. **Verify the build skeleton still resolves** by running:
   ```
   ./gradlew --no-daemon :HsbcFixClient:dependencies
   ```
   It is expected to fail on compilation since source files have not been updated yet, but the Gradle project graph must resolve without errors. Fix any Gradle evaluation errors before finishing.

7. Use `report_progress` to commit and push all changes with the message: `rename: Gradle module TheFixClient → HsbcFixClient (build skeleton)`.

**When done**, confirm what you changed, then say:  
> "Prompt 1 is complete. Please send me Prompt 2 to continue."

---

## Prompt 2 — Rename the Java package and source file names

You are working on the `legen848dary/MyFix` repository. Prompt 1 has already been completed: the Gradle module directory is now `HsbcFixClient/` and `settings.gradle.kts` includes `HsbcFixClient`.

**Context:** All Java source files live under `HsbcFixClient/src/main/java/com/insoftu/thefix/client/` and `HsbcFixClient/src/test/java/com/insoftu/thefix/client/`. This prompt renames the directory tree that represents the package and renames every source file to match the new class name. **No file content is changed yet** — that is covered in subsequent prompts.

**Tasks:**

1. **Rename the source directories** to reflect the new package `com.hsbc.fix.client`:
   - `HsbcFixClient/src/main/java/com/insoftu/thefix/client/` → `HsbcFixClient/src/main/java/com/hsbc/fix/client/`
   - `HsbcFixClient/src/test/java/com/insoftu/thefix/client/` → `HsbcFixClient/src/test/java/com/hsbc/fix/client/`
   
   Use `git mv` for each step so the renames are tracked. Create the new intermediate directories (`com/hsbc/fix/client/`) first with `mkdir -p` before moving.

2. **Rename every production source file** inside `HsbcFixClient/src/main/java/com/hsbc/fix/client/` using `git mv`. Apply the naming convention from the table at the top of this document. Files without a `TheFix` prefix (`AuthModule.java`, `AuthenticatedUser.java`, `DemoAuthModule.java`, `UserSession.java`, `UserSessionRegistry.java`) keep their names.

   | Old filename | New filename |
   |---|---|
   | `TheFixClientApplication.java` | `HsbcFixClientApplication.java` |
   | `TheFixClientConfig.java` | `HsbcFixClientConfig.java` |
   | `TheFixClientServer.java` | `HsbcFixClientServer.java` |
   | `TheFixClientWorkbenchState.java` | `HsbcFixClientWorkbenchState.java` |
   | `TheFixClientFixService.java` | `HsbcFixClientFixService.java` |
   | `TheFixBulkOptions.java` | `HsbcFixBulkOptions.java` |
   | `TheFixCucumberRunner.java` | `HsbcFixCucumberRunner.java` |
   | `TheFixFixDictionaryCatalog.java` | `HsbcFixDictionaryCatalog.java` |
   | `TheFixFixVersion.java` | `HsbcFixVersion.java` |
   | `TheFixMessageTemplate.java` | `HsbcFixMessageTemplate.java` |
   | `TheFixMessageTemplateStore.java` | `HsbcFixMessageTemplateStore.java` |
   | `TheFixMessageType.java` | `HsbcFixMessageType.java` |
   | `TheFixOrderRequest.java` | `HsbcFixOrderRequest.java` |
   | `TheFixOrderStore.java` | `HsbcFixOrderStore.java` |
   | `TheFixSessionProfile.java` | `HsbcFixSessionProfile.java` |
   | `TheFixSessionProfileStore.java` | `HsbcFixSessionProfileStore.java` |
   | `TheFixTagEntry.java` | `HsbcFixTagEntry.java` |

3. **Rename every test source file** inside `HsbcFixClient/src/test/java/com/hsbc/fix/client/` using `git mv`. Files without a `TheFix` prefix keep their names.

   | Old filename | New filename |
   |---|---|
   | `TheFixBulkOptionsTest.java` | `HsbcFixBulkOptionsTest.java` |
   | `TheFixClientApplicationTest.java` | `HsbcFixClientApplicationTest.java` |
   | `TheFixClientConfigTest.java` | `HsbcFixClientConfigTest.java` |
   | `TheFixClientFixServiceBulkFlowDisconnectTest.java` | `HsbcFixClientFixServiceBulkFlowDisconnectTest.java` |
   | `TheFixClientFixServiceMessageClassificationTest.java` | `HsbcFixClientFixServiceMessageClassificationTest.java` |
   | `TheFixClientLiveIntegrationTest.java` | `HsbcFixClientLiveIntegrationTest.java` |
   | `TheFixClientServerRoutingTest.java` | `HsbcFixClientServerRoutingTest.java` |
   | `TheFixClientWorkbenchStateTest.java` | `HsbcFixClientWorkbenchStateTest.java` |
   | `TheFixCucumberRunnerTest.java` | `HsbcFixCucumberRunnerTest.java` |
   | `TheFixFixDictionaryCatalogTest.java` | `HsbcFixDictionaryCatalogTest.java` |
   | `TheFixFixVersionTest.java` | `HsbcFixVersionTest.java` |
   | `TheFixMessageTemplateStoreTest.java` | `HsbcFixMessageTemplateStoreTest.java` |
   | `TheFixMessageTypeTest.java` | `HsbcFixMessageTypeTest.java` |
   | `TheFixOrderRequestTest.java` | `HsbcFixOrderRequestTest.java` |
   | `TheFixOrderStoreTest.java` | `HsbcFixOrderStoreTest.java` |
   | `TheFixSessionProfileStoreTest.java` | `HsbcFixSessionProfileStoreTest.java` |
   | `TheFixSessionProfileTest.java` | `HsbcFixSessionProfileTest.java` |
   | `TheFixTagEntryTest.java` | `HsbcFixTagEntryTest.java` |

4. Use `report_progress` to commit and push with the message: `rename: Java source file names and package directory (TheFix* → HsbcFix*)`.

**When done**, confirm what you changed, then say:  
> "Prompt 2 is complete. Please send me Prompt 3 to continue."

---

## Prompt 3 — Update Java source file content: package declarations, class names, and imports

You are working on the `legen848dary/MyFix` repository. Prompts 1 and 2 are done: the module is `HsbcFixClient/`, the package directory is `com/hsbc/fix/client/`, and all source files have their new filenames.

**Context:** Every Java source file in `HsbcFixClient/src/` still declares the old package and old class names inside its content. This prompt updates all file content: package declarations, class/record/interface names, constructor names, logger names, and internal cross-references.

**Tasks:**

1. **Update every production source file** in `HsbcFixClient/src/main/java/com/hsbc/fix/client/`:

   For **every file**, make these global substitutions in the file content:
   - `package com.insoftu.thefix.client;` → `package com.hsbc.fix.client;`
   - `import com.insoftu.thefix.client.` → `import com.hsbc.fix.client.`
   - Apply all class-name substitutions from the naming convention table (e.g., every occurrence of `TheFixClientApplication` → `HsbcFixClientApplication`, `TheFixClientConfig` → `HsbcFixClientConfig`, etc.)
   - `TheFixFixDictionaryCatalog` → `HsbcFixDictionaryCatalog` (drop the doubled `Fix`)
   - `TheFixFixVersion` → `HsbcFixVersion` (drop the doubled `Fix`)

   In addition, apply these specific changes to individual files:

   **`HsbcFixClientApplication.java`:**
   - Rename the class from `TheFixClientApplication` to `HsbcFixClientApplication`.
   - Update the shutdown thread name from `"thefix-client-shutdown"` to `"hsbcfix-client-shutdown"`.
   - Update the startup log message from `"TheFixClient trader workstation is available at "` to `"HsbcFixClient trader workstation is available at "`.
   - Update the static method `startupMessage` — its body string literal accordingly.

   **`HsbcFixClientServer.java`:**
   - Rename the class from `TheFixClientServer` to `HsbcFixClientServer`.
   - Change `AUTH_TOKEN_COOKIE = "thefix-token"` to `AUTH_TOKEN_COOKIE = "hsbcfix-token"`.
   - In the health endpoint JSON response, update `"application"` value from `"TheFixClient"` to `"HsbcFixClient"`.

   **`HsbcFixClientConfig.java`:**
   - Rename the record from `TheFixClientConfig` to `HsbcFixClientConfig`.
   - Replace all primary config property keys:
     - `"thefix.client.port"` → `"hsbcfix.client.port"`
     - `"thefix.fix.host"` → `"hsbcfix.fix.host"`
     - `"thefix.fix.port"` → `"hsbcfix.fix.port"`
     - `"thefix.fix.beginString"` → `"hsbcfix.fix.beginString"`
     - `"thefix.fix.senderCompId"` → `"hsbcfix.fix.senderCompId"`
     - `"thefix.fix.targetCompId"` → `"hsbcfix.fix.targetCompId"`
     - `"thefix.fix.defaultApplVerId"` → `"hsbcfix.fix.defaultApplVerId"`
     - `"thefix.fix.heartBtInt"` → `"hsbcfix.fix.heartBtInt"`
     - `"thefix.fix.reconnectIntervalSec"` → `"hsbcfix.fix.reconnectIntervalSec"`
     - `"thefix.fix.defaultRatePerSecond"` → `"hsbcfix.fix.defaultRatePerSecond"`
     - `"thefix.fix.logDir"` → `"hsbcfix.fix.logDir"`
     - `"thefix.fix.rawLoggingEnabled"` → `"hsbcfix.fix.rawLoggingEnabled"`
     - `"thefix.client.sessionTimeoutMinutes"` → `"hsbcfix.client.sessionTimeoutMinutes"`
     - `"thefix.client.orderDataRetentionDays"` → `"hsbcfix.client.orderDataRetentionDays"`
   - Replace all primary environment variable names:
     - `"THEFIX_CLIENT_PORT"` → `"HSBCFIX_CLIENT_PORT"`
     - `"THEFIX_FIX_HOST"` → `"HSBCFIX_FIX_HOST"`
     - `"THEFIX_FIX_PORT"` → `"HSBCFIX_FIX_PORT"`
     - `"THEFIX_FIX_BEGIN_STRING"` → `"HSBCFIX_FIX_BEGIN_STRING"`
     - `"THEFIX_FIX_SENDER_COMP_ID"` → `"HSBCFIX_FIX_SENDER_COMP_ID"`
     - `"THEFIX_FIX_TARGET_COMP_ID"` → `"HSBCFIX_FIX_TARGET_COMP_ID"`
     - `"THEFIX_FIX_DEFAULT_APPL_VER_ID"` → `"HSBCFIX_FIX_DEFAULT_APPL_VER_ID"`
     - `"THEFIX_FIX_HEARTBTINT"` → `"HSBCFIX_FIX_HEARTBTINT"`
     - `"THEFIX_FIX_RECONNECT_INTERVAL_SEC"` → `"HSBCFIX_FIX_RECONNECT_INTERVAL_SEC"`
     - `"THEFIX_FIX_DEFAULT_RATE"` → `"HSBCFIX_FIX_DEFAULT_RATE"`
     - `"THEFIX_FIX_LOG_DIR"` → `"HSBCFIX_FIX_LOG_DIR"`
     - `"THEFIX_FIX_RAW_LOGGING_ENABLED"` → `"HSBCFIX_FIX_RAW_LOGGING_ENABLED"`
     - `"THEFIX_CLIENT_SESSION_TIMEOUT_MINUTES"` → `"HSBCFIX_CLIENT_SESSION_TIMEOUT_MINUTES"`
     - `"THEFIX_CLIENT_ORDER_DATA_RETENTION_DAYS"` → `"HSBCFIX_CLIENT_ORDER_DATA_RETENTION_DAYS"`
   - Change the default log directory string from `"logs/thefixclient/quickfixj"` to `"logs/hsbcfixclient/quickfixj"`.
   - Change the default `senderCompId` value from `"THEFIX_TRDR01"` to `"HSBCFIX_TRDR01"`.
   - Change the `WEB_PORT_PROPERTY` constant from `"thefix.client.port"` to `"hsbcfix.client.port"`.
   - Change the `WEB_PORT_ENVIRONMENT` constant from `"THEFIX_CLIENT_PORT"` to `"HSBCFIX_CLIENT_PORT"`.

   **`HsbcFixClientFixService.java`:**
   - Rename the class from `TheFixClientFixService` to `HsbcFixClientFixService`.

   **`HsbcFixClientWorkbenchState.java`:**
   - Rename the class from `TheFixClientWorkbenchState` to `HsbcFixClientWorkbenchState`.

   **`HsbcFixDictionaryCatalog.java`** (was `TheFixFixDictionaryCatalog.java`):
   - Rename the class from `TheFixFixDictionaryCatalog` to `HsbcFixDictionaryCatalog`.
   - Update all self-references and logger declarations accordingly.

   **`HsbcFixVersion.java`** (was `TheFixFixVersion.java`):
   - Rename the class from `TheFixFixVersion` to `HsbcFixVersion`.

   For all remaining renamed files (`HsbcFixBulkOptions`, `HsbcFixCucumberRunner`, `HsbcFixMessageTemplate`, `HsbcFixMessageTemplateStore`, `HsbcFixMessageType`, `HsbcFixOrderRequest`, `HsbcFixOrderStore`, `HsbcFixSessionProfile`, `HsbcFixSessionProfileStore`, `HsbcFixTagEntry`): apply the global package substitution and class-name substitution as described above.

   For **unchanged-name files** (`AuthModule.java`, `AuthenticatedUser.java`, `DemoAuthModule.java`, `UserSession.java`, `UserSessionRegistry.java`): update only the package declaration and any imports that reference the old class names.

2. **Update `UserSessionRegistry.java`** — it constructs `HsbcFixClientWorkbenchState` instances (was `TheFixClientWorkbenchState`). Update those constructor/type references.

3. **Update `UserSession.java`** — update the type of the `workbench` field (if it holds a `TheFixClientWorkbenchState`) to `HsbcFixClientWorkbenchState`.

4. Use `report_progress` to commit and push with the message: `refactor: update Java source content for HsbcFixClient rename`.

**When done**, confirm what you changed, then say:  
> "Prompt 3 is complete. Please send me Prompt 4 to continue."

---

## Prompt 4 — Update all test file content

You are working on the `legen848dary/MyFix` repository. Prompts 1–3 are done: the module is `HsbcFixClient/`, the package is `com.hsbc.fix.client`, all production source files have their new names and updated content.

**Context:** The test source files in `HsbcFixClient/src/test/java/com/hsbc/fix/client/` still have the old package declarations, old class names in their own declarations, old import statements, and references to the old class names in test logic. This prompt brings all test content in line with the new names.

**Tasks:**

1. **Update every test source file** in `HsbcFixClient/src/test/java/com/hsbc/fix/client/`. For each file:
   - Replace `package com.insoftu.thefix.client;` with `package com.hsbc.fix.client;`
   - Replace any `import com.insoftu.thefix.client.` with `import com.hsbc.fix.client.`
   - Replace the test class declaration from the old name (e.g., `class TheFixClientConfigTest`) to the new name (e.g., `class HsbcFixClientConfigTest`).
   - Replace all references to renamed production classes with their new names (use the naming convention table).
   - In `HsbcFixClientConfigTest.java`:
     - All assertions that check for config property key strings (e.g., `"thefix.client.port"`, `"THEFIX_CLIENT_PORT"`, `"THEFIX_FIX_HOST"`, etc.) must be updated to the new names (`"hsbcfix.client.port"`, `"HSBCFIX_CLIENT_PORT"`, `"HSBCFIX_FIX_HOST"`, etc.).
     - The assertion checking the default senderCompId `"THEFIX_TRDR01"` must be updated to `"HSBCFIX_TRDR01"`.
     - The assertion checking the default log dir `"logs/thefixclient/quickfixj"` must be updated to `"logs/hsbcfixclient/quickfixj"`.
   - In `HsbcFixClientApplicationTest.java`:
     - Any string assertions checking the startup log message (`"TheFixClient trader workstation is available at "`) must be updated to `"HsbcFixClient trader workstation is available at "`.
   - In `HsbcFixClientServerRoutingTest.java`:
     - Any assertions on the health endpoint JSON `"application"` field value (`"TheFixClient"`) must be updated to `"HsbcFixClient"`.
     - Any assertions on the auth cookie name (`"thefix-token"`) must be updated to `"hsbcfix-token"`.
   - In `HsbcFixDictionaryCatalogTest.java` (was `TheFixFixDictionaryCatalogTest.java`):
     - Update the class name in the test class declaration.
     - Update all references from `TheFixFixDictionaryCatalog` to `HsbcFixDictionaryCatalog`.
   - In `HsbcFixVersionTest.java` (was `TheFixFixVersionTest.java`):
     - Update the class name and all references from `TheFixFixVersion` to `HsbcFixVersion`.

2. **Do not change** `DemoAuthModuleTest.java` or `UserSessionRegistryTest.java` class names or their package declarations (they do not carry the `TheFix` prefix). Update only their `package` line and any imports that reference renamed production classes.

3. **Attempt a test compilation** to catch any missed references:
   ```
   ./gradlew --no-daemon :HsbcFixClient:compileTestJava
   ```
   Fix all compilation errors before finishing. Do not proceed with an uncompilable test tree.

4. Use `report_progress` to commit and push with the message: `refactor: update test source content for HsbcFixClient rename`.

**When done**, confirm what you changed, then say:  
> "Prompt 4 is complete. Please send me Prompt 5 to continue."

---

## Prompt 5 — Update web frontend assets

You are working on the `legen848dary/MyFix` repository. Prompts 1–4 are done: Gradle module, Java source, and test files are all updated.

**Context:** The web frontend lives in `HsbcFixClient/src/main/resources/web/`. The files `app.js` and `index.html` contain multiple references to `TheFixClient`, `thefixclient`, `THEFIX_TRDR01`, and related names that must be updated.

**Tasks:**

1. **Update `HsbcFixClient/src/main/resources/web/app.js`:**
   - `THEME_STORAGE_KEY = 'thefixclient-theme'` → `'hsbcfixclient-theme'`
   - `REFRESH_FREQUENCY_STORAGE_KEY = 'thefixclient-refresh-frequency'` → `'hsbcfixclient-refresh-frequency'`
   - `AUTH_TOKEN_KEY = 'thefixclient-auth-token'` → `'hsbcfixclient-auth-token'`
   - All occurrences of the display string `'TheFixClient'` (used in headings, the about panel, architecture diagram labels, `applicationName` fallback, page title, etc.) → `'HsbcFixClient'`
   - The default `senderCompId` value `'THEFIX_TRDR01'` (in the session settings form defaults and any FIX tape tag defaults) → `'HSBCFIX_TRDR01'`
   - Any reference to the cookie name `'thefix-token'` → `'hsbcfix-token'`
   - Any plain text description that says `"TheFixClient"` in comment strings, aria-labels, or summary/description fields → `"HsbcFixClient"`

2. **Update `HsbcFixClient/src/main/resources/web/index.html`:**
   - Any `<title>` or heading text that contains `TheFixClient` → `HsbcFixClient`.
   - Any other occurrences of `TheFixClient` in attribute values or content → `HsbcFixClient`.

3. **Update `HsbcFixClient/src/main/resources/web/sample.feature`** (if it contains any `TheFixClient` text or `THEFIX_TRDR01` senderCompId defaults):
   - Replace `THEFIX_TRDR01` → `HSBCFIX_TRDR01` wherever it appears as a senderCompId default or FIX tag example.
   - Replace any display text `TheFixClient` → `HsbcFixClient`.

4. Use `report_progress` to commit and push with the message: `refactor: update web frontend assets for HsbcFixClient rename`.

**When done**, confirm what you changed, then say:  
> "Prompt 5 is complete. Please send me Prompt 6 to continue."

---

## Prompt 6 — Update Docker configuration and lifecycle scripts

You are working on the `legen848dary/MyFix` repository. Prompts 1–5 are done: Gradle module, Java source, tests, and web assets are all updated.

**Context:** The Docker `Dockerfile` and the shell lifecycle scripts in `scripts/` still reference the old `TheFixClient` binary name, container name, and log directory. This prompt updates all of them.

**Tasks:**

1. **Update `HsbcFixClient/Dockerfile`:**
   - Change `COPY build/install/TheFixClient/ /app/` → `COPY build/install/HsbcFixClient/ /app/`
   - Change `RUN chmod +x /app/bin/TheFixClient && \` → `RUN chmod +x /app/bin/HsbcFixClient && \`
   - Change `mkdir -p /app/logs/thefixclient/quickfixj` → `mkdir -p /app/logs/hsbcfixclient/quickfixj`
   - Change `CMD ["sh", "-c", "exec /app/bin/TheFixClient"]` → `CMD ["sh", "-c", "exec /app/bin/HsbcFixClient"]`

2. **Update `HsbcFixClient/config/target-fixclient.properties`:**
   - Update the comment at the top from `# Native runtime target profile for TheFixClient` to `# Native runtime target profile for HsbcFixClient`.
   - The file name `target-fixclient.properties` remains the same.

3. **Update shell scripts that reference TheFixClient** — search with:
   ```
   grep -rn "TheFixClient\|thefixclient\|thefix-client" scripts/ --include="*.sh"
   ```
   For every script found:
   - Replace binary/process name `TheFixClient` → `HsbcFixClient`
   - Replace container name `thefixclient` → `hsbcfixclient`
   - Replace log path `thefixclient` → `hsbcfixclient`
   - Replace module path references `TheFixClient/` → `HsbcFixClient/`
   - Replace Gradle task paths `:TheFixClient:` → `:HsbcFixClient:`
   - Preserve all other logic and formatting unchanged.

4. **Update `testall.sh`** at the repository root if it references `TheFixClient` or `:TheFixClient:` Gradle task paths.

5. **Update `scripts/native_runtime_targets.sh`** — fix any `TheFixClient` path references updated by the module rename in Prompt 1 if not already done.

6. Use `report_progress` to commit and push with the message: `refactor: update Dockerfile and scripts for HsbcFixClient rename`.

**When done**, confirm what you changed, then say:  
> "Prompt 6 is complete. Please send me Prompt 7 to continue."

---

## Prompt 7 — Update documentation and copilot instructions

You are working on the `legen848dary/MyFix` repository. Prompts 1–6 are done: all code, build configuration, Docker, and scripts are updated.

**Context:** Several documentation and configuration files still reference the old names. This prompt updates them so documentation is consistent with the new `HsbcFixClient` identity.

**Tasks:**

1. **Update `README.md`** (repository root):
   - Replace every occurrence of `TheFixClient` with `HsbcFixClient`.
   - Replace every occurrence of `thefixclient` with `hsbcfixclient` (e.g., in URLs, port descriptions, Docker commands).
   - Replace Gradle task paths `:TheFixClient:` → `:HsbcFixClient:`.
   - Replace the Java package reference `com.insoftu.thefix.client` → `com.hsbc.fix.client`.
   - Update the entry-point class name `TheFixClientApplication` → `HsbcFixClientApplication`.
   - Preserve all wording, structure, and formatting otherwise.

2. **Update `.github/copilot-instructions.md`**:
   - In the **Project overview** section:
     - Rename `TheFixClient` → `HsbcFixClient` in the module description bullet.
     - Update the package reference from `com.insoftu.thefix.client` to `com.hsbc.fix.client`.
     - Update the entry point from `com.insoftu.thefix.client.TheFixClientApplication` to `com.hsbc.fix.client.HsbcFixClientApplication`.
   - In the **Build, test, and run** section:
     - Update all `./gradlew :TheFixClient:…` examples to `./gradlew :HsbcFixClient:…`.
     - Update the example test class path from `com.insoftu.thefix.client.TheFixClientServerRoutingTest` to `com.hsbc.fix.client.HsbcFixClientServerRoutingTest`.
   - In the **TheFixClient architecture** section:
     - Rename the section heading to `## HsbcFixClient architecture`.
     - Update all class names in the narrative: `TheFixClientApplication` → `HsbcFixClientApplication`, `TheFixClientServer` → `HsbcFixClientServer`, `TheFixClientWorkbenchState` → `HsbcFixClientWorkbenchState`, `TheFixClientConfig` → `HsbcFixClientConfig`.
   - In the **Key conventions** section:
     - Update the `TheFixClient config resolution pattern` sub-heading and its body text to reference `HsbcFixClientConfig`.
     - Update the `Web static assets` sub-section to reference `HsbcFixClient`.

3. **Update `TEST_COVERAGE.md`** if it lists test class names referencing `TheFixClient` or `TheFix`. Apply the same class-name substitutions from the naming convention table.

4. Use `report_progress` to commit and push with the message: `docs: update README, copilot instructions, and coverage docs for HsbcFixClient`.

**When done**, confirm what you changed, then say:  
> "Prompt 7 is complete. Please send me Prompt 8 to continue."

---

## Prompt 8 — Final build, test, and validation

You are working on the `legen848dary/MyFix` repository. Prompts 1–7 are done: all source files, build scripts, Docker, scripts, and documentation have been updated for the `HsbcFixClient` rename.

**Context:** This is the final validation prompt. Run the full build and test suite to confirm the rename is complete and correct with no regressions.

**Tasks:**

1. **Run the full build and test suite:**
   ```
   ./gradlew --no-daemon clean build test
   ```
   The build **must pass** (`BUILD SUCCESSFUL`) with all tests green. Fix any failures before continuing.

   Common failure categories to look for and fix:
   - Compilation errors from missed `TheFix` → `HsbcFix` class-name substitutions in source or test files.
   - Test assertion failures where strings like `"TheFixClient"`, `"thefix-token"`, `"THEFIX_TRDR01"`, or `"logs/thefixclient/"` are compared but not yet updated.
   - Gradle task not found errors caused by leftover `:TheFixClient:` references in scripts or CI configs.
   - `ClassNotFoundException` or `NoSuchMethodError` caused by missed references in reflective or string-based lookups.

2. **Run the `TheFixSimulator` coverage gate** to confirm the simulator module is unaffected:
   ```
   ./gradlew --no-daemon :TheFixSimulator:cleanTest :TheFixSimulator:test :TheFixSimulator:jacocoTestCoverageVerification --rerun-tasks
   ```
   This must remain `BUILD SUCCESSFUL`.

3. **Run `parallel_validation`** to perform automated code review and CodeQL security scan. Address any valid issues found.

4. **Do a final grep** to confirm no `TheFixClient`, `thefixclient`, `com.insoftu.thefix.client`, or `THEFIX_` references remain in non-generated tracked files:
   ```
   git grep -i "TheFixClient\|thefixclient\|com\.insoftu\.thefix\|THEFIX_" -- \
     '*.java' '*.kt' '*.kts' '*.sh' '*.md' '*.xml' '*.properties' '*.html' '*.js' '*.json' '*.yml' '*.yaml'
   ```
   If any hits are found that are not from generated code or the `fix_client_rewrite_prompts.md` document itself, fix them.

5. Use `report_progress` to commit and push with the message: `chore: final validation — HsbcFixClient rename complete`.

**When done**, confirm:
- All tests pass.
- The coverage gate is green.
- The `parallel_validation` scan passed (or list any issues accepted as false positives with justification).
- The final grep returned no unexpected hits.

Then say:  
> "Prompt 8 is complete. The HsbcFixClient rename is fully validated. All prompts in this series are finished."
