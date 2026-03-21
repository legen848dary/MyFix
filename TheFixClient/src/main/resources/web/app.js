import { createApp, computed, onMounted, onUnmounted, reactive, ref, watch } from 'https://unpkg.com/vue@3/dist/vue.esm-browser.prod.js'

const THEME_STORAGE_KEY = 'thefixclient-theme'
const PAGE_OPTIONS = [
  { code: 'order-input', label: 'Order Input' },
  { code: 'order-blotter', label: 'Order Blotter' },
  { code: 'settings', label: 'Settings' }
]

createApp({
  template: `
    <div class="shell">
      <header class="topbar">
        <div class="brand">
          <div class="brand__mark">TF</div>
          <div>
            <p class="brand__eyebrow">Order workstation</p>
            <h1 class="brand__title">{{ overview.applicationName || 'TheFixClient' }}</h1>
            <p class="brand__subtitle">{{ overview.subtitle || 'Electronic execution workstation' }}</p>
            <p class="brand__subtitle">{{ overview.environment }}</p>
          </div>
        </div>

        <div class="topbar__actions">
          <div class="menu-wrap">
            <button class="menu-button" @click.stop="toggleMenu">{{ currentPageLabel }}</button>
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

          <div class="badge" :class="session.connected ? 'badge--live' : 'badge--warn'">
            <span class="badge__dot"></span>
            <span>{{ session.connected ? 'FIX session live' : 'Awaiting logon' }}</span>
          </div>
          <div class="badge" :class="session.autoFlowActive ? 'badge--flow' : ''">
            <span class="badge__dot"></span>
            <span>{{ session.autoFlowActive ? session.autoFlowDescriptor : 'Bulk flow idle' }}</span>
          </div>
          <div class="badge">
            <span class="badge__dot"></span>
            <span>API {{ apiStatus }}</span>
          </div>
          <label class="theme-select" aria-label="Theme selector">
            <span class="theme-select__label">Theme</span>
            <select v-model="theme" class="theme-select__control">
              <option value="system">System</option>
              <option value="dark">Dark</option>
              <option value="light">Light</option>
            </select>
          </label>
        </div>
      </header>

      <main v-if="activePage === 'order-input'" class="page-grid">
        <section class="stack">
          <article class="panel panel--focus">
            <div class="panel__header">
              <div>
                <h2 class="panel__title">Order Input</h2>
                <p class="panel__copy">Manual order entry and bulk routing in one comfortable input surface.</p>
              </div>
              <span class="chip">{{ preview.status || 'Draft ready' }}</span>
            </div>

            <div class="workflow-toolbar">
              <div class="tab-bar tab-bar--inline">
                <button class="tab" :class="{ 'tab--active': activeMode === 'single' }" @click="activeMode = 'single'">Single Order</button>
                <button class="tab" :class="{ 'tab--active': activeMode === 'bulk' }" @click="activeMode = 'bulk'">Bulk Order</button>
              </div>
              <div class="workflow-toolbar__actions">
                <button v-if="activeMode === 'single'" class="button button--primary" @click="sendTicket" :disabled="busyAction === 'send'">Send live order</button>
                <template v-else>
                  <button class="button button--primary" @click="startBulkFlow" :disabled="busyAction === 'flow-start'">Start bulk flow</button>
                  <button class="button button--ghost" @click="stopBulkFlow" :disabled="busyAction === 'flow-stop'">Stop bulk flow</button>
                </template>
              </div>
            </div>

            <div class="panel__body">
              <div class="order-grid order-grid--comfortable">
                <div class="field">
                  <span class="field__label">Region</span>
                  <select v-model="orderDraft.region" @change="onRegionChange">
                    <option v-for="region in regions" :key="region.code" :value="region.code">{{ region.label }}</option>
                  </select>
                </div>
                <div class="field">
                  <span class="field__label">Market</span>
                  <select v-model="orderDraft.market" @change="onMarketChange">
                    <option v-for="market in availableMarkets" :key="market.code" :value="market.code">{{ market.label }} · {{ market.currency }}</option>
                  </select>
                </div>
                <div class="field field--span-2">
                  <span class="field__label">Symbol</span>
                  <input v-model="orderDraft.symbol" placeholder="AAPL" />
                </div>
                <div class="field">
                  <span class="field__label">Side</span>
                  <select v-model="orderDraft.side">
                    <option v-for="side in sides" :key="side.code" :value="side.code">{{ side.label }}</option>
                  </select>
                </div>
                <div class="field">
                  <span class="field__label">Quantity</span>
                  <input v-model.number="orderDraft.quantity" type="number" min="1" step="1" />
                </div>
                <div class="field">
                  <span class="field__label">Order type</span>
                  <select v-model="orderDraft.orderType">
                    <option v-for="orderType in orderTypes" :key="orderType.code" :value="orderType.code">{{ orderType.label }}</option>
                  </select>
                </div>
                <div class="field">
                  <span class="field__label">Time in force</span>
                  <select v-model="orderDraft.timeInForce">
                    <option v-for="tif in timeInForces" :key="tif.code" :value="tif.code">{{ tif.label }}</option>
                  </select>
                </div>
                <div class="field">
                  <span class="field__label">Price type</span>
                  <select v-model="orderDraft.priceType">
                    <option v-for="priceType in priceTypes" :key="priceType.code" :value="priceType.code">{{ priceType.label }}</option>
                  </select>
                </div>
                <div class="field">
                  <span class="field__label">Currency</span>
                  <input v-model="orderDraft.currency" maxlength="3" />
                </div>
                <div class="field">
                  <span class="field__label">{{ needsLimitPrice ? 'Limit price' : 'Reference price' }}</span>
                  <input v-model.number="orderDraft.price" type="number" min="0" step="0.01" />
                </div>
                <div class="field" v-if="needsStopPrice">
                  <span class="field__label">Stop price</span>
                  <input v-model.number="orderDraft.stopPrice" type="number" min="0" step="0.01" />
                </div>
              </div>

              <div v-if="activeMode === 'bulk'" class="bulk-grid bulk-grid--comfortable" style="margin-top: 18px;">
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
                <button class="button button--primary" @click="connectSession" :disabled="busyAction === 'connect'">Prime session</button>
                <button class="button button--ghost" @click="disconnectSession" :disabled="busyAction === 'disconnect'">Standby</button>
                <button class="button button--soft" @click="pulseTest" :disabled="busyAction === 'pulse'">Pulse test</button>
              </div>
            </div>
          </article>

          <article class="panel panel--compact">
            <div class="panel__header">
              <div>
                <h2 class="panel__title">Live metrics</h2>
                <p class="panel__copy">Session readiness and order-routing KPIs.</p>
              </div>
            </div>
            <div class="panel__body">
              <div class="metrics-strip metrics-strip--stacked">
                <div class="compact-card">
                  <p class="eyebrow">Ready state</p>
                  <p class="compact-card__value">{{ kpis.readyState }}</p>
                  <p class="compact-card__copy">{{ kpis.sessionUptime }}</p>
                </div>
                <div class="compact-card">
                  <p class="eyebrow">Orders sent</p>
                  <p class="compact-card__value">{{ kpis.sentOrders }}</p>
                  <p class="compact-card__copy">Accepted NewOrderSingle submissions.</p>
                </div>
                <div class="compact-card">
                  <p class="eyebrow">Exec reports</p>
                  <p class="compact-card__value">{{ kpis.executionReports }}</p>
                  <p class="compact-card__copy">Inbound acknowledgements.</p>
                </div>
                <div class="compact-card">
                  <p class="eyebrow">Rejects / failures</p>
                  <p class="compact-card__value">{{ kpis.rejects }} / {{ kpis.sendFailures }}</p>
                  <p class="compact-card__copy">Business rejects and send failures.</p>
                </div>
              </div>
            </div>
          </article>
        </aside>
      </main>

      <main v-else-if="activePage === 'order-blotter'" class="workspace workspace--single">
        <section class="stack">
          <article class="panel">
            <div class="panel__header">
              <div>
                <h2 class="panel__title">Order Blotter</h2>
                <p class="panel__copy">Manual and bulk-routed orders with status, execution state, and operator notes.</p>
              </div>
              <span class="chip">{{ recentOrders.length }} tracked</span>
            </div>
            <div class="panel__body">
              <div v-if="!recentOrders.length" class="empty-state">No routed orders yet. Send a ticket or start bulk flow from Order Input.</div>
              <div v-else class="orders">
                <div v-for="order in recentOrders" :key="order.clOrdId" class="order-row">
                  <p class="order-row__title">{{ order.side }} {{ order.quantity }} {{ order.symbol }}</p>
                  <p class="order-row__copy">{{ order.note }}</p>
                  <div class="order-meta">
                    <span class="chip">{{ order.source }}</span>
                    <span class="chip">Status {{ order.status }}</span>
                    <span class="chip">Exec {{ order.execType }}</span>
                    <span class="chip mono">Px {{ order.limitPrice }}</span>
                    <span class="chip mono">AvgPx {{ order.avgPx }}</span>
                    <span class="chip mono">{{ order.clOrdId }}</span>
                    <span class="chip mono">{{ order.time }}</span>
                  </div>
                </div>
              </div>
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
      applicationName: 'TheFixClient',
      subtitle: 'Electronic execution workstation',
      environment: 'Loading…'
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
      fixVersions: []
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
    const apiStatus = ref('Connecting…')
    const busyAction = ref('')
    const refreshHandle = ref(null)
    const previewTimer = ref(null)
    const activePage = ref('order-input')
    const activeMode = ref('single')
    const menuOpen = ref(false)
    const theme = ref(window.localStorage.getItem(THEME_STORAGE_KEY) || 'system')
    const draftInitialized = ref(false)
    const settingsInitialized = ref(false)
    const settingsDirty = ref(false)
    const suggestedSymbol = ref('AAPL')
    const selectedProfileName = ref(TheFixSessionProfileNameFallback())
    const storagePathDraft = ref('')

    const orderDraft = reactive({
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
      stopPrice: 0
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
    const fixVersionOptions = computed(() => settingsState.fixVersionOptions || referenceData.fixVersions || [])
    const availableMarkets = computed(() => (referenceData.markets || []).filter(market => market.region === orderDraft.region))
    const selectedOrderType = computed(() => orderTypes.value.find(item => item.code === orderDraft.orderType) || null)
    const activeBulkMode = computed(() => bulkModes.value.find(item => item.code === bulkDraft.bulkMode) || null)
    const needsLimitPrice = computed(() => Boolean(selectedOrderType.value?.requiresLimitPrice))
    const needsStopPrice = computed(() => Boolean(selectedOrderType.value?.requiresStopPrice))
    const previewWarnings = computed(() => preview.warnings || [])
    const pageOptions = computed(() => PAGE_OPTIONS)
    const currentPageLabel = computed(() => pageOptions.value.find(page => page.code === activePage.value)?.label || 'Menu')

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
      document.body.setAttribute('data-theme', effectiveTheme === 'light' ? 'light' : 'dark')
      window.localStorage.setItem(THEME_STORAGE_KEY, nextTheme)
    }

    const handleSystemThemeChange = () => {
      if (theme.value === 'system') {
        applyTheme(theme.value)
      }
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

    const applyOverview = (payload, preserveSettingsDraft = true) => {
      Object.assign(overview, {
        applicationName: payload.applicationName,
        subtitle: payload.subtitle,
        environment: payload.environment
      })
      Object.assign(session, payload.session || {})
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
      try {
        applyOverview(await apiCall('/api/overview'), true)
      } catch (error) {
        apiStatus.value = 'Unavailable'
        recentEvents.value = [{
          time: 'now',
          level: 'WARN',
          title: 'Overview unavailable',
          detail: 'The client API could not be reached. Confirm the backend is running.'
        }]
      }
    }

    const buildOrderPayload = () => ({
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
      stopPrice: Number(orderDraft.stopPrice || 0)
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

    const connectSession = async () => runAction('connect', async () => applyOverview(await apiCall('/api/session/connect', { method: 'POST' }), true))
    const disconnectSession = async () => runAction('disconnect', async () => applyOverview(await apiCall('/api/session/disconnect', { method: 'POST' }), true))
    const pulseTest = async () => runAction('pulse', async () => applyOverview(await apiCall('/api/session/pulse-test', { method: 'POST' }), true))

    const sendTicket = async () => runAction('send', async () => {
      await previewTicket()
      applyOverview(await apiCall('/api/order-ticket/send', {
        method: 'POST',
        body: JSON.stringify(buildOrderPayload())
      }), true)
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

    const toggleMenu = () => {
      menuOpen.value = !menuOpen.value
    }

    const openPage = (pageCode) => {
      activePage.value = pageCode
      menuOpen.value = false
    }

    const closeMenu = () => {
      menuOpen.value = false
    }

    watch(theme, applyTheme, { immediate: true })

    watch(() => orderDraft.orderType, () => {
      if (!needsStopPrice.value) {
        orderDraft.stopPrice = 0
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
      window.addEventListener('click', closeMenu)
      await loadOverview()
      await previewTicket()
      refreshHandle.value = window.setInterval(loadOverview, 2000)
    })

    onUnmounted(() => {
      if (previewTimer.value) {
        window.clearTimeout(previewTimer.value)
      }
      if (refreshHandle.value) {
        window.clearInterval(refreshHandle.value)
      }
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
      apiStatus,
      activePage,
      activeMode,
      menuOpen,
      pageOptions,
      currentPageLabel,
      busyAction,
      theme,
      orderDraft,
      bulkDraft,
      preview,
      previewWarnings,
      regions,
      sides,
      orderTypes,
      priceTypes,
      timeInForces,
      bulkModes,
      fixVersionOptions,
      availableMarkets,
      activeBulkMode,
      needsLimitPrice,
      needsStopPrice,
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
      onRegionChange,
      onMarketChange,
      toggleMenu,
      openPage
    }
  }
}).mount('#app')

