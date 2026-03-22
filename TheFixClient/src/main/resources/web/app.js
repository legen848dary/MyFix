import { createApp, computed, onMounted, onUnmounted, reactive, ref, watch } from 'https://unpkg.com/vue@3/dist/vue.esm-browser.prod.js'

const THEME_STORAGE_KEY = 'thefixclient-theme'
const THEME_OPTIONS = Object.freeze(['system', 'dark', 'light', 'light2'])
const REFRESH_FREQUENCY_STORAGE_KEY = 'thefixclient-refresh-frequency'
const DEFAULT_REFRESH_SECONDS = 3
const REFRESH_FREQUENCY_OPTIONS = Object.freeze([1, 3, 5])
const PAGE_OPTIONS = [
  { code: 'order-input', label: 'Create FIX message' },
  { code: 'order-blotter', label: 'My Orders' },
  { code: 'settings', label: 'Settings' }
]

const PAGE_PATHS = Object.freeze({
  'order-input': '/order',
  'order-blotter': '/orders',
  settings: '/settings'
})

function normalizePagePath(pathname) {
  if (!pathname || pathname === '/') {
    return '/'
  }
  const normalized = pathname.replace(/\/+$/, '')
  return normalized || '/'
}

function pageCodeFromPath(pathname) {
  switch (normalizePagePath(pathname)) {
    case '/':
    case '/index.html':
    case '/home':
    case '/order':
      return 'order-input'
    case '/orders':
    case '/blotter':
      return 'order-blotter'
    case '/settings':
      return 'settings'
    default:
      return ''
  }
}

function pagePathForCode(pageCode) {
  return PAGE_PATHS[pageCode] || PAGE_PATHS['order-input']
}

function defaultRefreshFrequencyMap() {
  return PAGE_OPTIONS.reduce((map, page) => {
    map[page.code] = DEFAULT_REFRESH_SECONDS
    return map
  }, {})
}

function sanitizeRefreshFrequency(seconds) {
  return REFRESH_FREQUENCY_OPTIONS.includes(Number(seconds)) ? Number(seconds) : DEFAULT_REFRESH_SECONDS
}

function loadRefreshFrequencyMap() {
  const defaults = defaultRefreshFrequencyMap()
  try {
    const raw = window.localStorage.getItem(REFRESH_FREQUENCY_STORAGE_KEY)
    if (!raw) {
      return defaults
    }
    const parsed = JSON.parse(raw)
    return PAGE_OPTIONS.reduce((map, page) => {
      map[page.code] = sanitizeRefreshFrequency(parsed?.[page.code])
      return map
    }, { ...defaults })
  } catch (error) {
    return defaults
  }
}

createApp({
  template: `
    <div class="shell">
      <header class="topbar">
        <div class="brand">
          <div class="brand__mark">TF</div>
          <div>
            <p class="brand__eyebrow">Order workstation</p>
            <h1 class="brand__title">{{ overview.applicationName || 'TheFixClient' }}</h1>
          </div>
        </div>

        <div class="topbar__actions">
          <div class="topbar__controls">
            <div class="menu-wrap">
              <button class="menu-button" :aria-expanded="menuOpen ? 'true' : 'false'" @click.stop="toggleMenu">
                <span class="menu-button__label">Menu</span>
                <span class="menu-button__value">{{ currentPageLabel }}</span>
                <span class="menu-button__caret" :class="{ 'menu-button__caret--open': menuOpen }">▾</span>
              </button>
              <div v-if="menuOpen" class="menu-panel">
                <button
                  v-for="page in pageOptions"
                  :key="page.code"
                  class="menu-panel__item"
                  :class="{ 'menu-panel__item--active': activePage === page.code }"
                  @click="openPage(page.code)">
                  {{ page.label }}
                </button>
              </div>
            </div>

            <label class="theme-select" aria-label="Theme selector">
              <span class="theme-select__label">Theme</span>
              <select v-model="theme" class="theme-select__control">
                <option value="system">System</option>
                <option value="dark">Dark</option>
                <option value="light">Light</option>
                <option value="light2">Light2</option>
              </select>
            </label>

            <label class="theme-select refresh-select" aria-label="Refresh frequency selector">
              <span class="theme-select__label">Refresh every</span>
              <select v-model.number="activeRefreshSeconds" class="theme-select__control">
                <option v-for="seconds in refreshFrequencyOptions" :key="seconds" :value="seconds">
                  {{ seconds }} second{{ seconds === 1 ? '' : 's' }}
                </option>
              </select>
            </label>
          </div>

          <div class="status-indicator" :class="session.connected ? 'status-indicator--live' : 'status-indicator--warn'">
            <span class="status-indicator__dot"></span>
            <span>{{ session.connected ? 'FIX LIVE' : 'FIX IDLE' }}</span>
          </div>
          <div class="status-indicator" :class="session.autoFlowActive ? 'status-indicator--flow' : 'status-indicator--idle'">
            <span class="status-indicator__dot"></span>
            <span>{{ session.autoFlowActive ? session.autoFlowDescriptor : 'AUTO FLOW IDLE' }}</span>
          </div>
        </div>
      </header>

      <main v-if="activePage === 'order-input'" class="page-grid">
        <section class="stack">
          <article class="panel panel--compact panel--metrics">
            <div class="panel__header panel__header--metrics">
              <div>
                <h2 class="panel__title">Live metrics</h2>
              </div>
              <span class="chip">{{ session.connected ? 'Live' : 'Standby' }}</span>
            </div>
            <div class="panel__body panel__body--metrics">
              <div class="metrics-strip metrics-strip--compact">
                <div class="compact-card compact-card--metric-tile">
                  <p class="eyebrow">Ready</p>
                  <p class="compact-card__value">{{ kpis.readyState }}</p>
                  <p class="compact-card__copy">{{ kpis.sessionUptime }}</p>
                </div>
                <div class="compact-card compact-card--metric-tile">
                  <p class="eyebrow">Sent</p>
                  <p class="compact-card__value">{{ kpis.sentOrders }}</p>
                  <p class="compact-card__copy">Orders</p>
                </div>
                <div class="compact-card compact-card--metric-tile">
                  <p class="eyebrow">Exec</p>
                  <p class="compact-card__value">{{ kpis.executionReports }}</p>
                  <p class="compact-card__copy">Reports</p>
                </div>
                <div class="compact-card compact-card--metric-tile">
                  <p class="eyebrow">Reject/Cancel</p>
                  <p class="compact-card__value">{{ kpis.rejects }} / {{ kpis.sendFailures }}</p>
                  <p class="compact-card__copy">Count</p>
                </div>
              </div>
            </div>
          </article>

          <article class="panel panel--focus">
            <div class="panel__header">
              <div>
                <h2 class="panel__title">Create FIX message</h2>
                <p class="panel__copy">Build live FIX messages with version-aware tags, custom FIDs, and bulk NOS routing from one operator surface.</p>
              </div>
              <span class="chip">{{ preview.status || 'Draft ready' }}</span>
            </div>

            <div class="workflow-toolbar">
              <div class="workflow-toolbar__row workflow-toolbar__row--templates">
                <div class="workflow-toolbar__templates">
                  <label class="template-picker" aria-label="Saved FIX message templates">
                    <span class="template-picker__label">Saved templates</span>
                    <select v-model="selectedTemplateId" class="template-picker__control" @change="applySelectedTemplate">
                      <option value="">Select template</option>
                      <option v-for="template in messageTemplates" :key="template.id" :value="String(template.id)">
                        {{ template.name }}
                      </option>
                    </select>
                  </label>
                  <div class="field field--template-name">
                    <span class="field__label">Template name</span>
                    <input v-model="templateNameDraft" placeholder="Growth buy USD limit" />
                  </div>
                  <button class="button button--soft" @click="saveCurrentTemplate" :disabled="busyAction === 'save-template'">
                    {{ busyAction === 'save-template' ? 'Saving…' : 'Save template' }}
                  </button>
                </div>
              </div>
              <div class="workflow-toolbar__row workflow-toolbar__row--actions">
                <div class="tab-bar tab-bar--inline">
                  <button class="tab" :class="{ 'tab--active': activeMode === 'single' }" @click="activeMode = 'single'">Single Message</button>
                  <button class="tab" :class="{ 'tab--active': activeMode === 'bulk' }" @click="activeMode = 'bulk'">Bulk NOS</button>
                </div>
                <div class="workflow-toolbar__actions">
                  <button v-if="activeMode === 'single'" class="button button--primary" @click="sendTicket" :disabled="busyAction === 'send'">Send FIX message</button>
                  <template v-else-if="selectedMessageType?.supportsBulk">
                    <button class="button button--primary" @click="startBulkFlow" :disabled="busyAction === 'flow-start'">Start bulk flow</button>
                    <button class="button button--ghost" @click="stopBulkFlow" :disabled="busyAction === 'flow-stop'">Stop bulk flow</button>
                  </template>
                  <span v-else class="chip">Bulk flow supports NOS only</span>
                </div>
              </div>
            </div>

            <div class="panel__body">
              <div class="order-grid order-grid--comfortable order-grid--trade-ticket">
                <div class="field field--trade-ticket">
                  <span class="field__label">Message type</span>
                  <select v-model="orderDraft.messageType">
                    <option v-for="messageType in messageTypes" :key="messageType.code" :value="messageType.code">{{ messageType.label }}</option>
                  </select>
                </div>
                <div class="field field--trade-ticket">
                  <span class="field__label">ClOrdID</span>
                  <input v-model="orderDraft.clOrdId" placeholder="Auto-generated if blank" />
                </div>
                <div v-if="selectedMessageType?.requiresOrigClOrdId" class="field field--trade-ticket">
                  <span class="field__label">OrigClOrdID</span>
                  <input v-model="orderDraft.origClOrdId" placeholder="Existing ClOrdID to amend/cancel" />
                </div>
                <div class="field field--trade-ticket">
                  <span class="field__label">Region</span>
                  <select v-model="orderDraft.region" @change="onRegionChange">
                    <option v-for="region in regions" :key="region.code" :value="region.code">{{ region.label }}</option>
                  </select>
                </div>
                <div class="field field--trade-ticket">
                  <span class="field__label">Market</span>
                  <select v-model="orderDraft.market" @change="onMarketChange">
                    <option v-for="market in availableMarkets" :key="market.code" :value="market.code">{{ market.label }} · {{ market.currency }}</option>
                  </select>
                </div>
                <div class="field field--trade-ticket">
                  <span class="field__label">Symbol</span>
                  <input v-model="orderDraft.symbol" placeholder="AAPL" />
                </div>
                <div class="field field--trade-ticket">
                  <span class="field__label">Side</span>
                  <select v-model="orderDraft.side">
                    <option v-for="side in sides" :key="side.code" :value="side.code">{{ side.label }}</option>
                  </select>
                </div>
                <div class="field field--trade-ticket">
                  <span class="field__label">Quantity</span>
                  <input v-model.number="orderDraft.quantity" type="number" min="0" step="1" />
                </div>
                <div v-if="orderDraft.messageType !== 'ORDER_CANCEL_REQUEST'" class="field field--trade-ticket">
                  <span class="field__label">Order type</span>
                  <select v-model="orderDraft.orderType">
                    <option v-for="orderType in orderTypes" :key="orderType.code" :value="orderType.code">{{ orderType.label }}</option>
                  </select>
                </div>
                <div v-if="orderDraft.messageType !== 'ORDER_CANCEL_REQUEST'" class="field field--trade-ticket">
                  <span class="field__label">Time in force</span>
                  <select v-model="orderDraft.timeInForce">
                    <option v-for="tif in timeInForces" :key="tif.code" :value="tif.code">{{ tif.label }}</option>
                  </select>
                </div>
                <div v-if="orderDraft.messageType !== 'ORDER_CANCEL_REQUEST'" class="field field--trade-ticket">
                  <span class="field__label">Price type</span>
                  <select v-model="orderDraft.priceType">
                    <option v-for="priceType in priceTypes" :key="priceType.code" :value="priceType.code">{{ priceType.label }}</option>
                  </select>
                </div>
                <div class="field field--trade-ticket">
                  <span class="field__label">Currency</span>
                  <input v-model="orderDraft.currency" maxlength="3" />
                </div>
                <div v-if="orderDraft.messageType !== 'ORDER_CANCEL_REQUEST'" class="field field--trade-ticket">
                  <span class="field__label">{{ needsLimitPrice ? 'Limit price' : 'Reference price' }}</span>
                  <input v-model.number="orderDraft.price" type="number" min="0" step="0.01" />
                </div>
                <div class="field field--trade-ticket" v-if="orderDraft.messageType !== 'ORDER_CANCEL_REQUEST' && needsStopPrice">
                  <span class="field__label">Stop price</span>
                  <input v-model.number="orderDraft.stopPrice" type="number" min="0" step="0.01" />
                </div>
                <div class="field field--span-2">
                  <span class="field__label">Additional FIX tags</span>
                  <div class="button-row">
                    <select v-model="selectedSuggestedTagKey">
                      <option value="">Select a suggested {{ activeFixVersionLabel }} tag…</option>
                      <option v-for="tag in availableSuggestedTags" :key="tag.tag + '-' + tag.name" :value="tag.tag + ':' + tag.name">{{ tag.tag }} · {{ tag.name }}{{ tag.type ? ' · ' + tag.type : '' }}</option>
                    </select>
                    <button class="button button--ghost" @click="addSuggestedTag" :disabled="!selectedSuggestedTagKey">Add suggested tag</button>
                    <button class="button button--soft" @click="addCustomTag">Add custom FID</button>
                  </div>
                  <span class="field__hint">Current dictionary: {{ activeFixVersionLabel }}. Use standard FIX tags from the selected version or any custom numeric tag.</span>
                </div>
              </div>

              <div v-if="orderDraft.additionalTags.length" class="stack" style="margin-top: 18px; gap: 12px;">
                <div v-for="(tag, index) in orderDraft.additionalTags" :key="tag.tag + '-' + index" class="order-grid order-grid--comfortable" style="align-items: end;">
                  <div class="field">
                    <span class="field__label">Tag</span>
                    <input v-model.number="tag.tag" type="number" min="1" step="1" placeholder="9001" />
                  </div>
                  <div class="field">
                    <span class="field__label">Name</span>
                    <input v-model="tag.name" placeholder="CustomFID" />
                  </div>
                  <div class="field field--span-2">
                    <span class="field__label">Value</span>
                    <input v-model="tag.value" placeholder="FIX tag value" />
                  </div>
                  <div class="field">
                    <button class="button button--ghost" @click="removeAdditionalTag(index)">Remove</button>
                  </div>
                </div>
              </div>

              <div v-if="activeMode === 'bulk' && selectedMessageType?.supportsBulk" class="bulk-grid bulk-grid--comfortable" style="margin-top: 18px;">
                <div class="field field--span-2">
                  <span class="field__label">Bulk mode</span>
                  <div class="segmented">
                    <button
                      v-for="mode in bulkModes"
                      :key="mode.code"
                      class="segmented__option"
                      :class="{ 'segmented__option--active': bulkDraft.bulkMode === mode.code }"
                      @click="bulkDraft.bulkMode = mode.code">
                      {{ mode.label }}
                    </button>
                  </div>
                  <span class="field__hint">{{ activeBulkMode?.description || 'Choose how the workstation should schedule the bulk run.' }}</span>
                </div>
                <div class="field">
                  <span class="field__label">Total orders</span>
                  <input v-model.number="bulkDraft.totalOrders" type="number" min="0" step="1" />
                </div>
                <div class="field" v-if="bulkDraft.bulkMode === 'FIXED_RATE'">
                  <span class="field__label">Rate per second</span>
                  <input v-model.number="bulkDraft.ratePerSecond" type="number" min="1" step="1" />
                </div>
                <template v-else>
                  <div class="field">
                    <span class="field__label">Burst size</span>
                    <input v-model.number="bulkDraft.burstSize" type="number" min="1" step="1" />
                  </div>
                  <div class="field">
                    <span class="field__label">Burst interval (ms)</span>
                    <input v-model.number="bulkDraft.burstIntervalMs" type="number" min="1" step="50" />
                  </div>
                </template>
              </div>

              <div v-else-if="activeMode === 'bulk'" class="compact-card" style="margin-top: 18px;">
                <p class="eyebrow">Bulk routing</p>
                <p class="compact-card__copy">Bulk mode is available for New Order Single only. Switch the message type back to NOS to run bulk traffic.</p>
              </div>

              <ul v-if="previewWarnings.length" class="warning-list" style="margin-top: 18px;">
                <li v-for="warning in previewWarnings" :key="warning">{{ warning }}</li>
              </ul>
            </div>
          </article>
        </section>

        <aside class="stack">
          <article class="panel panel--compact">
            <div class="panel__header">
              <div>
                <h2 class="panel__title">Session Control</h2>
                <p class="panel__copy">Connection controls and current FIX session profile.</p>
              </div>
              <span class="chip">{{ session.profileName || settingsState.activeProfileName }}</span>
            </div>
            <div class="panel__body compact-stack">
              <div class="compact-card">
                <p class="eyebrow">Session</p>
                <p class="compact-card__value">{{ session.connected ? 'Connected' : 'Standby' }}</p>
                <p class="compact-card__copy">{{ session.host }}:{{ session.port }} · {{ session.fixVersionLabel || session.beginString }}</p>
              </div>
              <div class="compact-card">
                <p class="eyebrow">Comp IDs</p>
                <p class="compact-card__value mono">{{ session.senderCompId }} → {{ session.targetCompId }}</p>
                <p class="compact-card__copy">{{ session.pendingProfileChange ? 'Reconnect required to apply selected profile.' : session.status }}</p>
              </div>
              <div class="compact-actions compact-actions--stacked">
                <button
                  class="button"
                  :class="session.connected ? 'button--danger' : 'button--success'"
                  @click="session.connected ? disconnectSession() : connectSession()"
                  :disabled="sessionActionBusy">
                  {{ sessionButtonLabel }}
                </button>
              </div>
            </div>
          </article>
        </aside>
      </main>

      <main v-else-if="activePage === 'order-blotter'" class="workspace workspace--single">
        <section class="stack">
          <article class="panel orders-panel">
            <div class="panel__header orders-panel__header">
              <div>
                <h2 class="panel__title">My Orders</h2>
              </div>
              <span class="orders-panel__count mono">{{ recentOrders.length }} recent</span>
            </div>
            <div class="orders-table__scroll">
              <table class="orders-table orders-table--simulator">
                  <thead>
                    <tr>
                      <th class="text-left">Time</th>
                      <th class="text-left">Symbol</th>
                      <th class="text-left">Side</th>
                      <th class="text-right">Qty</th>
                      <th class="text-right">Price</th>
                      <th class="text-left">ExecType</th>
                      <th class="text-left">Status</th>
                      <th class="text-left">Behavior</th>
                      <th class="text-right">ClOrdID</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="order in recentOrders"
                        :key="order.clOrdId"
                        :class="orderRowClass(order)">
                      <td class="mono text-slate-400">{{ order.time }}</td>
                      <td class="mono text-white strong">{{ order.symbol }}</td>
                      <td :class="blotterSideClass(order.side)">{{ order.side }}</td>
                      <td class="mono text-right text-white">{{ order.quantity }}</td>
                      <td class="mono text-right text-slate-300">{{ order.limitPrice }}</td>
                      <td>
                        <span class="exec-badge" :class="execTypeBadge(order.execType)">{{ order.execType }}</span>
                      </td>
                      <td :class="blotterStatusClass(order.status)">{{ order.status }}</td>
                      <td :class="blotterSourceClass(order.source)">{{ order.source }}</td>
                      <td class="mono text-right text-slate-300">{{ order.clOrdId }}</td>
                    </tr>
                    <tr v-if="!recentOrders.length">
                      <td colspan="9" class="orders-table__empty">Waiting for orders…</td>
                    </tr>
                  </tbody>
                </table>
            </div>
          </article>
        </section>
      </main>

      <main v-else class="workspace workspace--single">
        <section class="stack">
          <article class="panel">
            <div class="panel__header">
              <div>
                <h2 class="panel__title">Settings</h2>
                <p class="panel__copy">Maintain default and custom FIX session profiles, plus the filesystem location where profiles are stored.</p>
              </div>
              <span class="chip">Active · {{ settingsState.activeProfileName }}</span>
            </div>
            <div class="panel__body settings-grid">
              <section class="stack">
                <div class="compact-card">
                  <p class="eyebrow">Profile storage</p>
                  <p class="compact-card__copy">Profiles are loaded from and saved to the path below.</p>
                  <div class="field" style="margin-top: 12px;">
                    <span class="field__label">Storage path</span>
                    <input v-model="storagePathDraft" />
                  </div>
                  <p class="compact-card__copy" style="margin-top: 10px;">Default temp path: {{ settingsState.defaultStoragePath }}</p>
                  <div class="button-row">
                    <button class="button" @click="applyStoragePath" :disabled="busyAction === 'storage-path'">Apply path</button>
                  </div>
                </div>

                <div class="compact-card">
                  <p class="eyebrow">Profiles</p>
                  <div class="field" style="margin-top: 12px;">
                    <span class="field__label">Available profiles</span>
                    <select v-model="selectedProfileName">
                      <option v-for="profile in settingsState.profiles" :key="profile.name" :value="profile.name">{{ profile.name }}</option>
                    </select>
                  </div>
                  <div class="button-row">
                    <button class="button" @click="loadSelectedProfile">Load profile</button>
                    <button class="button button--ghost" @click="activateSelectedProfile" :disabled="busyAction === 'activate-profile'">Activate</button>
                    <button class="button button--soft" @click="startNewProfile">New profile</button>
                  </div>
                </div>
              </section>

              <section class="panel panel--compact panel--subtle">
                <div class="panel__header">
                  <div>
                    <h2 class="panel__title">Profile details</h2>
                    <p class="panel__copy">Edit the active draft, then save it as the default profile or a new named profile.</p>
                  </div>
                </div>
                <div class="panel__body" @input.capture="markSettingsDirty" @change.capture="markSettingsDirty">
                  <div class="order-grid order-grid--comfortable">
                    <div class="field field--span-2">
                      <span class="field__label">Profile name</span>
                      <input v-model="settingsDraft.name" />
                    </div>
                    <div class="field">
                      <span class="field__label">Sender Comp ID</span>
                      <input v-model="settingsDraft.senderCompId" />
                    </div>
                    <div class="field">
                      <span class="field__label">Target Comp ID</span>
                      <input v-model="settingsDraft.targetCompId" />
                    </div>
                    <div class="field">
                      <span class="field__label">Target host</span>
                      <input v-model="settingsDraft.fixHost" />
                    </div>
                    <div class="field">
                      <span class="field__label">Target port</span>
                      <input v-model.number="settingsDraft.fixPort" type="number" min="1" max="65535" />
                    </div>
                    <div class="field">
                      <span class="field__label">FIX version</span>
                      <select v-model="settingsDraft.fixVersionCode">
                        <option v-for="option in fixVersionOptions" :key="option.code" :value="option.code">{{ option.label }}</option>
                      </select>
                    </div>
                    <div class="field">
                      <span class="field__label">ResetOnLogon</span>
                      <select v-model="settingsDraft.resetOnLogon">
                        <option :value="true">Yes</option>
                        <option :value="false">No</option>
                      </select>
                    </div>
                    <div class="field">
                      <span class="field__label">Session start</span>
                      <input v-model="settingsDraft.sessionStartTime" placeholder="00:00:00" />
                    </div>
                    <div class="field">
                      <span class="field__label">Session end</span>
                      <input v-model="settingsDraft.sessionEndTime" placeholder="00:00:00" />
                    </div>
                    <div class="field">
                      <span class="field__label">HeartBtInt</span>
                      <input v-model.number="settingsDraft.heartBtIntSec" type="number" min="1" />
                    </div>
                    <div class="field">
                      <span class="field__label">Reconnect interval</span>
                      <input v-model.number="settingsDraft.reconnectIntervalSec" type="number" min="1" />
                    </div>
                  </div>

                  <div class="button-row">
                    <button class="button button--primary" @click="saveSettingsProfile" :disabled="busyAction === 'save-profile'">Save profile</button>
                    <button class="button button--ghost" @click="resetSettingsDraft">Reset draft</button>
                  </div>
                </div>
              </section>
            </div>
          </article>
        </section>
      </main>
    </div>
  `,

  setup() {
    const overview = reactive({
      applicationName: 'TheFixClient'
    })

    const session = reactive({
      connected: false,
      status: 'Loading…',
      host: 'localhost',
      port: 9880,
      beginString: 'FIX.4.4',
      senderCompId: 'THEFIX_TRDR01',
      targetCompId: 'LLEXSIM',
      profileName: 'Default profile',
      activeProfileName: 'Default profile',
      pendingProfileChange: false,
      fixVersionCode: 'FIX_44',
      fixVersionLabel: 'FIX 4.4',
      mode: 'QuickFIX/J initiator',
      autoFlowActive: false,
      autoFlowRate: 0,
      autoFlowMode: 'OFF',
      autoFlowDescriptor: 'Inactive',
      rawMessageLoggingEnabled: false
    })

    const kpis = reactive({
      readyState: 'Loading…',
      pulseChecks: 0,
      openOrders: 0,
      sentOrders: 0,
      executionReports: 0,
      rejects: 0,
      sendFailures: 0,
      sessionUptime: '—'
    })

    const referenceData = reactive({
      regions: [],
      markets: [],
      sides: [],
      orderTypes: [],
      priceTypes: [],
      timeInForces: [],
      bulkModes: [],
      messageTypes: [],
      fixVersions: []
    })

    const fixMetadata = reactive({
      versions: [],
      messageTypes: []
    })

    const settingsState = reactive({
      storagePath: '',
      defaultStoragePath: '',
      activeProfileName: 'Default profile',
      profiles: [],
      fixVersionOptions: []
    })

    const recentEvents = ref([])
    const recentOrders = ref([])
    const messageTemplates = ref([])
    const apiStatus = ref('Connecting…')
    const busyAction = ref('')
    const sessionActionPending = ref('')
    const refreshHandle = ref(null)
    const overviewRefreshPending = ref(false)
    const previewTimer = ref(null)
    const activePage = ref(pageCodeFromPath(window.location.pathname) || 'order-input')
    const activeMode = ref('single')
    const menuOpen = ref(false)
    const normalizeTheme = (value) => THEME_OPTIONS.includes(value) ? value : 'system'
    const theme = ref(normalizeTheme(window.localStorage.getItem(THEME_STORAGE_KEY) || 'system'))
    const refreshFrequencyByPage = reactive(loadRefreshFrequencyMap())
    const draftInitialized = ref(false)
    const settingsInitialized = ref(false)
    const settingsDirty = ref(false)
    const suggestedSymbol = ref('AAPL')
    const selectedProfileName = ref(TheFixSessionProfileNameFallback())
    const storagePathDraft = ref('')
    const selectedSuggestedTagKey = ref('')
    const selectedTemplateId = ref('')
    const templateNameDraft = ref('')

    const orderDraft = reactive({
      messageType: 'NEW_ORDER_SINGLE',
      clOrdId: '',
      origClOrdId: '',
      region: 'AMERICAS',
      market: 'XNAS',
      symbol: 'AAPL',
      side: 'BUY',
      quantity: 100,
      orderType: 'LIMIT',
      priceType: 'PER_UNIT',
      timeInForce: 'DAY',
      currency: 'USD',
      price: 100.25,
      stopPrice: 0,
      additionalTags: []
    })

    const bulkDraft = reactive({
      bulkMode: 'FIXED_RATE',
      totalOrders: 50,
      ratePerSecond: 25,
      burstSize: 10,
      burstIntervalMs: 1000
    })

    const preview = reactive({
      status: '',
      warnings: []
    })

    const settingsDraft = reactive(emptySettingsDraft())

    const regions = computed(() => referenceData.regions || [])
    const sides = computed(() => referenceData.sides || [])
    const orderTypes = computed(() => referenceData.orderTypes || [])
    const priceTypes = computed(() => referenceData.priceTypes || [])
    const timeInForces = computed(() => referenceData.timeInForces || [])
    const bulkModes = computed(() => referenceData.bulkModes || [])
    const messageTypes = computed(() => referenceData.messageTypes?.length ? referenceData.messageTypes : fixMetadata.messageTypes || [])
    const fixVersionOptions = computed(() => settingsState.fixVersionOptions || referenceData.fixVersions || [])
    const availableMarkets = computed(() => (referenceData.markets || []).filter(market => market.region === orderDraft.region))
    const selectedOrderType = computed(() => orderTypes.value.find(item => item.code === orderDraft.orderType) || null)
    const selectedMessageType = computed(() => messageTypes.value.find(item => item.code === orderDraft.messageType) || null)
    const activeBulkMode = computed(() => bulkModes.value.find(item => item.code === bulkDraft.bulkMode) || null)
    const needsLimitPrice = computed(() => orderDraft.messageType !== 'ORDER_CANCEL_REQUEST' && Boolean(selectedOrderType.value?.requiresLimitPrice))
    const needsStopPrice = computed(() => orderDraft.messageType !== 'ORDER_CANCEL_REQUEST' && Boolean(selectedOrderType.value?.requiresStopPrice))
    const previewWarnings = computed(() => preview.warnings || [])
    const pageOptions = computed(() => PAGE_OPTIONS)
    const currentPageLabel = computed(() => pageOptions.value.find(page => page.code === activePage.value)?.label || 'Menu')
    const refreshFrequencyOptions = computed(() => REFRESH_FREQUENCY_OPTIONS)
    const activeRefreshSeconds = computed({
      get: () => sanitizeRefreshFrequency(refreshFrequencyByPage[activePage.value]),
      set: (seconds) => {
        refreshFrequencyByPage[activePage.value] = sanitizeRefreshFrequency(seconds)
        window.localStorage.setItem(REFRESH_FREQUENCY_STORAGE_KEY, JSON.stringify({ ...refreshFrequencyByPage }))
      }
    })
    const activeFixVersionCode = computed(() => session.fixVersionCode || settingsDraft.fixVersionCode || 'FIX_44')
    const activeFixVersionLabel = computed(() => fixVersionOptions.value.find(option => option.code === activeFixVersionCode.value)?.label || 'FIX 4.4')
    const currentFixDictionary = computed(() => (fixMetadata.versions || []).find(version => version.code === activeFixVersionCode.value) || null)
    const availableSuggestedTags = computed(() => {
      const messageSpecific = currentFixDictionary.value?.messages?.[orderDraft.messageType] || []
      const usedTags = new Set((orderDraft.additionalTags || []).map(tag => Number(tag.tag || 0)))
      return messageSpecific.filter(tag => !usedTags.has(Number(tag.tag || 0)))
    })
    const sessionActionBusy = computed(() => sessionActionPending.value === 'connect' || sessionActionPending.value === 'disconnect')
    const sessionButtonLabel = computed(() => {
      if (sessionActionPending.value === 'connect') return 'Connecting…'
      if (sessionActionPending.value === 'disconnect') return 'Disconnecting…'
      return session.connected ? 'Disconnect' : 'Connect'
    })
    const execTypeBadge = (execType) => {
      const normalized = String(execType || '').trim().toUpperCase()
      if (normalized === 'FILL' || normalized === 'TRADE') {
        return 'exec-badge--success'
      }
      if (normalized === 'PARTIAL_FILL' || normalized === 'PARTIAL') {
        return 'exec-badge--warn'
      }
      if (normalized === 'REJECT' || normalized === 'REJECTED' || normalized === 'CANCELED' || normalized === 'CANCELLED' || normalized === 'CANCEL' || normalized === 'EXPIRED') {
        return 'exec-badge--danger'
      }
      return 'exec-badge--neutral'
    }
    const orderRowClass = (order) => {
      return ['order-row']
    }
    const blotterSideClass = (side) => {
      const normalized = String(side || '').trim().toUpperCase()
      return normalized === 'SELL' || normalized === 'SELL_SHORT'
        ? 'text-danger strong'
        : 'text-brand strong'
    }
    const blotterStatusClass = (status) => {
      const normalized = String(status || '').trim().toUpperCase()
      if (normalized.includes('REJECT') || normalized.includes('CANCEL') || normalized.includes('EXPIRE')) {
        return 'text-danger'
      }
      if (normalized.includes('FILL') || normalized === 'NEW' || normalized === 'SENT') {
        return 'text-slate-300'
      }
      return 'text-slate-400'
    }
    const blotterSourceClass = (source) => {
      const normalized = String(source || '').trim().toUpperCase()
      if (normalized.includes('AUTO')) {
        return 'text-slate-400'
      }
      return 'text-slate-300'
    }

    const themeMediaQuery = window.matchMedia ? window.matchMedia('(prefers-color-scheme: dark)') : null

    function TheFixSessionProfileNameFallback() {
      return 'Default profile'
    }

    function emptySettingsDraft() {
      return {
        name: 'Default profile',
        senderCompId: 'THEFIX_TRDR01',
        targetCompId: 'LLEXSIM',
        fixHost: 'localhost',
        fixPort: 9880,
        fixVersionCode: 'FIX_44',
        resetOnLogon: true,
        sessionStartTime: '00:00:00',
        sessionEndTime: '00:00:00',
        heartBtIntSec: 30,
        reconnectIntervalSec: 5
      }
    }

    const applyTheme = (nextTheme) => {
      const effectiveTheme = nextTheme === 'system' && themeMediaQuery
        ? (themeMediaQuery.matches ? 'dark' : 'light')
        : nextTheme
      const normalizedTheme = normalizeTheme(nextTheme)
      document.body.setAttribute('data-theme', normalizeTheme(effectiveTheme))
      window.localStorage.setItem(THEME_STORAGE_KEY, normalizedTheme)
    }

    const handleSystemThemeChange = () => {
      if (theme.value === 'system') {
        applyTheme(theme.value)
      }
    }

    const clearOverviewRefresh = () => {
      if (refreshHandle.value) {
        window.clearTimeout(refreshHandle.value)
        refreshHandle.value = null
      }
    }

    const queueOverviewRefresh = (delayMs = activeRefreshSeconds.value * 1000) => {
      clearOverviewRefresh()
      refreshHandle.value = window.setTimeout(() => {
        runOverviewRefreshCycle().catch(() => {})
      }, Math.max(250, Number(delayMs) || activeRefreshSeconds.value * 1000))
    }

    const apiCall = async (url, options = {}) => {
      const response = await fetch(url, {
        headers: { 'Content-Type': 'application/json' },
        ...options
      })
      if (!response.ok) {
        throw new Error(`Request failed: ${response.status}`)
      }
      return response.json()
    }

    const applyFixMetadata = (payload) => {
      Object.assign(fixMetadata, {
        versions: payload?.versions || [],
        messageTypes: payload?.messageTypes || []
      })
    }

    const regionLabel = (code) => regions.value.find(region => region.code === code)?.label || code

    const applyMarketDefaults = (forceSymbol = false) => {
      const market = availableMarkets.value.find(item => item.code === orderDraft.market)
      if (!market) {
        return
      }
      orderDraft.currency = market.currency || orderDraft.currency
      const suggested = market.symbolHints?.[0] || 'AAPL'
      if (forceSymbol || !orderDraft.symbol || orderDraft.symbol === suggestedSymbol.value) {
        orderDraft.symbol = suggested
      }
      suggestedSymbol.value = suggested
    }

    const initializeDrafts = (defaults = {}) => {
      Object.assign(orderDraft, {
        ...orderDraft,
        ...(defaults.order || {})
      })
      orderDraft.additionalTags = Array.isArray(defaults.order?.additionalTags) ? defaults.order.additionalTags : []
      if (defaults.defaultFlowRate) {
        bulkDraft.ratePerSecond = defaults.defaultFlowRate
      }
      if (defaults.bulkMode) {
        bulkDraft.bulkMode = defaults.bulkMode
      }
      applyMarketDefaults(true)
      draftInitialized.value = true
    }

    const syncSettingsDraft = (profile) => {
      Object.assign(settingsDraft, emptySettingsDraft(), profile || {})
      settingsDirty.value = false
    }

    const applySettings = (settings, preserveDraft = true) => {
      Object.assign(settingsState, {
        storagePath: settings?.storagePath || '',
        defaultStoragePath: settings?.defaultStoragePath || '',
        activeProfileName: settings?.activeProfileName || 'Default profile',
        profiles: settings?.profiles || [],
        fixVersionOptions: settings?.fixVersionOptions || []
      })
      if (!selectedProfileName.value || !settingsState.profiles.some(profile => profile.name === selectedProfileName.value)) {
        selectedProfileName.value = settingsState.activeProfileName
      }
      if (!settingsInitialized.value || !preserveDraft || !settingsDirty.value) {
        syncSettingsDraft(settings?.profileDraft || settingsState.profiles.find(profile => profile.name === settingsState.activeProfileName))
        storagePathDraft.value = settingsState.storagePath
      }
      settingsInitialized.value = true
    }

    const applyOverview = (payload, preserveSettingsDraft = true, preservePendingSessionState = false) => {
      Object.assign(overview, {
        applicationName: payload.applicationName
      })
      if (preservePendingSessionState && sessionActionPending.value) {
        Object.assign(session, {
          ...(payload.session || {}),
          connected: session.connected,
          status: session.status
        })
      } else {
        Object.assign(session, payload.session || {})
      }
      Object.assign(kpis, payload.kpis || {})
      Object.assign(referenceData, payload.referenceData || {})
      recentEvents.value = payload.recentEvents || []
      recentOrders.value = payload.recentOrders || []

      if (!draftInitialized.value) {
        initializeDrafts(payload.defaults || {})
      } else if (!availableMarkets.value.find(market => market.code === orderDraft.market) && availableMarkets.value.length) {
        orderDraft.market = availableMarkets.value[0].code
        applyMarketDefaults(true)
      }

      if (payload.settings) {
        applySettings(payload.settings, preserveSettingsDraft)
      }

      apiStatus.value = 'Live'
    }

    const loadOverview = async () => {
      if (overviewRefreshPending.value) {
        return
      }
      overviewRefreshPending.value = true
      try {
        applyOverview(await apiCall('/api/overview'), true, true)
      } catch (error) {
        apiStatus.value = 'Unavailable'
        recentEvents.value = [{
          time: 'now',
          level: 'WARN',
          title: 'Overview unavailable',
          detail: 'The client API could not be reached. Confirm the backend is running.'
        }]
      } finally {
        overviewRefreshPending.value = false
      }
    }

    const applyTemplates = (payload) => {
      const templates = payload?.templates?.items || payload?.items || []
      const previousSelection = String(selectedTemplateId.value || '')
      messageTemplates.value = templates
      if (payload?.savedTemplateId) {
        selectedTemplateId.value = String(payload.savedTemplateId)
      } else if (!templates.some(template => String(template.id) === previousSelection)) {
        selectedTemplateId.value = ''
      }
      if (selectedTemplateId.value) {
        const selectedTemplate = templates.find(template => String(template.id) === String(selectedTemplateId.value))
        if (selectedTemplate) {
          templateNameDraft.value = selectedTemplate.name || templateNameDraft.value
        }
      }
    }

    const loadTemplates = async () => {
      try {
        applyTemplates(await apiCall('/api/templates'))
      } catch (error) {
        console.warn('Unable to load FIX message templates', error)
      }
    }

    const cloneAdditionalTags = (items) => Array.isArray(items)
      ? items.map(tag => ({
        tag: Number(tag?.tag || 0),
        name: tag?.name || '',
        value: tag?.value || '',
        custom: Boolean(tag?.custom)
      }))
      : []

    const applyTemplateDraft = (draft) => {
      if (!draft) {
        return
      }
      Object.assign(orderDraft, {
        messageType: draft.messageType || 'NEW_ORDER_SINGLE',
        clOrdId: draft.clOrdId || '',
        origClOrdId: draft.origClOrdId || '',
        region: draft.region || 'AMERICAS',
        market: draft.market || 'XNAS',
        symbol: draft.symbol || 'AAPL',
        side: draft.side || 'BUY',
        quantity: Number(draft.quantity ?? 100),
        orderType: draft.orderType || 'LIMIT',
        priceType: draft.priceType || 'PER_UNIT',
        timeInForce: draft.timeInForce || 'DAY',
        currency: draft.currency || 'USD',
        price: Number(draft.price ?? 100.25),
        stopPrice: Number(draft.stopPrice ?? 0)
      })
      orderDraft.additionalTags = cloneAdditionalTags(draft.additionalTags)
      selectedSuggestedTagKey.value = ''
    }

    const applySelectedTemplate = () => {
      const selectedTemplate = messageTemplates.value.find(template => String(template.id) === String(selectedTemplateId.value))
      if (!selectedTemplate) {
        return
      }
      templateNameDraft.value = selectedTemplate.name || ''
      applyTemplateDraft(selectedTemplate.draft || {})
    }

    const saveCurrentTemplate = async () => runAction('save-template', async () => {
      const templateName = (templateNameDraft.value || '').trim()
      if (!templateName) {
        window.alert('Template name is required before saving.')
        return
      }
      applyTemplates(await apiCall('/api/templates/save', {
        method: 'POST',
        body: JSON.stringify({
          name: templateName,
          draft: buildOrderPayload()
        })
      }))
    })

    const runOverviewRefreshCycle = async () => {
      await loadOverview()
      queueOverviewRefresh()
    }

    const loadFixMetadata = async () => {
      try {
        applyFixMetadata(await apiCall('/api/fix-metadata'))
      } catch (error) {
        console.warn('Unable to load FIX metadata', error)
      }
    }

    const buildOrderPayload = () => ({
      messageType: orderDraft.messageType,
      clOrdId: (orderDraft.clOrdId || '').trim(),
      origClOrdId: (orderDraft.origClOrdId || '').trim(),
      region: orderDraft.region,
      market: orderDraft.market,
      symbol: (orderDraft.symbol || '').trim(),
      side: orderDraft.side,
      quantity: Number(orderDraft.quantity),
      orderType: orderDraft.orderType,
      priceType: orderDraft.priceType,
      timeInForce: orderDraft.timeInForce,
      currency: (orderDraft.currency || '').trim().toUpperCase(),
      price: Number(orderDraft.price),
      stopPrice: Number(orderDraft.stopPrice || 0),
      additionalTags: (orderDraft.additionalTags || []).map(tag => ({
        tag: Number(tag.tag || 0),
        name: (tag.name || '').trim(),
        value: (tag.value || '').trim(),
        custom: Boolean(tag.custom)
      })).filter(tag => tag.tag || tag.name || tag.value)
    })

    const buildSettingsPayload = () => ({
      name: (settingsDraft.name || '').trim(),
      senderCompId: (settingsDraft.senderCompId || '').trim(),
      targetCompId: (settingsDraft.targetCompId || '').trim(),
      fixHost: (settingsDraft.fixHost || '').trim(),
      fixPort: Number(settingsDraft.fixPort),
      fixVersionCode: settingsDraft.fixVersionCode,
      resetOnLogon: Boolean(settingsDraft.resetOnLogon),
      sessionStartTime: (settingsDraft.sessionStartTime || '').trim(),
      sessionEndTime: (settingsDraft.sessionEndTime || '').trim(),
      heartBtIntSec: Number(settingsDraft.heartBtIntSec),
      reconnectIntervalSec: Number(settingsDraft.reconnectIntervalSec),
      activate: true
    })

    const runAction = async (name, work) => {
      busyAction.value = name
      try {
        await work()
      } finally {
        busyAction.value = ''
      }
    }

    const runSessionAction = async (name, work) => {
      if (sessionActionPending.value) {
        return
      }
      sessionActionPending.value = name
      session.status = name === 'connect' ? 'Connecting…' : 'Disconnecting…'
      try {
        await work()
      } finally {
        sessionActionPending.value = ''
      }
    }

    const previewTicket = async () => {
      const payload = await apiCall('/api/order-ticket/preview', {
        method: 'POST',
        body: JSON.stringify(buildOrderPayload())
      })
      Object.assign(preview, payload)
      return payload
    }

    const schedulePreview = () => {
      if (previewTimer.value) {
        window.clearTimeout(previewTimer.value)
      }
      previewTimer.value = window.setTimeout(() => {
        previewTicket().catch(() => {})
      }, 250)
    }

    const connectSession = async () => runSessionAction('connect', async () => runAction('connect', async () => {
      applyOverview(await apiCall('/api/session/connect', { method: 'POST' }), true)
    }))
    const disconnectSession = async () => runSessionAction('disconnect', async () => runAction('disconnect', async () => {
      if (session.autoFlowActive) {
        applyOverview(await apiCall('/api/order-flow/stop', { method: 'POST' }), true)
      }
      applyOverview(await apiCall('/api/session/disconnect', { method: 'POST' }), true)
    }))
    const pulseTest = async () => runAction('pulse', async () => applyOverview(await apiCall('/api/session/pulse-test', { method: 'POST' }), true))

    const sendTicket = async () => runAction('send', async () => {
      await previewTicket()
      applyOverview(await apiCall('/api/order-ticket/send', {
        method: 'POST',
        body: JSON.stringify(buildOrderPayload())
      }), true)
      await loadTemplates()
    })

    const startBulkFlow = async () => runAction('flow-start', async () => {
      await previewTicket()
      applyOverview(await apiCall('/api/order-flow/start', {
        method: 'POST',
        body: JSON.stringify({
          ...buildOrderPayload(),
          bulkMode: bulkDraft.bulkMode,
          totalOrders: Number(bulkDraft.totalOrders || 0),
          ratePerSecond: Number(bulkDraft.ratePerSecond || 0),
          burstSize: Number(bulkDraft.burstSize || 0),
          burstIntervalMs: Number(bulkDraft.burstIntervalMs || 0)
        })
      }), true)
    })

    const stopBulkFlow = async () => runAction('flow-stop', async () => applyOverview(await apiCall('/api/order-flow/stop', { method: 'POST' }), true))

    const saveSettingsProfile = async () => runAction('save-profile', async () => {
      const payload = await apiCall('/api/settings/profiles/save', {
        method: 'POST',
        body: JSON.stringify(buildSettingsPayload())
      })
      applyOverview(payload, false)
      selectedProfileName.value = settingsDraft.name
    })

    const activateSelectedProfile = async () => runAction('activate-profile', async () => {
      const payload = await apiCall('/api/settings/profiles/activate', {
        method: 'POST',
        body: JSON.stringify({ name: selectedProfileName.value })
      })
      applyOverview(payload, false)
    })

    const applyStoragePath = async () => runAction('storage-path', async () => {
      const payload = await apiCall('/api/settings/storage-path', {
        method: 'POST',
        body: JSON.stringify({ storagePath: storagePathDraft.value })
      })
      applyOverview(payload, false)
    })

    const loadSelectedProfile = () => {
      const profile = settingsState.profiles.find(item => item.name === selectedProfileName.value)
      if (profile) {
        syncSettingsDraft(profile)
      }
    }

    const resetSettingsDraft = () => {
      const profile = settingsState.profiles.find(item => item.name === settingsState.activeProfileName)
      syncSettingsDraft(profile)
      storagePathDraft.value = settingsState.storagePath
    }

    const startNewProfile = () => {
      const activeProfile = settingsState.profiles.find(item => item.name === settingsState.activeProfileName)
      syncSettingsDraft({ ...(activeProfile || emptySettingsDraft()), name: 'New profile' })
      settingsDirty.value = true
    }

    const markSettingsDirty = () => {
      settingsDirty.value = true
    }

    const addCustomTag = () => {
      orderDraft.additionalTags.push({ tag: '', name: '', value: '', custom: true })
    }

    const addSuggestedTag = () => {
      if (!selectedSuggestedTagKey.value) {
        return
      }
      const [tagValue, ...nameParts] = selectedSuggestedTagKey.value.split(':')
      orderDraft.additionalTags.push({
        tag: Number(tagValue || 0),
        name: nameParts.join(':') || '',
        value: '',
        custom: false
      })
      selectedSuggestedTagKey.value = ''
    }

    const removeAdditionalTag = (index) => {
      orderDraft.additionalTags.splice(index, 1)
    }

    const onRegionChange = () => {
      if (!availableMarkets.value.find(market => market.code === orderDraft.market) && availableMarkets.value.length) {
        orderDraft.market = availableMarkets.value[0].code
      }
      applyMarketDefaults(true)
      schedulePreview()
    }

    const onMarketChange = () => {
      applyMarketDefaults(false)
      schedulePreview()
    }

    const syncPageFromLocation = (replaceAlias = false) => {
      const currentPath = normalizePagePath(window.location.pathname)
      const resolvedPage = pageCodeFromPath(currentPath)
      if (!resolvedPage) {
        activePage.value = 'order-input'
        window.history.replaceState({ page: 'order-input' }, '', pagePathForCode('order-input'))
        return
      }
      activePage.value = resolvedPage
      if (replaceAlias && (currentPath === '/index.html' || currentPath === '/blotter')) {
        window.history.replaceState({ page: resolvedPage }, '', pagePathForCode(resolvedPage))
      }
    }

    const navigateToPage = (pageCode, replace = false) => {
      const nextPath = pagePathForCode(pageCode)
      activePage.value = pageCode
      if (normalizePagePath(window.location.pathname) !== nextPath) {
        const method = replace ? 'replaceState' : 'pushState'
        window.history[method]({ page: pageCode }, '', nextPath)
      }
    }

    const handlePopState = () => {
      syncPageFromLocation(false)
    }

    const toggleMenu = () => {
      menuOpen.value = !menuOpen.value
    }

    const openPage = (pageCode) => {
      navigateToPage(pageCode)
      menuOpen.value = false
    }

    const closeMenu = () => {
      menuOpen.value = false
    }

    watch(theme, applyTheme, { immediate: true })

    watch(() => activePage.value, () => {
      runOverviewRefreshCycle().catch(() => {})
    })

    watch(() => settingsState.activeProfileName, () => {
      selectedTemplateId.value = ''
      templateNameDraft.value = ''
      loadTemplates().catch(() => {})
    })

    watch(activeRefreshSeconds, () => {
      queueOverviewRefresh()
    })

    watch(() => orderDraft.orderType, () => {
      if (!needsStopPrice.value) {
        orderDraft.stopPrice = 0
      }
      schedulePreview()
    })

    watch(() => orderDraft.messageType, (nextMessageType) => {
      selectedSuggestedTagKey.value = ''
      if (nextMessageType === 'ORDER_CANCEL_REQUEST') {
        orderDraft.stopPrice = 0
      }
      if (activeMode.value === 'bulk' && nextMessageType !== 'NEW_ORDER_SINGLE') {
        activeMode.value = 'single'
      }
      schedulePreview()
    })

    watch(orderDraft, schedulePreview, { deep: true })

    onMounted(async () => {
      if (themeMediaQuery?.addEventListener) {
        themeMediaQuery.addEventListener('change', handleSystemThemeChange)
      } else if (themeMediaQuery?.addListener) {
        themeMediaQuery.addListener(handleSystemThemeChange)
      }
      window.addEventListener('popstate', handlePopState)
      window.addEventListener('click', closeMenu)
      syncPageFromLocation(true)
      await loadFixMetadata()
      await loadOverview()
      await loadTemplates()
      await previewTicket()
      queueOverviewRefresh()
    })

    onUnmounted(() => {
      if (previewTimer.value) {
        window.clearTimeout(previewTimer.value)
      }
      clearOverviewRefresh()
      window.removeEventListener('popstate', handlePopState)
      window.removeEventListener('click', closeMenu)
      if (themeMediaQuery?.removeEventListener) {
        themeMediaQuery.removeEventListener('change', handleSystemThemeChange)
      } else if (themeMediaQuery?.removeListener) {
        themeMediaQuery.removeListener(handleSystemThemeChange)
      }
    })

    return {
      overview,
      session,
      kpis,
      recentOrders,
      messageTemplates,
      apiStatus,
      activePage,
      activeMode,
      menuOpen,
      pageOptions,
      currentPageLabel,
      busyAction,
      theme,
      refreshFrequencyOptions,
      activeRefreshSeconds,
      orderDraft,
      bulkDraft,
      preview,
      previewWarnings,
      regions,
      sides,
      messageTypes,
      orderTypes,
      priceTypes,
      timeInForces,
      bulkModes,
      selectedMessageType,
      fixVersionOptions,
      activeFixVersionLabel,
      availableSuggestedTags,
      availableMarkets,
      activeBulkMode,
      needsLimitPrice,
      needsStopPrice,
      selectedSuggestedTagKey,
      selectedTemplateId,
      templateNameDraft,
      sessionActionBusy,
      sessionButtonLabel,
      execTypeBadge,
      blotterSideClass,
      blotterStatusClass,
      blotterSourceClass,
      orderRowClass,
      settingsState,
      settingsDraft,
      selectedProfileName,
      storagePathDraft,
      regionLabel,
      connectSession,
      disconnectSession,
      pulseTest,
      sendTicket,
      startBulkFlow,
      stopBulkFlow,
      saveSettingsProfile,
      activateSelectedProfile,
      applyStoragePath,
      loadSelectedProfile,
      resetSettingsDraft,
      startNewProfile,
      markSettingsDirty,
      addCustomTag,
      addSuggestedTag,
      removeAdditionalTag,
      applySelectedTemplate,
      saveCurrentTemplate,
      onRegionChange,
      onMarketChange,
      toggleMenu,
      openPage
    }
  }
}).mount('#app')

