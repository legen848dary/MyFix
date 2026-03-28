import { createApp, computed, onMounted, onUnmounted, reactive, ref, watch } from 'https://unpkg.com/vue@3/dist/vue.esm-browser.prod.js'

const THEME_STORAGE_KEY = 'thefixclient-theme'
const THEME_OPTIONS = Object.freeze(['system', 'dark', 'light', 'light2', 'hsbc-dark'])
const THEME_CHOICES = Object.freeze([
  { value: 'system', label: 'System' },
  { value: 'dark', label: 'Dark - Green' },
  { value: 'light', label: 'Light' },
  { value: 'light2', label: 'Light2' },
  { value: 'hsbc-dark', label: 'Dark - RED' }
])
const REFRESH_FREQUENCY_STORAGE_KEY = 'thefixclient-refresh-frequency'
const DEFAULT_REFRESH_SECONDS = 3
const REFRESH_FREQUENCY_OPTIONS = Object.freeze([1, 3, 5])
const PAGE_OPTIONS = [
  { code: 'order-input', label: 'Create Order' },
  { code: 'order-blotter', label: 'My Orders' },
  { code: 'fix-messages', label: 'Fix In/Out' }
]

const PAGE_PATHS = Object.freeze({
  'order-input': '/home',
  'order-blotter': '/orders',
  'fix-messages': '/recentfixmsgs',
  'session-profiles': '/session-profiles',
  about: '/about',
  settings: '/settings'
})

const ABOUT_COMPONENTS = Object.freeze([
  {
    title: 'Web workstation shell',
    summary: 'Vue 3 single-page operator UI served from TheFixClient static resources.',
    bullets: ['Desktop-style top menu', 'Session-aware order entry, blotter, and FIX tape', 'Right-side session control rail with per-profile actions']
  },
  {
    title: 'TheFixClient server',
    summary: 'Vert.x HTTP server that serves the SPA and exposes workstation APIs.',
    bullets: ['SPA route handling for clean URLs', 'JSON endpoints for overview, profiles, templates, and orders', 'Bridges browser actions to workbench state']
  },
  {
    title: 'Workbench state',
    summary: 'Central orchestration layer for session profiles, runtime selection, and workstation snapshots.',
    bullets: ['Builds the overview payload', 'Coordinates profile storage and template storage', 'Targets one runtime per selected FIX profile']
  },
  {
    title: 'FIX runtime services',
    summary: 'QuickFIX/J initiator services, one runtime instance per active session profile.',
    bullets: ['Connect/disconnect/reconnect lifecycle', 'Order routing and bulk flow control', 'Recent events, blotter state, and raw FIX message capture']
  },
  {
    title: 'Session profile store',
    summary: 'Persistent store for named FIX connectivity profiles.',
    bullets: ['Default profile seeding', 'Rename, activate, and delete support', 'Workspace storage-path awareness']
  },
  {
    title: 'Simulator integration',
    summary: 'TheFixClient connects to TheFixSimulator for end-to-end FIX workflow testing.',
    bullets: ['Simulator web UI on port 8080', 'Client web UI on port 8081', 'FIX acceptor on localhost:9880 by default']
  }
])

const ABOUT_CAPABILITIES = Object.freeze([
  'Create and preview FIX messages before routing.',
  'Run profile-targeted multi-session workflows from one workstation.',
  'Inspect recent orders and recent sent/received FIX traffic.',
  'Manage saved session profiles, templates, and workspace preferences.',
  'Drive TheFixSimulator from a browser-first FIX operator surface.'
])

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
    case '/neworder':
    case '/order':
      return 'order-input'
    case '/orders':
    case '/blotter':
      return 'order-blotter'
    case '/recentfixmsgs':
      return 'fix-messages'
    case '/session-profiles':
    case '/sessionprofiles':
      return 'session-profiles'
    case '/about':
      return 'about'
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

const FIX_TAG_LABELS = Object.freeze({
  '8': 'BeginString',
  '11': 'ClOrdID',
  '15': 'Currency',
  '21': 'HandlInst',
  '35': 'MsgType',
  '38': 'OrderQty',
  '40': 'OrdType',
  '41': 'OrigClOrdID',
  '44': 'Price',
  '49': 'SenderCompID',
  '54': 'Side',
  '55': 'Symbol',
  '56': 'TargetCompID',
  '58': 'Text',
  '59': 'TimeInForce',
  '60': 'TransactTime',
  '98': 'EncryptMethod',
  '99': 'StopPx',
  '108': 'HeartBtInt',
  '141': 'ResetSeqNumFlag',
  '207': 'SecurityExchange',
  '423': 'PriceType'
})

const FIX_FORM_FIELD_TAGS = Object.freeze({
  messageType: '35',
  clOrdId: '11',
  origClOrdId: '41',
  symbol: '55',
  side: '54',
  quantity: '38',
  orderType: '40',
  timeInForce: '59',
  priceType: '423',
  currency: '15',
  price: '44',
  stopPrice: '99'
})

const SIDE_TO_FIX = Object.freeze({ BUY: '1', SELL: '2', SELL_SHORT: '5' })
const FIX_TO_SIDE = Object.freeze({ '1': 'BUY', '2': 'SELL', '5': 'SELL_SHORT' })
const ORDER_TYPE_TO_FIX = Object.freeze({
  MARKET: '1',
  LIMIT: '2',
  STOP: '3',
  STOP_LIMIT: '4',
  MARKET_ON_CLOSE: '5',
  LIMIT_ON_CLOSE: 'B',
  PEGGED: 'P'
})
const FIX_TO_ORDER_TYPE = Object.freeze(Object.fromEntries(Object.entries(ORDER_TYPE_TO_FIX).map(([key, value]) => [value, key])))
const TIF_TO_FIX = Object.freeze({ DAY: '0', GTC: '1', OPG: '2', IOC: '3', FOK: '4', GTD: '6' })
const FIX_TO_TIF = Object.freeze(Object.fromEntries(Object.entries(TIF_TO_FIX).map(([key, value]) => [value, key])))
const PRICE_TYPE_TO_FIX = Object.freeze({ PER_UNIT: '1', PERCENTAGE: '2', FIXED_AMOUNT: '3', YIELD: '9', SPREAD: '6' })
const FIX_TO_PRICE_TYPE = Object.freeze(Object.fromEntries(Object.entries(PRICE_TYPE_TO_FIX).map(([key, value]) => [value, key])))

createApp({
  template: `
    <div class="shell">
      <nav class="desktop-menubar" aria-label="Desktop workstation menu">
        <div class="desktop-menubar__group">
          <div class="desktop-menu">
            <button class="desktop-menu__button" :class="{ 'desktop-menu__button--open': desktopMenuOpen === 'views' }" @click.stop="toggleDesktopMenu('views')">
              <span class="desktop-menu__button-icon" aria-hidden="true">▣</span>
              <span class="desktop-menu__button-label">Views</span>
            </button>
            <div v-if="desktopMenuOpen === 'views'" class="desktop-menu__panel">
              <div class="desktop-menu__section">
                <p class="desktop-menu__section-title">Workspace</p>
                <button v-for="page in pageOptions" :key="'views-' + page.code" class="desktop-menu__item" @click="openPage(page.code)">
                  <span class="desktop-menu__item-label">{{ page.label }}</span>
                  <span class="desktop-menu__item-meta" v-if="activePage === page.code">Open</span>
                </button>
              </div>
            </div>
          </div>

          <div class="desktop-menu">
            <button class="desktop-menu__button" :class="{ 'desktop-menu__button--open': desktopMenuOpen === 'help' }" @click.stop="toggleDesktopMenu('help')">
              <span class="desktop-menu__button-icon" aria-hidden="true">?</span>
              <span class="desktop-menu__button-label">Help</span>
            </button>
            <div v-if="desktopMenuOpen === 'help'" class="desktop-menu__panel">
              <div class="desktop-menu__section">
                <p class="desktop-menu__section-title">About</p>
                <button class="desktop-menu__item" @click="openPage('about')">
                  <span class="desktop-menu__item-label">About TheFixClient</span>
                  <span class="desktop-menu__item-meta">About</span>
                </button>
              </div>
              <div class="desktop-menu__section">
                <p class="desktop-menu__section-title">Workspace</p>
                <button class="desktop-menu__item" @click="openPage('session-profiles')">
                  <span class="desktop-menu__item-label">Session Profiles</span>
                  <span class="desktop-menu__item-meta">Profiles</span>
                </button>
                <button class="desktop-menu__item" @click="openPage('settings')">
                  <span class="desktop-menu__item-label">Settings</span>
                  <span class="desktop-menu__item-meta">Workspace</span>
                </button>
              </div>
            </div>
          </div>
        </div>

        <div class="desktop-menubar__status">
          <div class="desktop-nav" aria-label="Workspace navigation">
            <button class="desktop-nav__button" type="button" aria-label="Go back" title="Back" @click="goBack">
              <span aria-hidden="true">←</span>
              <span class="desktop-nav__label">Back</span>
            </button>
            <button class="desktop-nav__button" type="button" aria-label="Go forward" title="Forward" @click="goForward">
              <span aria-hidden="true">→</span>
              <span class="desktop-nav__label">Forward</span>
            </button>
            <button class="desktop-nav__button desktop-nav__button--home" type="button" aria-label="Go home" title="Home" @click="goHome">
              <span aria-hidden="true">⌂</span>
              <span>Home</span>
            </button>
          </div>
          <div class="desktop-menu">
            <button class="desktop-menu__button" :class="{ 'desktop-menu__button--open': desktopMenuOpen === 'display' }" @click.stop="toggleDesktopMenu('display')">
              <span class="desktop-menu__button-icon" aria-hidden="true">◐</span>
              <span class="desktop-menu__button-label">Display</span>
            </button>
            <div v-if="desktopMenuOpen === 'display'" class="desktop-menu__panel desktop-menu__panel--wide">
              <div class="desktop-menu__section">
                <p class="desktop-menu__section-title">Theme</p>
                <button v-for="option in themeChoices" :key="'display-theme-' + option.value" class="desktop-menu__item" @click="selectTheme(option.value)">
                  <span class="desktop-menu__item-label">{{ option.label }}</span>
                  <span class="desktop-menu__item-meta" v-if="theme === option.value">Active</span>
                </button>
              </div>
              <div class="desktop-menu__section">
                <p class="desktop-menu__section-title">Refresh</p>
                <button v-for="seconds in refreshFrequencyOptions" :key="'display-refresh-' + seconds" class="desktop-menu__item" @click="selectRefreshFrequency(seconds)">
                  <span class="desktop-menu__item-label">{{ refreshFrequencyLabel(seconds) }}</span>
                  <span class="desktop-menu__item-meta" v-if="activeRefreshSeconds === seconds">Active</span>
                </button>
              </div>
            </div>
          </div>
        </div>
      </nav>

      <header class="topbar">
        <div class="brand">
          <div class="brand__mark">TF</div>
          <div>
            <p class="brand__eyebrow">Order workstation · <span class="brand__profile-name">{{ activeSessionProfileLabel }}</span></p>
            <h1 class="brand__title">{{ overview.applicationName || 'TheFixClient' }}</h1>
            <p class="brand__subtitle">Command the full FIX journey from one deck — compose, launch, and track every order in motion.</p>
          </div>
        </div>

        <div class="topbar__actions">
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
                <div class="compact-card compact-card--metric-tile compact-card--metric-inline">
                  <span class="compact-card__metric-label">Ready</span>
                  <span class="compact-card__metric-value">{{ kpis.readyState }}</span>
                </div>
                <div class="compact-card compact-card--metric-tile compact-card--metric-inline">
                  <span class="compact-card__metric-label">Sent</span>
                  <span class="compact-card__metric-value">{{ kpis.sentOrders }}</span>
                </div>
                <div class="compact-card compact-card--metric-tile compact-card--metric-inline">
                  <span class="compact-card__metric-label">Exec</span>
                  <span class="compact-card__metric-value">{{ kpis.executionReports }}</span>
                </div>
                <div class="compact-card compact-card--metric-tile compact-card--metric-inline">
                  <span class="compact-card__metric-label">Cancels</span>
                  <span class="compact-card__metric-value">{{ kpis.cancels }}</span>
                </div>
                <div class="compact-card compact-card--metric-tile compact-card--metric-inline">
                  <span class="compact-card__metric-label">Reject/Fail</span>
                  <span class="compact-card__metric-value">{{ kpis.rejects }} / {{ kpis.sendFailures }}</span>
                </div>
              </div>
            </div>
          </article>

          <article class="panel panel--focus">
            <div class="composer-strip">
              <div class="composer-strip__info">
                <span class="composer-strip__title">Create Order</span>
                <span class="chip">{{ activeMode === 'single' ? 'Single message' : 'Bulk NOS' }}</span>
                <span class="chip">{{ activeMode === 'single' ? (singleMessageViewMode === 'fields' ? 'Field view' : 'Raw FIX view') : 'Bulk flow view' }}</span>
                <span class="chip">{{ activeFixVersionLabel }}</span>
                <span class="chip">{{ previewStatusLabel }}</span>
              </div>
            </div>

            <div class="workflow-toolbar">
              <div class="workflow-toolbar__row workflow-toolbar__row--actions">
                <div class="tab-bar tab-bar--inline">
                  <button class="tab" :class="{ 'tab--active': activeMode === 'single' }" @click="activeMode = 'single'">Single Message</button>
                  <button class="tab" :class="{ 'tab--active': activeMode === 'bulk' }" @click="activeMode = 'bulk'">Bulk NOS</button>
                </div>
                <div class="workflow-toolbar__actions">
                  <template v-if="activeMode === 'single'">
                    <button class="button button--soft" @click="saveCurrentTemplate" :disabled="busyAction === 'save-template'">
                      {{ busyAction === 'save-template' ? 'Saving…' : 'Save template' }}
                    </button>
                    <button class="button button--soft composer-strip__toggle" @click="toggleSingleMessageViewMode">
                      {{ singleMessageViewMode === 'fields' ? 'Raw FIX view' : 'Field view' }}
                    </button>
                    <button class="button button--primary" @click="sendTicket" :disabled="busyAction === 'send'">Send message</button>
                  </template>
                  <template v-else-if="selectedMessageType?.supportsBulk">
                    <button class="button" :class="bulkFlowStartButtonClass" @click="startBulkFlow" :disabled="!canStartBulkFlow || busyAction === 'flow-start'">
                      {{ busyAction === 'flow-start' ? 'Starting…' : (session.autoFlowActive ? 'Bulk flow running' : 'Start bulk flow') }}
                    </button>
                    <button class="button" :class="bulkFlowStopButtonClass" @click="stopBulkFlow" :disabled="!canStopBulkFlow || busyAction === 'flow-stop'">
                      {{ busyAction === 'flow-stop' ? 'Stopping…' : 'Stop bulk flow' }}
                    </button>
                  </template>
                  <span v-else class="chip">Bulk flow supports NOS only</span>
                </div>
              </div>
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
                </div>
              </div>
            </div>

            <div class="panel__body panel__body--composer">
              <div v-if="activeMode === 'single' && singleMessageViewMode === 'raw'" class="single-message-workspace">
                <div class="single-message-workspace__topbar">
                  <div>
                    <p class="eyebrow">Single Message</p>
                    <p class="single-message-workspace__copy">Raw FIX message view with custom delimiter parsing.</p>
                  </div>
                  <div class="single-message-workspace__actions">
                    <label class="field field--inline field--delimiter-input">
                      <span class="field__label">Delimiter</span>
                      <input v-model="rawFixDelimiter" placeholder="| or SOH" />
                    </label>
                    <button class="button button--soft" @click="parseRawFixInputByDelimiter">Parse Raw fix by deliminator</button>
                  </div>
                </div>

                <div class="field">
                  <span class="field__label">Raw FIX input</span>
                  <textarea
                    v-model="rawFixInputDraft"
                    class="raw-fix-input"
                    placeholder="Paste a FIX message using the delimiter above, then parse it into the raw message view or switch to the field view."></textarea>
                  <span class="field__hint">Supports literal delimiters plus aliases such as <span class="mono">SOH</span>, <span class="mono">^A</span>, <span class="mono">\u0001</span>, or any custom separator.</span>
                </div>

                <div v-if="rawFixParseNotice" class="chip-row">
                  <span class="chip">{{ rawFixParseNotice }}</span>
                </div>

                <ul v-if="rawFixParseWarnings.length" class="warning-list">
                  <li v-for="warning in rawFixParseWarnings" :key="warning">{{ warning }}</li>
                </ul>
              </div>

              <div v-if="activeMode === 'single' && singleMessageViewMode === 'fields'" class="order-grid order-grid--comfortable order-grid--trade-ticket">
                <div class="field field--trade-ticket">
                  <span class="field__label">{{ fixFieldLabel('messageType', 'Message type') }}</span>
                  <select v-model="orderDraft.messageType">
                    <option v-for="messageType in messageTypes" :key="messageType.code" :value="messageType.code">{{ messageType.label }}</option>
                  </select>
                </div>
                <div class="field field--trade-ticket">
                  <span class="field__label">{{ fixFieldLabel('clOrdId', 'ClOrdID') }}</span>
                  <input v-model="orderDraft.clOrdId" placeholder="Auto-generated if blank" />
                </div>
                <div v-if="selectedMessageType?.requiresOrigClOrdId" class="field field--trade-ticket">
                  <span class="field__label">{{ fixFieldLabel('origClOrdId', 'OrigClOrdID') }}</span>
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
                  <span class="field__label">{{ fixFieldLabel('symbol', 'Symbol') }}</span>
                  <input v-model="orderDraft.symbol" placeholder="AAPL" />
                </div>
                <div class="field field--trade-ticket">
                  <span class="field__label">{{ fixFieldLabel('side', 'Side') }}</span>
                  <select v-model="orderDraft.side">
                    <option v-for="side in sides" :key="side.code" :value="side.code">{{ side.label }}</option>
                  </select>
                </div>
                <div class="field field--trade-ticket">
                  <span class="field__label">{{ fixFieldLabel('quantity', 'Quantity') }}</span>
                  <input v-model.number="orderDraft.quantity" type="number" min="0" step="1" />
                </div>
                <div v-if="orderDraft.messageType !== 'ORDER_CANCEL_REQUEST'" class="field field--trade-ticket">
                  <span class="field__label">{{ fixFieldLabel('orderType', 'Order type') }}</span>
                  <select v-model="orderDraft.orderType">
                    <option v-for="orderType in orderTypes" :key="orderType.code" :value="orderType.code">{{ orderType.label }}</option>
                  </select>
                </div>
                <div v-if="orderDraft.messageType !== 'ORDER_CANCEL_REQUEST'" class="field field--trade-ticket">
                  <span class="field__label">{{ fixFieldLabel('timeInForce', 'Time in force') }}</span>
                  <select v-model="orderDraft.timeInForce">
                    <option v-for="tif in timeInForces" :key="tif.code" :value="tif.code">{{ tif.label }}</option>
                  </select>
                </div>
                <div v-if="orderDraft.messageType !== 'ORDER_CANCEL_REQUEST'" class="field field--trade-ticket">
                  <span class="field__label">{{ fixFieldLabel('priceType', 'Price type') }}</span>
                  <select v-model="orderDraft.priceType">
                    <option v-for="priceType in priceTypes" :key="priceType.code" :value="priceType.code">{{ priceType.label }}</option>
                  </select>
                </div>
                <div class="field field--trade-ticket">
                  <span class="field__label">{{ fixFieldLabel('currency', 'Currency') }}</span>
                  <input v-model="orderDraft.currency" maxlength="3" />
                </div>
                <div v-if="orderDraft.messageType !== 'ORDER_CANCEL_REQUEST'" class="field field--trade-ticket">
                  <span class="field__label">{{ fixFieldLabel('price', needsLimitPrice ? 'Limit price' : 'Reference price') }}</span>
                  <input v-model.number="orderDraft.price" type="number" min="0" step="0.01" />
                </div>
                <div class="field field--trade-ticket" v-if="orderDraft.messageType !== 'ORDER_CANCEL_REQUEST' && needsStopPrice">
                  <span class="field__label">{{ fixFieldLabel('stopPrice', 'Stop price') }}</span>
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

              <div v-else-if="activeMode === 'single' && singleMessageViewMode === 'raw'" class="raw-message-view">
                <div class="raw-message-view__panel">
                  <div class="raw-message-view__header">
                    <p class="eyebrow">Raw FIX message</p>
                    <span class="chip mono">{{ normalizedRawFixDelimiterLabel }}</span>
                  </div>
                  <pre class="raw-message-view__content">{{ formattedRawFixMessage }}</pre>
                </div>
              </div>

              <div v-if="activeMode === 'single' && singleMessageViewMode === 'fields' && orderDraft.additionalTags.length" class="stack" style="margin-top: 18px; gap: 12px;">
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

              <div v-if="activeMode === 'bulk' && selectedMessageType?.supportsBulk" class="stack" style="margin-top: 18px; gap: 18px;">
                <div class="bulk-grid bulk-grid--comfortable">
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

                <div class="compact-card bulk-fields-card">
                  <div class="bulk-fields-card__header">
                    <div>
                      <p class="eyebrow">Bulk FIX fields</p>
                      <p class="compact-card__copy">Keep region and market fixed for the run. Symbol, side, quantity, and pricing are randomized per outbound NOS.</p>
                    </div>
                    <span class="chip" :class="session.autoFlowActive ? 'chip--success' : 'chip--neutral'">{{ session.autoFlowActive ? 'Bulk flow live' : 'Bulk flow ready' }}</span>
                  </div>
                  <div class="order-grid order-grid--comfortable" style="margin-top: 14px;">
                    <div class="field">
                      <span class="field__label">Region</span>
                      <select v-model="orderDraft.region" @change="onRegionChange">
                        <option v-for="region in regions" :key="'bulk-region-' + region.code" :value="region.code">{{ region.label }}</option>
                      </select>
                    </div>
                    <div class="field">
                      <span class="field__label">Market</span>
                      <select v-model="orderDraft.market" @change="onMarketChange">
                        <option v-for="market in availableMarkets" :key="'bulk-market-' + market.code" :value="market.code">{{ market.label }} · {{ market.currency }}</option>
                      </select>
                    </div>
                  </div>
                </div>
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

        <aside class="stack page-grid__aside">
          <article class="panel panel--compact">
            <div class="panel__header">
              <h2 class="panel__title">Session Control</h2>
              <button class="button button--ghost session-control-panel__manage" @click="openPage('session-profiles')">Edit Profiles</button>
            </div>
            <div class="panel__body compact-stack session-control-rail">
              <div v-if="sessionControlCards.length" class="session-control-rail__cards">
                <article
                  v-for="card in sessionControlCards"
                  :key="card.profileName"
                  class="runtime-session-card"
                  :class="runtimeSessionCardClass(card)"
                  role="button"
                  tabindex="0"
                  @click="loadSessionCard(card.profileName)"
                  @keydown.enter.prevent="loadSessionCard(card.profileName)"
                  @keydown.space.prevent="loadSessionCard(card.profileName)">
                  <div class="runtime-session-card__header">
                    <div>
                      <p class="runtime-session-card__title">{{ card.profileName }}</p>
                      <p class="runtime-session-card__meta">{{ card.host }}:{{ card.port }} · {{ card.fixVersionLabel }}</p>
                    </div>
                    <span class="chip" :class="runtimeSessionToneClass(card)">{{ card.status }}</span>
                  </div>
                  <div class="chip-row runtime-session-card__state">
                    <span v-if="card.loaded" class="chip chip--success">Selected</span>
                  </div>
                  <div class="runtime-session-card__details mono">
                    <span>{{ card.senderCompId }}</span>
                    <span>→</span>
                    <span>{{ card.targetCompId }}</span>
                  </div>
                  <p v-if="sessionControlCardCopy(card)" class="runtime-session-card__meta">{{ sessionControlCardCopy(card) }}</p>
                  <div class="runtime-session-card__actions">
                    <button class="button button--ghost" @click.stop="editSessionCard(card.profileName)">Edit</button>
                    <button class="button button--ghost button--danger-outline" @click.stop="deleteSessionCard(card)" :disabled="!canDeleteSessionCard(card) || busyAction === 'delete-profile'">Delete</button>
                    <button class="button" :class="runtimeSessionButtonClass(card)" @click.stop="toggleRuntimeSession(card)" :disabled="runtimeSessionButtonDisabled(card)">{{ runtimeSessionButtonLabel(card) }}</button>
                  </div>
                </article>
              </div>

              <div v-else class="runtime-roster__empty">
                No session profiles are available yet. Create one from Session Profiles to populate the card rail.
              </div>
            </div>
          </article>
        </aside>
      </main>

      <main v-else-if="activePage === 'order-blotter'" class="page-grid">
        <section class="stack">
          <article class="panel orders-panel">
            <div class="panel__header orders-panel__header">
              <div>
                <h2 class="panel__title">My Orders</h2>
              </div>
              <span class="orders-panel__count mono">{{ ordersCountLabel }}</span>
            </div>
            <div class="fix-messages-toolbar orders-toolbar">
              <div class="fix-messages-toolbar__controls">
                <label class="fix-messages-toolbar__label">
                  <span>Page size</span>
                  <select v-model.number="ordersLimit" class="fix-messages-toolbar__select">
                    <option :value="10">10</option>
                    <option :value="20">20</option>
                    <option :value="40">40</option>
                  </select>
                </label>
                <label class="fix-messages-toolbar__label fix-messages-toolbar__label--search">
                  <span>Filter</span>
                  <input v-model="ordersSearch" placeholder="Search orders…" class="fix-messages-toolbar__input" />
                </label>
                <label class="fix-messages-toolbar__label">
                  <span>Status</span>
                  <select v-model="ordersStatusFilter" class="fix-messages-toolbar__select">
                    <option value="">All</option>
                    <option v-for="status in orderStatusOptions" :key="'order-status-' + status" :value="status">{{ status }}</option>
                  </select>
                </label>
                <label class="fix-messages-toolbar__label">
                  <span>Source</span>
                  <select v-model="ordersSourceFilter" class="fix-messages-toolbar__select">
                    <option value="">All</option>
                    <option v-for="source in orderSourceOptions" :key="'order-source-' + source" :value="source">{{ source }}</option>
                  </select>
                </label>
              </div>
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
                      <th class="text-right">ClOrdID</th>
                      <th class="text-right">Actions</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="order in paginatedRecentOrders"
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
                      <td class="mono text-right text-slate-300">{{ order.clOrdId }}</td>
                      <td class="orders-table__actions">
                        <button class="button button--soft button--table" @click="openAmendModal(order)" :disabled="!order.canAmend || busyAction === 'order-amend'">Amend</button>
                        <button class="button button--ghost button--table button--danger-outline" @click="openCancelDialog(order)" :disabled="!order.canCancel || busyAction === 'order-cancel'">Cancel</button>
                      </td>
                    </tr>
                    <tr v-if="!paginatedRecentOrders.length">
                      <td colspan="9" class="orders-table__empty">Waiting for orders…</td>
                    </tr>
                  </tbody>
                </table>
            </div>
            <div class="fix-messages-pagination">
              <button class="button button--soft" :disabled="ordersOffset === 0" @click="ordersPrev">← Previous</button>
              <span class="fix-messages-pagination__info mono">{{ ordersRangeStart }}–{{ ordersRangeEnd }} of {{ filteredOrdersTotal }}</span>
              <button class="button button--soft" :disabled="ordersOffset + ordersLimit >= filteredOrdersTotal" @click="ordersNext">Next →</button>
            </div>
          </article>
        </section>

        <aside class="stack page-grid__aside">
          <article class="panel panel--compact">
            <div class="panel__header">
              <h2 class="panel__title">Session Control</h2>
              <button class="button button--ghost session-control-panel__manage" @click="openPage('session-profiles')">Edit Profiles</button>
            </div>
            <div class="panel__body compact-stack session-control-rail">
              <div v-if="sessionControlCards.length" class="session-control-rail__cards">
                <article
                  v-for="card in sessionControlCards"
                  :key="card.profileName"
                  class="runtime-session-card"
                  :class="runtimeSessionCardClass(card)"
                  role="button"
                  tabindex="0"
                  @click="loadSessionCard(card.profileName)"
                  @keydown.enter.prevent="loadSessionCard(card.profileName)"
                  @keydown.space.prevent="loadSessionCard(card.profileName)">
                  <div class="runtime-session-card__header">
                    <div>
                      <p class="runtime-session-card__title">{{ card.profileName }}</p>
                      <p class="runtime-session-card__meta">{{ card.host }}:{{ card.port }} · {{ card.fixVersionLabel }}</p>
                    </div>
                    <span class="chip" :class="runtimeSessionToneClass(card)">{{ card.status }}</span>
                  </div>
                  <div class="chip-row runtime-session-card__state">
                    <span v-if="card.loaded" class="chip chip--success">Selected</span>
                  </div>
                  <div class="runtime-session-card__details mono">
                    <span>{{ card.senderCompId }}</span>
                    <span>→</span>
                    <span>{{ card.targetCompId }}</span>
                  </div>
                  <p v-if="sessionControlCardCopy(card)" class="runtime-session-card__meta">{{ sessionControlCardCopy(card) }}</p>
                  <div class="runtime-session-card__actions">
                    <button class="button button--ghost" @click.stop="editSessionCard(card.profileName)">Edit</button>
                    <button class="button button--ghost button--danger-outline" @click.stop="deleteSessionCard(card)" :disabled="!canDeleteSessionCard(card) || busyAction === 'delete-profile'">Delete</button>
                    <button class="button" :class="runtimeSessionButtonClass(card)" @click.stop="toggleRuntimeSession(card)" :disabled="runtimeSessionButtonDisabled(card)">{{ runtimeSessionButtonLabel(card) }}</button>
                  </div>
                </article>
              </div>

              <div v-else class="runtime-roster__empty">
                No session profiles are available yet. Create one from Session Profiles to populate the card rail.
              </div>
            </div>
          </article>
        </aside>
      </main>

      <main v-else-if="activePage === 'fix-messages'" class="page-grid">
        <section class="stack">
          <article class="panel orders-panel">
            <div class="panel__header orders-panel__header">
              <div class="fix-messages-header__summary">
                <h2 class="panel__title">Fix In/Out</h2>
                <p class="panel__copy">Recent FIX messages captured in real time — both inbound and outbound.</p>
              </div>
              <span class="orders-panel__count mono">{{ fixMsgTotal }} total</span>
            </div>
            <div class="fix-messages-toolbar">
              <div class="fix-messages-toolbar__controls">
                <label class="fix-messages-toolbar__label">
                  <span>Page size</span>
                  <select v-model.number="fixMsgLimit" @change="loadFixMessages" class="fix-messages-toolbar__select">
                    <option :value="20">20</option>
                    <option :value="50">50</option>
                    <option :value="100">100</option>
                  </select>
                </label>
                <label class="fix-messages-toolbar__label fix-messages-toolbar__label--search">
                  <span>Filter</span>
                  <input v-model="fixMsgSearch" placeholder="Search messages…" class="fix-messages-toolbar__input" />
                </label>
                <label class="fix-messages-toolbar__label">
                  <span>Direction</span>
                  <select v-model="fixMsgDirectionFilter" class="fix-messages-toolbar__select">
                    <option value="">All</option>
                    <option value="SENT">Sent</option>
                    <option value="RECEIVED">Received</option>
                  </select>
                </label>
                <label class="fix-messages-toolbar__label">
                  <span>Flow</span>
                  <select v-model="fixMsgFlowFilter" class="fix-messages-toolbar__select">
                    <option value="">All</option>
                    <option value="APPLICATION">Application</option>
                    <option value="ADMIN">Admin</option>
                  </select>
                </label>
                <label class="fix-messages-toolbar__label">
                  <input type="checkbox" v-model="fixMsgHideHeartbeats" style="margin-right:4px" />
                  <span>Hide heartbeats</span>
                </label>
              </div>
            </div>
            <div class="orders-table__scroll">
              <table class="orders-table fix-messages-table">
                <thead>
                  <tr>
                    <th class="text-left">Timestamp</th>
                    <th class="text-left">Direction</th>
                    <th class="text-left">Flow</th>
                    <th class="text-left">Message Type</th>
                    <th class="text-left">Preview</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="msg in filteredFixMessages"
                      :key="msg.id"
                      class="fix-messages-row"
                      @click="openFixMsgDetail(msg)">
                    <td class="mono fix-msg-cell">{{ msg.timestamp }}</td>
                    <td class="fix-msg-cell">
                      <span class="fix-msg-direction"
                            :class="msg.direction === 'SENT' ? 'fix-msg-direction--sent' : 'fix-msg-direction--received'">
                        {{ msg.direction }}
                      </span>
                    </td>
                    <td class="fix-msg-cell">
                      <span class="fix-msg-flow"
                            :class="msg.flow === 'ADMIN' ? 'fix-msg-flow--admin' : 'fix-msg-flow--application'">
                        {{ msg.flow || 'APPLICATION' }}
                      </span>
                    </td>
                    <td class="mono fix-msg-cell">{{ msg.msgTypeLabel }} <span class="text-faint">({{ msg.msgType }})</span></td>
                    <td class="mono fix-msg-preview">{{ msg.preview }}</td>
                  </tr>
                  <tr v-if="!filteredFixMessages.length">
                    <td colspan="5" class="orders-table__empty">No FIX messages match the current filters.</td>
                  </tr>
                </tbody>
              </table>
            </div>
            <div class="fix-messages-pagination">
              <button class="button button--soft" :disabled="fixMsgOffset === 0" @click="fixMsgPrev">← Previous</button>
              <span class="fix-messages-pagination__info mono">
                {{ fixMsgOffset + 1 }}–{{ Math.min(fixMsgOffset + fixMsgLimit, fixMsgTotal) }} of {{ fixMsgTotal }}
              </span>
              <button class="button button--soft" :disabled="fixMsgOffset + fixMsgLimit >= fixMsgTotal" @click="fixMsgNext">Next →</button>
            </div>
          </article>
        </section>

        <aside class="stack page-grid__aside">
          <article class="panel panel--compact">
            <div class="panel__header">
              <h2 class="panel__title">Session Control</h2>
              <button class="button button--ghost session-control-panel__manage" @click="openPage('session-profiles')">Edit Profiles</button>
            </div>
            <div class="panel__body compact-stack session-control-rail">
              <div v-if="sessionControlCards.length" class="session-control-rail__cards">
                <article
                  v-for="card in sessionControlCards"
                  :key="card.profileName"
                  class="runtime-session-card"
                  :class="runtimeSessionCardClass(card)"
                  role="button"
                  tabindex="0"
                  @click="loadSessionCard(card.profileName)"
                  @keydown.enter.prevent="loadSessionCard(card.profileName)"
                  @keydown.space.prevent="loadSessionCard(card.profileName)">
                  <div class="runtime-session-card__header">
                    <div>
                      <p class="runtime-session-card__title">{{ card.profileName }}</p>
                      <p class="runtime-session-card__meta">{{ card.host }}:{{ card.port }} · {{ card.fixVersionLabel }}</p>
                    </div>
                    <span class="chip" :class="runtimeSessionToneClass(card)">{{ card.status }}</span>
                  </div>
                  <div class="chip-row runtime-session-card__state">
                    <span v-if="card.loaded" class="chip chip--success">Selected</span>
                  </div>
                  <div class="runtime-session-card__details mono">
                    <span>{{ card.senderCompId }}</span>
                    <span>→</span>
                    <span>{{ card.targetCompId }}</span>
                  </div>
                  <p v-if="sessionControlCardCopy(card)" class="runtime-session-card__meta">{{ sessionControlCardCopy(card) }}</p>
                  <div class="runtime-session-card__actions">
                    <button class="button button--ghost" @click.stop="editSessionCard(card.profileName)">Edit</button>
                    <button class="button button--ghost button--danger-outline" @click.stop="deleteSessionCard(card)" :disabled="!canDeleteSessionCard(card) || busyAction === 'delete-profile'">Delete</button>
                    <button class="button" :class="runtimeSessionButtonClass(card)" @click.stop="toggleRuntimeSession(card)" :disabled="runtimeSessionButtonDisabled(card)">{{ runtimeSessionButtonLabel(card) }}</button>
                  </div>
                </article>
              </div>

              <div v-else class="runtime-roster__empty">
                No session profiles are available yet. Create one from Session Profiles to populate the card rail.
              </div>
            </div>
          </article>
        </aside>
      </main>

      <main v-else-if="activePage === 'session-profiles'" class="workspace workspace--single">
        <section class="stack">
          <article class="panel">
            <div class="panel__header">
              <div>
                <h2 class="panel__title">Session Profiles</h2>
                <p class="panel__copy">Create, load, activate, and edit FIX connectivity profiles without changing global workspace settings.</p>
              </div>
              <span class="chip">Active · {{ sessionProfilesState.activeProfileName }}</span>
            </div>
            <div class="panel__body settings-grid">
              <section class="stack">
                <div class="compact-card">
                  <p class="eyebrow">Active session profile</p>
                  <p class="compact-card__value">{{ activeSessionProfileLabel }}</p>
                  <p class="compact-card__copy">{{ session.pendingProfileChange ? 'Reconnect required to apply the selected profile.' : 'Single-session runtime behavior is intentionally preserved for this milestone.' }}</p>
                </div>

                <div class="compact-card">
                  <p class="eyebrow">Profiles</p>
                  <div class="field" style="margin-top: 12px;">
                    <span class="field__label">Available profiles</span>
                    <select v-model="selectedProfileName">
                      <option v-for="profile in sessionProfilesState.profiles" :key="profile.name" :value="profile.name">{{ profile.name }}</option>
                    </select>
                  </div>
                  <div class="button-row">
                    <button class="button" @click="loadSelectedProfile">Load profile</button>
                    <button class="button button--ghost" @click="activateSelectedProfile" :disabled="busyAction === 'activate-profile'">Activate</button>
                    <button class="button button--soft" @click="startNewProfile">New profile</button>
                    <button class="button button--ghost button--danger-outline" @click="deleteSelectedProfile()" :disabled="busyAction === 'delete-profile'">Delete profile</button>
                  </div>
                  <p class="compact-card__copy" style="margin-top: 10px;">Profile storage location is managed from the Settings page.</p>
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

      <main v-else-if="activePage === 'about'" class="workspace workspace--single">
        <section class="stack">
          <article class="panel">
            <div class="panel__header">
              <div>
                <h2 class="panel__title">About TheFixClient</h2>
                <p class="panel__copy">Architecture overview and component summary for the browser workstation, server layer, FIX runtime services, and simulator integration.</p>
              </div>
              <span class="chip">Architecture</span>
            </div>
            <div class="panel__body about-layout">
              <section class="stack">
                <div class="compact-card about-hero-card">
                  <p class="eyebrow">Purpose</p>
                  <p class="compact-card__value">Multi-session FIX workstation</p>
                  <p class="compact-card__copy">TheFixClient provides a browser-first operator surface for routing FIX traffic into TheFixSimulator, monitoring recent orders, and managing named FIX session profiles.</p>
                </div>

                <div class="compact-card">
                  <p class="eyebrow">Architecture diagram</p>
                  <svg class="about-diagram" viewBox="0 0 920 420" role="img" aria-label="TheFixClient architecture diagram">
                    <defs>
                      <linearGradient id="about-diagram-gradient" x1="0%" y1="0%" x2="100%" y2="100%">
                        <stop offset="0%" stop-color="var(--brand)" stop-opacity="0.95" />
                        <stop offset="100%" stop-color="var(--brand-strong)" stop-opacity="0.95" />
                      </linearGradient>
                    </defs>
                    <rect x="30" y="40" width="240" height="124" rx="18" class="about-diagram__node about-diagram__node--accent" />
                    <text x="52" y="78" class="about-diagram__title">Browser SPA</text>
                    <text x="52" y="108" class="about-diagram__text">Views, Display, Help/About</text>
                    <text x="52" y="132" class="about-diagram__text">Order entry, blotter, FIX tape</text>
                    <text x="52" y="156" class="about-diagram__text">Right-side session card rail</text>

                    <rect x="340" y="40" width="250" height="124" rx="18" class="about-diagram__node" />
                    <text x="362" y="78" class="about-diagram__title">Vert.x HTTP Server</text>
                    <text x="362" y="108" class="about-diagram__text">Static assets + clean routes</text>
                    <text x="362" y="132" class="about-diagram__text">Overview / profile / template APIs</text>
                    <text x="362" y="156" class="about-diagram__text">/home, /orders, /recentfixmsgs, /about</text>

                    <rect x="660" y="40" width="230" height="124" rx="18" class="about-diagram__node" />
                    <text x="682" y="78" class="about-diagram__title">Workbench State</text>
                    <text x="682" y="108" class="about-diagram__text">Snapshot orchestration</text>
                    <text x="682" y="132" class="about-diagram__text">Profile + template coordination</text>
                    <text x="682" y="156" class="about-diagram__text">Per-profile runtime targeting</text>

                    <rect x="170" y="242" width="260" height="124" rx="18" class="about-diagram__node" />
                    <text x="192" y="280" class="about-diagram__title">Session Profile Store</text>
                    <text x="192" y="310" class="about-diagram__text">Saved FIX connectivity profiles</text>
                    <text x="192" y="334" class="about-diagram__text">Activate / edit / rename / delete</text>
                    <text x="192" y="358" class="about-diagram__text">Workspace-aware persistence</text>

                    <rect x="490" y="242" width="260" height="124" rx="18" class="about-diagram__node about-diagram__node--success" />
                    <text x="512" y="280" class="about-diagram__title">QuickFIX/J Runtime Services</text>
                    <text x="512" y="310" class="about-diagram__text">Connect / disconnect / reconnect</text>
                    <text x="512" y="334" class="about-diagram__text">Manual orders + bulk flows</text>
                    <text x="512" y="358" class="about-diagram__text">Recent orders, events, raw FIX capture</text>

                    <rect x="760" y="242" width="130" height="124" rx="18" class="about-diagram__node" />
                    <text x="782" y="280" class="about-diagram__title">Simulator</text>
                    <text x="782" y="310" class="about-diagram__text">Web UI :8080</text>
                    <text x="782" y="334" class="about-diagram__text">FIX :9880</text>

                    <path d="M270 102 H340" class="about-diagram__edge" />
                    <path d="M590 102 H660" class="about-diagram__edge" />
                    <path d="M775 164 V216 H620" class="about-diagram__edge" />
                    <path d="M430 304 H490" class="about-diagram__edge" />
                    <path d="M750 304 H760" class="about-diagram__edge" />
                    <path d="M620 164 V242" class="about-diagram__edge" />
                    <path d="M430 304 H450 V164 H660" class="about-diagram__edge about-diagram__edge--muted" />
                  </svg>
                </div>
              </section>

              <section class="stack">
                <div class="compact-card">
                  <p class="eyebrow">Components summary</p>
                  <div class="about-components">
                    <article v-for="component in aboutComponents" :key="component.title" class="about-component-card">
                      <h3 class="about-component-card__title">{{ component.title }}</h3>
                      <p class="about-component-card__summary">{{ component.summary }}</p>
                      <ul class="about-component-card__list">
                        <li v-for="bullet in component.bullets" :key="bullet">{{ bullet }}</li>
                      </ul>
                    </article>
                  </div>
                </div>

                <div class="compact-card">
                  <p class="eyebrow">Current capabilities</p>
                  <ul class="about-component-card__list">
                    <li v-for="capability in aboutCapabilities" :key="capability">{{ capability }}</li>
                  </ul>
                </div>
              </section>
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
                <p class="panel__copy">Workspace-wide controls only. FIX connectivity and session definitions now live under Session Profiles.</p>
              </div>
              <span class="chip">Workspace</span>
            </div>
            <div class="panel__body settings-grid">
              <section class="stack">
                <div class="compact-card">
                  <p class="eyebrow">Profile storage</p>
                  <p class="compact-card__copy">Session profile files are loaded from and saved to this workspace path.</p>
                  <div class="field" style="margin-top: 12px;">
                    <span class="field__label">Storage path</span>
                    <input v-model="storagePathDraft" />
                  </div>
                  <p class="compact-card__copy" style="margin-top: 10px;">Default temp path: {{ workspaceSettingsState.defaultStoragePath }}</p>
                  <div class="button-row">
                    <button class="button" @click="applyStoragePath" :disabled="busyAction === 'storage-path'">Apply path</button>
                  </div>
                </div>

                <div class="compact-card">
                  <p class="eyebrow">Browser preferences</p>
                  <p class="compact-card__copy">Theme and refresh cadence are global browser preferences managed from the header for quick access.</p>
                  <div class="order-grid order-grid--comfortable" style="margin-top: 12px;">
                    <div class="field">
                      <span class="field__label">Theme</span>
                      <input :value="currentThemeLabel" readonly />
                    </div>
                    <div class="field">
                      <span class="field__label">Refresh every</span>
                      <input :value="currentRefreshFrequencyLabel" readonly />
                    </div>
                  </div>
                </div>
              </section>

              <section class="stack">
                <div class="compact-card">
                  <p class="eyebrow">Session profile summary</p>
                  <p class="compact-card__value">{{ workspaceSettingsState.profileCount || sessionProfilesState.profiles.length }} profiles</p>
                  <p class="compact-card__copy">Active profile: {{ activeSessionProfileLabel }}</p>
                  <div class="button-row" style="margin-top: 12px;">
                    <button class="button button--primary" @click="openPage('session-profiles')">Open Session Profiles</button>
                  </div>
                </div>

                <div class="compact-card">
                  <p class="eyebrow">Current runtime</p>
                  <p class="compact-card__copy">Milestone 1 keeps the current single-session runtime intact while separating global workspace settings from FIX session profile management.</p>
                  <div class="order-grid order-grid--comfortable" style="margin-top: 12px;">
                    <div class="field">
                      <span class="field__label">Session status</span>
                      <input :value="session.connected ? 'Connected' : 'Standby'" readonly />
                    </div>
                    <div class="field">
                      <span class="field__label">FIX version</span>
                      <input :value="session.fixVersionLabel || activeFixVersionLabel" readonly />
                    </div>
                  </div>
                </div>

                <div class="compact-card">
                  <p class="eyebrow">FIX sequence reset</p>
                  <p class="compact-card__copy">Reset inbound and outbound FIX sequence numbers for the currently selected live session profile.</p>
                  <div class="order-grid order-grid--comfortable" style="margin-top: 12px;">
                    <div class="field">
                      <span class="field__label">Selected profile</span>
                      <input :value="selectedProfileName" readonly />
                    </div>
                    <div class="field">
                      <span class="field__label">Session state</span>
                      <input :value="canResetSelectedSession ? 'Connected' : 'Connect the session first'" readonly />
                    </div>
                  </div>
                  <div class="button-row" style="margin-top: 12px;">
                    <button class="button button--danger" @click="resetFixSequenceNumbers" :disabled="busyAction === 'reset-sequence' || !canResetSelectedSession">
                      {{ busyAction === 'reset-sequence' ? 'Resetting…' : 'Reset FIX Sequence Numbers' }}
                    </button>
                  </div>
                </div>
              </section>
            </div>
          </article>
        </section>
      </main>

      <div v-if="amendModalOpen" class="modal-backdrop" @click.self="closeAmendModal">
        <div class="modal-panel">
          <div class="modal-panel__header">
            <div>
              <h3 class="modal-panel__title">Amend order</h3>
              <p class="modal-panel__copy mono">{{ amendDraft.symbol }} · {{ amendDraft.clOrdId }}</p>
            </div>
            <button class="button button--ghost" @click="closeAmendModal">Close</button>
          </div>
          <div class="modal-panel__body">
            <div class="order-grid order-grid--comfortable">
              <div class="field">
                <span class="field__label">Quantity</span>
                <input v-model.number="amendDraft.quantity" type="number" min="1" step="1" />
              </div>
              <div class="field">
                <span class="field__label">Price</span>
                <input v-model.number="amendDraft.price" type="number" min="0" step="0.01" />
              </div>
            </div>
            <div class="modal-panel__actions">
              <button class="button button--ghost" @click="closeAmendModal">Discard</button>
              <button class="button button--primary" @click="submitAmend" :disabled="busyAction === 'order-amend'">
                {{ busyAction === 'order-amend' ? 'Saving…' : 'Save changes' }}
              </button>
            </div>
          </div>
        </div>
      </div>

      <div v-if="cancelDialogOpen" class="modal-backdrop" @click.self="closeCancelDialog">
        <div class="modal-panel modal-panel--confirm">
          <div class="modal-panel__header">
            <div>
              <h3 class="modal-panel__title">Cancel order</h3>
              <p class="modal-panel__copy">Remove {{ cancelTarget?.symbol }} · {{ cancelTarget?.clOrdId }} from the blotter and submit a FIX cancel request?</p>
            </div>
          </div>
          <div class="modal-panel__actions">
            <button class="button button--ghost" @click="closeCancelDialog">Keep order</button>
            <button class="button button--danger" @click="submitCancel" :disabled="busyAction === 'order-cancel'">
              {{ busyAction === 'order-cancel' ? 'Cancelling…' : 'Confirm cancel' }}
            </button>
          </div>
        </div>
      </div>

      <div v-if="fixMsgDetailOpen" class="modal-backdrop" @click.self="closeFixMsgDetail">
        <div class="modal-panel modal-panel--wide">
          <div class="modal-panel__header">
            <div>
              <h3 class="modal-panel__title">FIX Message Detail</h3>
              <p class="modal-panel__copy mono">{{ fixMsgDetail?.msgTypeLabel }} ({{ fixMsgDetail?.msgType }}) · {{ fixMsgDetail?.direction }} · {{ fixMsgDetail?.timestamp }}</p>
            </div>
            <button class="button button--ghost" @click="closeFixMsgDetail">Close</button>
          </div>
          <div class="modal-panel__body">
            <div class="fix-detail-sections">
              <div class="fix-detail-section">
                <span class="field__label">Full FIX message</span>
                <textarea class="fix-detail-textbox" :value="fixMsgDetailRawMessage" readonly spellcheck="false"></textarea>
              </div>
              <div class="fix-detail-section">
                <span class="field__label">Annotated view</span>
                <pre class="fix-detail-raw">{{ fixMsgDetailFormatted }}</pre>
              </div>
            </div>
          </div>
        </div>
      </div>
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
      cancels: 0,
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

    const workspaceSettingsState = reactive({
      storagePath: '',
      defaultStoragePath: '',
      profileCount: 0
    })

    const sessionProfilesState = reactive({
      activeProfileName: 'Default profile',
      profiles: [],
      fixVersionOptions: []
    })

    const recentEvents = ref([])
    const recentOrders = ref([])
    const runtimeSessions = ref([])
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
    const themeMenuOpen = ref(false)
    const refreshMenuOpen = ref(false)
    const normalizeTheme = (value) => THEME_OPTIONS.includes(value) ? value : 'system'
    const theme = ref(normalizeTheme(window.localStorage.getItem(THEME_STORAGE_KEY) || 'system'))
    const refreshFrequencyByPage = reactive(loadRefreshFrequencyMap())
    const draftInitialized = ref(false)
    const settingsInitialized = ref(false)
    const settingsDirty = ref(false)
    const suggestedSymbol = ref('AAPL')
    const selectedProfileName = ref(TheFixSessionProfileNameFallback())
    const editingProfileOriginalName = ref('')
    const storagePathDraft = ref('')
    const selectedSuggestedTagKey = ref('')
    const selectedTemplateId = ref('')
    const singleMessageViewMode = ref('fields')
    const rawFixDelimiter = ref('|')
    const rawFixInputDraft = ref('')
    const rawFixDraft = ref('')
    const rawFixParseNotice = ref('')
    const rawFixParseWarnings = ref([])
    const amendModalOpen = ref(false)
    const cancelDialogOpen = ref(false)
    const cancelTarget = ref(null)

    const fixMessages = ref([])
    const fixMsgTotal = ref(0)
    const fixMsgLimit = ref(20)
    const fixMsgOffset = ref(0)
    const fixMsgSearch = ref('')
    const fixMsgDirectionFilter = ref('')
    const fixMsgFlowFilter = ref('')
    const fixMsgHideHeartbeats = ref(false)
    const fixMsgDetailOpen = ref(false)
    const fixMsgDetail = ref(null)
    const ordersLimit = ref(10)
    const ordersOffset = ref(0)
    const ordersSearch = ref('')
    const ordersStatusFilter = ref('')
    const ordersSourceFilter = ref('')

    const amendDraft = reactive({
      clOrdId: '',
      symbol: '',
      quantity: 0,
      price: 0
    })

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
    const fixVersionOptions = computed(() => sessionProfilesState.fixVersionOptions || referenceData.fixVersions || [])
    const availableMarkets = computed(() => (referenceData.markets || []).filter(market => market.region === orderDraft.region))
    const selectedOrderType = computed(() => orderTypes.value.find(item => item.code === orderDraft.orderType) || null)
    const selectedMessageType = computed(() => messageTypes.value.find(item => item.code === orderDraft.messageType) || null)
    const activeBulkMode = computed(() => bulkModes.value.find(item => item.code === bulkDraft.bulkMode) || null)
    const needsLimitPrice = computed(() => orderDraft.messageType !== 'ORDER_CANCEL_REQUEST' && Boolean(selectedOrderType.value?.requiresLimitPrice))
    const needsStopPrice = computed(() => orderDraft.messageType !== 'ORDER_CANCEL_REQUEST' && Boolean(selectedOrderType.value?.requiresStopPrice))
    const previewWarnings = computed(() => preview.warnings || [])
    const previewStatusLabel = computed(() => preview.status || (session.connected ? 'READY_FOR_ROUTING' : 'READY_PENDING_CONNECTION'))
    const pageOptions = computed(() => PAGE_OPTIONS)
    const themeChoices = computed(() => THEME_CHOICES)
    const currentThemeLabel = computed(() => themeChoices.value.find(option => option.value === theme.value)?.label || 'System')
    const refreshFrequencyOptions = computed(() => REFRESH_FREQUENCY_OPTIONS)
    const activeRefreshSeconds = computed({
      get: () => sanitizeRefreshFrequency(refreshFrequencyByPage[activePage.value]),
      set: (seconds) => {
        refreshFrequencyByPage[activePage.value] = sanitizeRefreshFrequency(seconds)
        window.localStorage.setItem(REFRESH_FREQUENCY_STORAGE_KEY, JSON.stringify({ ...refreshFrequencyByPage }))
      }
    })
    const refreshFrequencyLabel = (seconds) => `${seconds} second${seconds === 1 ? '' : 's'}`
    const currentRefreshFrequencyLabel = computed(() => refreshFrequencyLabel(activeRefreshSeconds.value))
    const isSessionScopedPage = computed(() => ['order-input', 'order-blotter', 'fix-messages'].includes(activePage.value))
    const activeSessionProfileLabel = computed(() => session.profileName || sessionProfilesState.activeProfileName || 'Default profile')
    const selectedRuntimeSession = computed(() => (runtimeSessions.value || []).find(item => item.profileName === selectedProfileName.value) || null)
    const canResetSelectedSession = computed(() => Boolean(selectedRuntimeSession.value?.connected))
    const sessionControlCards = computed(() => {
      const cardsByProfile = new Map()
      const profileOrder = new Map((sessionProfilesState.profiles || []).map((profile, index) => [profile.name, index]))

      ;(sessionProfilesState.profiles || []).forEach(profile => {
        cardsByProfile.set(profile.name, {
          profileName: profile.name,
          senderCompId: profile.senderCompId || '',
          targetCompId: profile.targetCompId || '',
          host: profile.fixHost || profile.host || '',
          port: profile.fixPort || profile.port || '',
          fixVersionCode: profile.fixVersionCode || '',
          beginString: profile.beginString || '',
          connected: false,
          status: profile.name === sessionProfilesState.activeProfileName ? 'Standby' : 'Available'
        })
      })

      ;(runtimeSessions.value || []).forEach(runtime => {
        const existing = cardsByProfile.get(runtime.profileName) || { profileName: runtime.profileName }
        cardsByProfile.set(runtime.profileName, {
          ...existing,
          ...runtime,
          profileName: runtime.profileName || existing.profileName,
          senderCompId: runtime.senderCompId || existing.senderCompId || '',
          targetCompId: runtime.targetCompId || existing.targetCompId || '',
          host: runtime.host || existing.host || '',
          port: runtime.port || existing.port || '',
          fixVersionCode: runtime.fixVersionCode || existing.fixVersionCode || '',
          beginString: runtime.beginString || existing.beginString || '',
          connected: Boolean(runtime.connected),
          status: runtime.status || existing.status || 'Available'
        })
      })

      if (selectedProfileName.value) {
        const existing = cardsByProfile.get(selectedProfileName.value) || { profileName: selectedProfileName.value }
        cardsByProfile.set(selectedProfileName.value, {
          ...existing,
          senderCompId: existing.senderCompId || session.senderCompId || '',
          targetCompId: existing.targetCompId || session.targetCompId || '',
          host: existing.host || session.host || '',
          port: existing.port || session.port || '',
          fixVersionCode: existing.fixVersionCode || session.fixVersionCode || '',
          beginString: existing.beginString || session.beginString || '',
          connected: existing.connected || Boolean(session.connected),
          status: existing.status || session.status || 'Available'
        })
      }

      return Array.from(cardsByProfile.values())
        .map(card => ({
          ...card,
          fixVersionLabel: card.fixVersionLabel
            || fixVersionOptions.value.find(option => option.code === card.fixVersionCode)?.label
            || card.beginString
            || 'FIX 4.4',
          loaded: card.profileName === selectedProfileName.value,
          active: card.profileName === sessionProfilesState.activeProfileName,
          pendingProfileChange: card.profileName === selectedProfileName.value && Boolean(session.pendingProfileChange),
          status: card.status || (card.connected ? 'Connected' : 'Available')
        }))
        .sort((left, right) => {
          const leftIndex = profileOrder.has(left.profileName) ? profileOrder.get(left.profileName) : Number.MAX_SAFE_INTEGER
          const rightIndex = profileOrder.has(right.profileName) ? profileOrder.get(right.profileName) : Number.MAX_SAFE_INTEGER
          if (leftIndex !== rightIndex) {
            return leftIndex - rightIndex
          }
          return String(left.profileName || '').localeCompare(String(right.profileName || ''))
        })
    })
    const aboutComponents = computed(() => ABOUT_COMPONENTS)
    const aboutCapabilities = computed(() => ABOUT_CAPABILITIES)
    const activeFixVersionCode = computed(() => session.fixVersionCode || settingsDraft.fixVersionCode || 'FIX_44')
    const activeFixVersionLabel = computed(() => fixVersionOptions.value.find(option => option.code === activeFixVersionCode.value)?.label || 'FIX 4.4')
    const currentFixDictionary = computed(() => (fixMetadata.versions || []).find(version => version.code === activeFixVersionCode.value) || null)
    const selectedMessageTemplate = computed(() => messageTemplates.value.find(template => String(template.id) === String(selectedTemplateId.value)) || null)
    const orderStatusOptions = computed(() => Array.from(new Set((recentOrders.value || []).map(order => String(order?.status || '').trim()).filter(Boolean))).sort((left, right) => left.localeCompare(right)))
    const orderSourceOptions = computed(() => Array.from(new Set((recentOrders.value || []).map(order => String(order?.source || '').trim()).filter(Boolean))).sort((left, right) => left.localeCompare(right)))
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
    const normalizedRawFixDelimiter = computed(() => normalizeRawFixDelimiter(rawFixDelimiter.value))
    const normalizedRawFixDelimiterLabel = computed(() => rawFixDelimiterDisplayLabel(normalizedRawFixDelimiter.value))
    const formattedRawFixMessage = computed(() => formatRawFixMessageForDisplay(rawFixDraft.value, normalizedRawFixDelimiter.value))
    const fixMsgDetailRawMessage = computed(() => fixMsgDetail.value?.rawMessage || '')
    const isBulkFlowRunning = computed(() => Boolean(session.autoFlowActive))
    const canStartBulkFlow = computed(() => Boolean(selectedMessageType.value?.supportsBulk) && !isBulkFlowRunning.value)
    const canStopBulkFlow = computed(() => isBulkFlowRunning.value)
    const bulkFlowStartButtonClass = computed(() => isBulkFlowRunning.value ? 'button--soft' : 'button--success')
    const bulkFlowStopButtonClass = computed(() => isBulkFlowRunning.value ? 'button--danger' : 'button--ghost')
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

    const filteredFixMessages = computed(() => {
      let items = fixMessages.value || []
      if (fixMsgDirectionFilter.value) {
        items = items.filter(m => m.direction === fixMsgDirectionFilter.value)
      }
      if (fixMsgFlowFilter.value) {
        items = items.filter(m => (m.flow || 'APPLICATION') === fixMsgFlowFilter.value)
      }
      if (fixMsgHideHeartbeats.value) {
        items = items.filter(m => m.msgType !== '0')
      }
      const query = (fixMsgSearch.value || '').trim().toLowerCase()
      if (query) {
        items = items.filter(m =>
          (m.msgTypeLabel || '').toLowerCase().includes(query) ||
          (m.msgType || '').toLowerCase().includes(query) ||
          (m.direction || '').toLowerCase().includes(query) ||
          (m.flow || '').toLowerCase().includes(query) ||
          (m.preview || '').toLowerCase().includes(query) ||
          (m.timestamp || '').toLowerCase().includes(query)
        )
      }
      return items
    })

    const filteredRecentOrders = computed(() => {
      let items = recentOrders.value || []
      if (ordersStatusFilter.value) {
        items = items.filter(order => String(order?.status || '') === ordersStatusFilter.value)
      }
      if (ordersSourceFilter.value) {
        items = items.filter(order => String(order?.source || '') === ordersSourceFilter.value)
      }
      const query = (ordersSearch.value || '').trim().toLowerCase()
      if (query) {
        items = items.filter(order => [
          order?.time,
          order?.messageType,
          order?.messageTypeCode,
          order?.symbol,
          order?.side,
          order?.quantity,
          order?.limitPrice,
          order?.status,
          order?.execType,
          order?.source,
          order?.clOrdId,
          order?.region,
          order?.market,
          order?.currency,
          order?.note
        ].some(value => String(value || '').toLowerCase().includes(query)))
      }
      return items
    })
    const filteredOrdersTotal = computed(() => filteredRecentOrders.value.length)
    const paginatedRecentOrders = computed(() => filteredRecentOrders.value.slice(ordersOffset.value, ordersOffset.value + ordersLimit.value))
    const ordersRangeStart = computed(() => filteredOrdersTotal.value ? ordersOffset.value + 1 : 0)
    const ordersRangeEnd = computed(() => filteredOrdersTotal.value ? Math.min(ordersOffset.value + ordersLimit.value, filteredOrdersTotal.value) : 0)
    const ordersCountLabel = computed(() => filteredOrdersTotal.value === (recentOrders.value || []).length
      ? `${(recentOrders.value || []).length} recent`
      : `${filteredOrdersTotal.value} filtered · ${(recentOrders.value || []).length} recent`)

    const fixMsgDetailFormatted = computed(() => {
      if (!fixMsgDetail.value?.rawMessage) return ''
      return fixMsgDetail.value.rawMessage
        .split('|')
        .filter(Boolean)
        .map(segment => {
          const eqIdx = segment.indexOf('=')
          if (eqIdx >= 0) {
            const tag = segment.slice(0, eqIdx)
            const label = FIX_TAG_LABELS[tag] || 'Tag ' + tag
            return segment + '  ← ' + label
          }
          return segment
        })
        .join('\n')
    })

    const fixFieldLabel = (fieldKey, fallback) => {
      const tag = FIX_FORM_FIELD_TAGS[fieldKey]
      return tag ? `${fallback} (${tag})` : fallback
    }

    const loadFixMessages = async () => {
      try {
        const data = await apiCall('/api/fix-messages?limit=' + fixMsgLimit.value + '&offset=' + fixMsgOffset.value + '&' + selectedProfileQuery())
        fixMessages.value = data.items || []
        fixMsgTotal.value = data.total || 0
      } catch (error) {
        console.warn('Unable to load FIX messages', error)
      }
    }

    const fixMsgPrev = () => {
      fixMsgOffset.value = Math.max(0, fixMsgOffset.value - fixMsgLimit.value)
      loadFixMessages()
    }

    const fixMsgNext = () => {
      fixMsgOffset.value = fixMsgOffset.value + fixMsgLimit.value
      loadFixMessages()
    }

    const ordersPrev = () => {
      ordersOffset.value = Math.max(0, ordersOffset.value - ordersLimit.value)
    }

    const ordersNext = () => {
      ordersOffset.value = Math.min(ordersOffset.value + ordersLimit.value, Math.max(filteredOrdersTotal.value - 1, 0))
    }

    const openFixMsgDetail = (msg) => {
      fixMsgDetail.value = msg
      fixMsgDetailOpen.value = true
    }

    const closeFixMsgDetail = () => {
      fixMsgDetailOpen.value = false
      fixMsgDetail.value = null
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

    const selectedProfileQuery = () => `profileName=${encodeURIComponent(selectedProfileName.value || '')}`
    const withSelectedProfile = (payload = {}) => ({
      ...payload,
      profileName: selectedProfileName.value || ''
    })

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

    function normalizeRawFixDelimiter(rawValue) {
      const trimmed = String(rawValue ?? '').trim()
      if (!trimmed) {
        return '|'
      }
      const normalized = trimmed.toUpperCase()
      if (normalized === 'SOH' || normalized === '^A' || normalized === '\\U0001' || normalized === '\\X01') {
        return '\u0001'
      }
      if (normalized === '\\T' || normalized === 'TAB') {
        return '\t'
      }
      if (normalized === '\\N' || normalized === 'NEWLINE') {
        return '\n'
      }
      return trimmed
    }

    function rawFixDelimiterDisplayLabel(delimiter) {
      if (delimiter === '\u0001') {
        return 'SOH'
      }
      if (delimiter === '\t') {
        return 'TAB'
      }
      if (delimiter === '\n') {
        return 'NEWLINE'
      }
      return delimiter
    }

    function buildGeneratedRawFixFields() {
      const fields = [
        { tag: '8', value: session.beginString || 'FIX.4.4' },
        { tag: '35', value: selectedMessageType.value?.msgType || 'D' },
        { tag: '49', value: session.senderCompId || settingsDraft.senderCompId || 'THEFIX_TRDR01' },
        { tag: '56', value: session.targetCompId || settingsDraft.targetCompId || 'LLEXSIM' }
      ]

      if (orderDraft.clOrdId) {
        fields.push({ tag: '11', value: orderDraft.clOrdId })
      }
      if (selectedMessageType.value?.requiresOrigClOrdId && orderDraft.origClOrdId) {
        fields.push({ tag: '41', value: orderDraft.origClOrdId })
      }

      fields.push(
        { tag: '55', value: orderDraft.symbol || '' },
        { tag: '54', value: SIDE_TO_FIX[orderDraft.side] || orderDraft.side || '' },
        { tag: '207', value: orderDraft.market || '' },
        { tag: '15', value: orderDraft.currency || '' }
      )

      if (selectedMessageType.value?.code !== 'ORDER_CANCEL_REQUEST') {
        fields.push(
          { tag: '38', value: String(orderDraft.quantity ?? '') },
          { tag: '40', value: ORDER_TYPE_TO_FIX[orderDraft.orderType] || orderDraft.orderType || '' },
          { tag: '59', value: TIF_TO_FIX[orderDraft.timeInForce] || orderDraft.timeInForce || '' },
          { tag: '423', value: PRICE_TYPE_TO_FIX[orderDraft.priceType] || orderDraft.priceType || '' }
        )

        if (needsLimitPrice.value) {
          fields.push({ tag: '44', value: String(orderDraft.price ?? '') })
        }
        if (needsStopPrice.value) {
          fields.push({ tag: '99', value: String(orderDraft.stopPrice ?? '') })
        }
      } else if (orderDraft.quantity) {
        fields.push({ tag: '38', value: String(orderDraft.quantity) })
      }

      ;(orderDraft.additionalTags || []).forEach(tag => {
        const numericTag = String(tag?.tag ?? '').trim()
        const value = String(tag?.value ?? '').trim()
        if (numericTag && value) {
          fields.push({ tag: numericTag, value })
        }
      })

      return fields.filter(field => String(field.value ?? '').trim() !== '')
    }

    function buildGeneratedRawFixMessage() {
      const delimiter = normalizedRawFixDelimiter.value
      const body = buildGeneratedRawFixFields().map(field => `${field.tag}=${field.value}`).join(delimiter)
      return body ? `${body}${delimiter}` : ''
    }

    function parseRawFixFields(rawMessage, delimiter) {
      if (!rawMessage) {
        return []
      }
      return rawMessage
        .split(delimiter)
        .map(segment => segment.trim())
        .filter(Boolean)
        .map((segment, index) => {
          const equalsIndex = segment.indexOf('=')
          const tag = equalsIndex >= 0 ? segment.slice(0, equalsIndex).trim() : `segment-${index + 1}`
          const value = equalsIndex >= 0 ? segment.slice(equalsIndex + 1) : segment
          return {
            index,
            tag,
            name: FIX_TAG_LABELS[tag] || 'Custom field',
            value
          }
        })
    }

    function formatRawFixMessageForDisplay(rawMessage, delimiter) {
      if (!rawMessage) {
        return 'No raw FIX message available yet.'
      }
      const visibleDelimiter = delimiter === '\u0001' ? '<SOH>' : rawFixDelimiterDisplayLabel(delimiter)
      return rawMessage
        .split(delimiter)
        .map(segment => segment.trim())
        .filter(Boolean)
        .map(segment => `${segment}${visibleDelimiter}`)
        .join('\n')
    }

    function applyParsedRawFixToDraft(parsedFields) {
      const fieldMap = new Map(parsedFields.map(field => [field.tag, field.value]))
      const messageTypeByFix = Object.fromEntries((messageTypes.value || []).map(item => [item.msgType, item.code]))
      const nextMessageType = messageTypeByFix[fieldMap.get('35')] || orderDraft.messageType
      const parsedMarket = fieldMap.get('207') || orderDraft.market
      const parsedMarketDefinition = (referenceData.markets || []).find(item => item.code === parsedMarket)
      orderDraft.messageType = nextMessageType
      orderDraft.clOrdId = fieldMap.get('11') || ''
      orderDraft.origClOrdId = fieldMap.get('41') || ''
      orderDraft.symbol = fieldMap.get('55') || orderDraft.symbol
      orderDraft.side = FIX_TO_SIDE[fieldMap.get('54')] || orderDraft.side
      orderDraft.region = parsedMarketDefinition?.region || orderDraft.region
      orderDraft.market = parsedMarket
      orderDraft.currency = (fieldMap.get('15') || parsedMarketDefinition?.currency || orderDraft.currency || '').toUpperCase()

      const parsedQty = Number(fieldMap.get('38'))
      if (Number.isFinite(parsedQty) && parsedQty >= 0) {
        orderDraft.quantity = parsedQty
      }

      const parsedPrice = Number(fieldMap.get('44'))
      if (Number.isFinite(parsedPrice)) {
        orderDraft.price = parsedPrice
      }

      const parsedStopPrice = Number(fieldMap.get('99'))
      if (Number.isFinite(parsedStopPrice)) {
        orderDraft.stopPrice = parsedStopPrice
      }

      orderDraft.orderType = FIX_TO_ORDER_TYPE[fieldMap.get('40')] || orderDraft.orderType
      orderDraft.timeInForce = FIX_TO_TIF[fieldMap.get('59')] || orderDraft.timeInForce
      orderDraft.priceType = FIX_TO_PRICE_TYPE[fieldMap.get('423')] || orderDraft.priceType

      const coreTags = new Set(['8', '11', '15', '21', '35', '38', '40', '41', '44', '49', '54', '55', '56', '58', '59', '60', '98', '99', '108', '141', '207', '423'])
      orderDraft.additionalTags = parsedFields
        .filter(field => /^\d+$/.test(field.tag) && !coreTags.has(field.tag))
        .map(field => ({ tag: Number(field.tag), name: field.name === 'Custom field' ? '' : field.name, value: field.value, custom: true }))
    }

    const syncFieldValuesToRawFixDraft = () => {
      const generatedRawFixMessage = buildGeneratedRawFixMessage()
      rawFixDraft.value = generatedRawFixMessage
      rawFixInputDraft.value = generatedRawFixMessage
      rawFixParseNotice.value = 'Loaded raw FIX content from the current field values.'
      rawFixParseWarnings.value = []
      return rawFixDraft.value
    }

    const parseRawFixInputByDelimiter = (applyToFields = false) => {
      const candidateRawFix = rawFixInputDraft.value
      const parsedFields = parseRawFixFields(candidateRawFix, normalizedRawFixDelimiter.value)
      if (!parsedFields.length) {
        rawFixParseWarnings.value = ['Provide a raw FIX message before parsing it.']
        rawFixParseNotice.value = ''
        return false
      }
      rawFixDraft.value = candidateRawFix
      if (applyToFields) {
        applyParsedRawFixToDraft(parsedFields)
      }
      rawFixParseWarnings.value = []
      rawFixParseNotice.value = `Parsed ${parsedFields.length} FIX fields using delimiter ${normalizedRawFixDelimiterLabel.value}.`
      return true
    }

    const parseRawFixMessageToFields = () => {
      if (parseRawFixInputByDelimiter(true)) {
        singleMessageViewMode.value = 'fields'
      }
    }

    const toggleSingleMessageViewMode = () => {
      const nextMode = singleMessageViewMode.value === 'fields' ? 'raw' : 'fields'
      rawFixParseWarnings.value = []
      if (nextMode === 'raw') {
        syncFieldValuesToRawFixDraft()
        singleMessageViewMode.value = 'raw'
        return
      }
      if (parseRawFixInputByDelimiter(true)) {
        singleMessageViewMode.value = 'fields'
      }
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
      editingProfileOriginalName.value = profile?.name || ''
      settingsDirty.value = false
    }

    const applyWorkspaceSettings = (settings) => {
      Object.assign(workspaceSettingsState, {
        storagePath: settings?.storagePath || '',
        defaultStoragePath: settings?.defaultStoragePath || '',
        profileCount: Number(settings?.profileCount || 0)
      })
      storagePathDraft.value = workspaceSettingsState.storagePath
    }

    const applySessionProfiles = (settings, preserveDraft = true) => {
      Object.assign(sessionProfilesState, {
        activeProfileName: settings?.activeProfileName || 'Default profile',
        profiles: settings?.profiles || [],
        fixVersionOptions: settings?.fixVersionOptions || []
      })
      if (!selectedProfileName.value || !sessionProfilesState.profiles.some(profile => profile.name === selectedProfileName.value)) {
        selectedProfileName.value = sessionProfilesState.activeProfileName
      }
      if (!settingsInitialized.value || !preserveDraft || !settingsDirty.value) {
        syncSettingsDraft(settings?.profileDraft || sessionProfilesState.profiles.find(profile => profile.name === sessionProfilesState.activeProfileName))
      }
      settingsInitialized.value = true
    }

    const shouldKeepConnectPending = (nextSession) => {
      const normalizedStatus = String(nextSession?.status || '').trim().toLowerCase()
      if (nextSession?.connected) {
        return false
      }
      return normalizedStatus.includes('connect')
    }

    const shouldClearConnectPending = (nextSession) => {
      const normalizedStatus = String(nextSession?.status || '').trim().toLowerCase()
      if (nextSession?.connected) {
        return true
      }
      return normalizedStatus === 'error' || normalizedStatus === 'standby'
    }

    const reconcileSessionActionPending = (nextSession = session) => {
      if (sessionActionPending.value === 'connect') {
        if (shouldClearConnectPending(nextSession)) {
          sessionActionPending.value = ''
          return
        }
        if (shouldKeepConnectPending(nextSession)) {
          session.status = nextSession.status || 'Connecting…'
          return
        }
        sessionActionPending.value = ''
        return
      }

      if (sessionActionPending.value === 'disconnect' && !nextSession?.connected) {
        sessionActionPending.value = ''
      }
    }

    const syncSelectedProfileDraft = (profileName) => {
      if (!profileName) {
        return
      }
      selectedProfileName.value = profileName
      const profile = sessionProfilesState.profiles.find(item => item.name === profileName)
      if (profile) {
        syncSettingsDraft(profile)
      }
    }

    const runtimeSessionToneClass = (runtime) => {
      if (!runtime) {
        return 'chip--neutral'
      }
      const status = String(runtime.status || '').trim().toLowerCase()
      if (runtime.connected) {
        return 'chip--success'
      }
      if (status.includes('error') || status.includes('reject')) {
        return 'chip--danger'
      }
      if (status.includes('connect')) {
        return 'chip--warn'
      }
      return 'chip--neutral'
    }

    const runtimeSessionCardClass = (runtime) => ({
      'runtime-session-card--selected': runtime?.profileName === selectedProfileName.value,
      'runtime-session-card--connected': Boolean(runtime?.connected)
    })

    const runtimeSessionIsConnecting = (runtime) => {
      if (!runtime || runtime.connected) {
        return false
      }
      return String(runtime.status || '').trim().toLowerCase().includes('connect')
    }

    const runtimeSessionButtonLabel = (runtime) => {
      if (!runtime) return 'Connect'
      if (runtimeSessionIsConnecting(runtime)) return 'Stop reconnecting'
      return runtime.connected ? 'Disconnect' : 'Connect'
    }

    const runtimeSessionButtonClass = (runtime) => {
      if (!runtime) return 'button--success'
      if (runtimeSessionIsConnecting(runtime)) return 'button--danger-outline'
      return runtime.connected ? 'button--danger' : 'button--success'
    }

    const runtimeSessionButtonDisabled = (runtime) => {
      if (!runtime || !sessionActionPending.value) {
        return false
      }
      const isSelectedRuntime = runtime.profileName === selectedProfileName.value
      if (!isSelectedRuntime) {
        return true
      }
      return sessionActionPending.value === 'disconnect'
    }

    const sessionControlCardCopy = (card) => {
      if (!card) {
        return 'Available session profile.'
      }
      if (!canDeleteSessionCard(card)) {
        return card.connected
          ? 'Disconnect this runtime before deleting the profile.'
          : ''
      }
      if (card.pendingProfileChange) {
        return 'Reconnect required to apply the loaded profile to the live FIX session.'
      }
      if (runtimeSessionIsConnecting(card)) {
        return 'This runtime is reconnecting. Use the reconnect control to stop the initiator if needed.'
      }
      if (card.connected) {
        return 'Live runtime connected for this session profile.'
      }
      if (card.loaded) {
        return ''
      }
      return 'Available session profile. Load it into the main workspace when needed.'
    }

    const canDeleteSessionCard = (card) => {
      if (!card?.profileName) {
        return false
      }
      if (card.connected) {
        return false
      }
      return sessionControlCards.value.length > 1
    }

    const applyOverview = (payload, preserveSettingsDraft = true, preservePendingSessionState = false) => {
      Object.assign(overview, {
        applicationName: payload.applicationName
      })
      const nextSession = payload.session || {}
      const preserveConnectProgress = preservePendingSessionState
        && sessionActionPending.value === 'connect'
        && shouldKeepConnectPending(nextSession)
      const preserveDisconnectProgress = preservePendingSessionState
        && sessionActionPending.value === 'disconnect'
        && Boolean(nextSession.connected)

      if (preserveConnectProgress || preserveDisconnectProgress) {
        Object.assign(session, {
          ...nextSession,
          connected: session.connected,
          status: session.status
        })
      } else {
        Object.assign(session, nextSession)
      }
      Object.assign(kpis, payload.kpis || {})
      Object.assign(referenceData, payload.referenceData || {})
      recentEvents.value = payload.recentEvents || []
      recentOrders.value = payload.recentOrders || []
      runtimeSessions.value = payload.runtimeSessions || []

      if (!draftInitialized.value) {
        initializeDrafts(payload.defaults || {})
      } else if (!availableMarkets.value.find(market => market.code === orderDraft.market) && availableMarkets.value.length) {
        orderDraft.market = availableMarkets.value[0].code
        applyMarketDefaults(true)
      }

      if (payload.settings) {
        applyWorkspaceSettings(payload.settings)
      }

      if (payload.sessionProfiles) {
        applySessionProfiles(payload.sessionProfiles, preserveSettingsDraft)
      } else if (payload.settings?.profiles) {
        applySessionProfiles(payload.settings, preserveSettingsDraft)
      }

      reconcileSessionActionPending(nextSession)

      apiStatus.value = 'Live'
    }

    const loadOverview = async () => {
      if (overviewRefreshPending.value) {
        return
      }
      overviewRefreshPending.value = true
      try {
        applyOverview(await apiCall('/api/overview?' + selectedProfileQuery()), true, true)
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
    }

    const loadTemplates = async () => {
      try {
        applyTemplates(await apiCall('/api/templates?' + selectedProfileQuery()))
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
      applyTemplateDraft(selectedTemplate.draft || {})
    }

    const resolvedTemplateSaveName = () => {
      const existingTemplateName = String(selectedMessageTemplate.value?.name || '').trim()
      if (existingTemplateName) {
        return existingTemplateName
      }
      const messageLabel = String(selectedMessageType.value?.label || 'FIX message').trim()
      const symbol = String(orderDraft.symbol || '').trim()
      const descriptor = symbol ? `${messageLabel} · ${symbol}` : messageLabel
      const timestamp = new Date().toISOString().replace('T', ' ').replace('Z', ' UTC')
      return `Saved · ${timestamp} · ${descriptor}`
    }

    const saveCurrentTemplate = async () => runAction('save-template', async () => {
      applyTemplates(await apiCall('/api/templates/save', {
        method: 'POST',
        body: JSON.stringify({
          profileName: selectedProfileName.value || '',
          name: resolvedTemplateSaveName(),
          draft: buildOrderPayload()
        })
      }))
    })

    const openAmendModal = (order) => {
      amendDraft.clOrdId = order?.clOrdId || ''
      amendDraft.symbol = order?.symbol || ''
      amendDraft.quantity = Number(order?.quantity || 0)
      amendDraft.price = Number(order?.limitPrice || 0)
      amendModalOpen.value = true
    }

    const closeAmendModal = () => {
      amendModalOpen.value = false
      amendDraft.clOrdId = ''
      amendDraft.symbol = ''
      amendDraft.quantity = 0
      amendDraft.price = 0
    }

    const submitAmend = async () => runAction('order-amend', async () => {
      const payload = await apiCall('/api/orders/amend', {
        method: 'POST',
        body: JSON.stringify({
          profileName: selectedProfileName.value || '',
          clOrdId: amendDraft.clOrdId,
          quantity: Number(amendDraft.quantity || 0),
          price: Number(amendDraft.price || 0)
        })
      })
      applyOverview(payload, true)
      if (payload?.actionResult?.success) {
        closeAmendModal()
        return
      }
      window.alert(payload?.actionResult?.message || 'Unable to amend the selected order.')
    })

    const openCancelDialog = (order) => {
      cancelTarget.value = order
      cancelDialogOpen.value = true
    }

    const closeCancelDialog = () => {
      cancelDialogOpen.value = false
      cancelTarget.value = null
    }

    const submitCancel = async () => runAction('order-cancel', async () => {
      if (!cancelTarget.value?.clOrdId) {
        closeCancelDialog()
        return
      }
      const payload = await apiCall('/api/orders/cancel', {
        method: 'POST',
        body: JSON.stringify({ profileName: selectedProfileName.value || '', clOrdId: cancelTarget.value.clOrdId })
      })
      applyOverview(payload, true)
      if (payload?.actionResult?.success) {
        closeCancelDialog()
        return
      }
      window.alert(payload?.actionResult?.message || 'Unable to cancel the selected order.')
    })

    const runOverviewRefreshCycle = async () => {
      await loadOverview()
      if (activePage.value === 'fix-messages') {
        await loadFixMessages()
      }
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

    const buildBulkOrderPayload = () => ({
      ...buildOrderPayload(),
      additionalTags: []
    })

    const buildSettingsPayload = () => ({
      originalName: (editingProfileOriginalName.value || settingsDraft.name || '').trim(),
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
      if (sessionActionPending.value && !(sessionActionPending.value === 'connect' && name === 'disconnect')) {
        return
      }
      sessionActionPending.value = name
      session.status = name === 'connect' ? 'Connecting…' : 'Disconnecting…'
      try {
        await work()
      } finally {
        if (name === 'disconnect') {
          sessionActionPending.value = ''
        } else {
          reconcileSessionActionPending(session)
        }
      }
    }

    const previewTicket = async () => {
      const draftPayload = activeMode.value === 'bulk' ? buildBulkOrderPayload() : buildOrderPayload()
      const payload = await apiCall('/api/order-ticket/preview', {
        method: 'POST',
        body: JSON.stringify(withSelectedProfile(draftPayload))
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
      const payload = await apiCall('/api/session/connect', {
        method: 'POST',
        body: JSON.stringify(withSelectedProfile())
      })
      applyOverview(payload, true)
      if (payload?.actionResult && payload.actionResult.success === false) {
        window.alert(payload.actionResult.message || 'Unable to connect the selected session profile.')
      }
    }))
    const disconnectSession = async () => runSessionAction('disconnect', async () => runAction('disconnect', async () => {
      if (session.autoFlowActive) {
        applyOverview(await apiCall('/api/order-flow/stop', {
          method: 'POST',
          body: JSON.stringify(withSelectedProfile())
        }), true)
      }
      applyOverview(await apiCall('/api/session/disconnect', {
        method: 'POST',
        body: JSON.stringify(withSelectedProfile())
      }), true)
      if (activePage.value === 'fix-messages') {
        await loadFixMessages()
        window.setTimeout(() => {
          if (activePage.value === 'fix-messages') {
            loadFixMessages().catch(() => {})
          }
        }, 750)
      }
    }))
    const pulseTest = async () => runAction('pulse', async () => applyOverview(await apiCall('/api/session/pulse-test', {
      method: 'POST',
      body: JSON.stringify(withSelectedProfile())
    }), true))
    const resetFixSequenceNumbers = async () => runAction('reset-sequence', async () => {
      const payload = await apiCall('/api/session/reset-sequence', {
        method: 'POST',
        body: JSON.stringify(withSelectedProfile())
      })
      applyOverview(payload, true)
      if (!payload?.actionResult?.success) {
        window.alert(payload?.actionResult?.message || 'Unable to reset FIX sequence numbers for the selected session profile.')
      }
    })

    const connectProfile = async (profileName) => {
      syncSelectedProfileDraft(profileName)
      await connectSession()
    }

    const selectRuntimeProfile = (profileName) => {
      syncSelectedProfileDraft(profileName)
      loadOverview().catch(() => {})
      loadTemplates().catch(() => {})
      if (activePage.value === 'fix-messages') {
        fixMsgOffset.value = 0
        loadFixMessages().catch(() => {})
      }
    }

    const disconnectProfile = async (profileName) => {
      syncSelectedProfileDraft(profileName)
      await disconnectSession()
    }

    const loadSessionCard = (profileName) => {
      if (!profileName) {
        return
      }
      syncSelectedProfileDraft(profileName)
      loadSelectedProfile()
    }

    const editSessionCard = (profileName) => {
      if (!profileName) {
        return
      }
      syncSelectedProfileDraft(profileName)
      const profile = sessionProfilesState.profiles.find(item => item.name === profileName)
      if (profile) {
        syncSettingsDraft(profile)
      }
      openPage('session-profiles')
    }

    const deleteSelectedProfile = async (profileName = selectedProfileName.value) => runAction('delete-profile', async () => {
      const targetProfileName = String(profileName || '').trim()
      if (!targetProfileName) {
        return
      }
      const targetCard = sessionControlCards.value.find(card => card.profileName === targetProfileName)
      if (!canDeleteSessionCard(targetCard)) {
        window.alert(targetCard?.connected
          ? 'Disconnect this session before deleting its profile.'
          : 'At least one session profile must remain available.')
        return
      }
      const confirmed = window.confirm(`Delete FIX session profile "${targetProfileName}"? This removes the saved profile from the workstation.`)
      if (!confirmed) {
        return
      }
      const payload = await apiCall('/api/session-profiles/delete', {
        method: 'POST',
        body: JSON.stringify({ name: targetProfileName })
      })
      applyOverview(payload, false)
      if (!payload?.actionResult?.success) {
        window.alert(payload?.actionResult?.message || 'Unable to delete the selected session profile.')
        return
      }
      await loadTemplates()
      if (activePage.value === 'fix-messages') {
        fixMsgOffset.value = 0
        await loadFixMessages()
      }
    })

    const deleteSessionCard = (card) => {
      deleteSelectedProfile(card?.profileName)
    }

    const toggleRuntimeSession = async (runtime) => {
      if (!runtime?.profileName) {
        return
      }
      if (runtime.connected || runtimeSessionIsConnecting(runtime)) {
        await disconnectProfile(runtime.profileName)
      } else {
        await connectProfile(runtime.profileName)
      }
    }

    const sendTicket = async () => runAction('send', async () => {
      await previewTicket()
      applyOverview(await apiCall('/api/order-ticket/send', {
        method: 'POST',
        body: JSON.stringify(withSelectedProfile(buildOrderPayload()))
      }), true)
    })

    const startBulkFlow = async () => runAction('flow-start', async () => {
      await previewTicket()
      const payload = await apiCall('/api/order-flow/start', {
        method: 'POST',
        body: JSON.stringify(withSelectedProfile({
          ...buildBulkOrderPayload(),
          bulkMode: bulkDraft.bulkMode,
          totalOrders: Number(bulkDraft.totalOrders || 0),
          ratePerSecond: Number(bulkDraft.ratePerSecond || 0),
          burstSize: Number(bulkDraft.burstSize || 0),
          burstIntervalMs: Number(bulkDraft.burstIntervalMs || 0)
        }))
      })
      applyOverview(payload, true)
      if (payload?.actionResult && payload.actionResult.success === false) {
        window.alert(payload.actionResult.message || 'Unable to start bulk flow for the selected session profile.')
      }
    })

    const stopBulkFlow = async () => runAction('flow-stop', async () => applyOverview(await apiCall('/api/order-flow/stop', {
      method: 'POST',
      body: JSON.stringify(withSelectedProfile())
    }), true))

    const saveSettingsProfile = async () => runAction('save-profile', async () => {
      const payload = await apiCall('/api/session-profiles/save', {
        method: 'POST',
        body: JSON.stringify(buildSettingsPayload())
      })
      applyOverview(payload, false)
      selectedProfileName.value = settingsDraft.name
      editingProfileOriginalName.value = settingsDraft.name
    })

    const activateSelectedProfile = async () => runAction('activate-profile', async () => {
      const payload = await apiCall('/api/session-profiles/activate', {
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
      const profile = sessionProfilesState.profiles.find(item => item.name === selectedProfileName.value)
      if (profile) {
        syncSettingsDraft(profile)
      }
      loadOverview().catch(() => {})
      loadTemplates().catch(() => {})
      if (activePage.value === 'fix-messages') {
        loadFixMessages().catch(() => {})
      }
    }

    const resetSettingsDraft = () => {
      const profile = sessionProfilesState.profiles.find(item => item.name === sessionProfilesState.activeProfileName)
      syncSettingsDraft(profile)
    }

    const startNewProfile = () => {
      const activeProfile = sessionProfilesState.profiles.find(item => item.name === sessionProfilesState.activeProfileName)
      syncSettingsDraft({ ...(activeProfile || emptySettingsDraft()), name: 'New profile' })
      editingProfileOriginalName.value = ''
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
      if (replaceAlias && (currentPath === '/' || currentPath === '/index.html' || currentPath === '/order' || currentPath === '/blotter' || currentPath === '/recentfixmsgs' || currentPath === '/sessionprofiles')) {
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

    const closeTopbarPanels = () => {
      menuOpen.value = false
      themeMenuOpen.value = false
      refreshMenuOpen.value = false
    }

    const toggleMenu = () => {
      const nextState = !menuOpen.value
      closeTopbarPanels()
      menuOpen.value = nextState
    }

    const toggleThemeMenu = () => {
      const nextState = !themeMenuOpen.value
      closeTopbarPanels()
      themeMenuOpen.value = nextState
    }

    const toggleRefreshMenu = () => {
      const nextState = !refreshMenuOpen.value
      closeTopbarPanels()
      refreshMenuOpen.value = nextState
    }

    const selectTheme = (nextTheme) => {
      theme.value = normalizeTheme(nextTheme)
      closeTopbarPanels()
    }

    const selectRefreshFrequency = (seconds) => {
      activeRefreshSeconds.value = seconds
      closeTopbarPanels()
    }

    const openPage = (pageCode) => {
      navigateToPage(pageCode)
      closeTopbarPanels()
    }

    const goBack = () => {
      closeMenu()
      window.history.back()
    }

    const goForward = () => {
      closeMenu()
      window.history.forward()
    }

    const goHome = () => {
      openPage('order-input')
    }

    const desktopMenuOpen = ref('')

    const toggleDesktopMenu = (menuKey) => {
      desktopMenuOpen.value = desktopMenuOpen.value === menuKey ? '' : menuKey
      closeTopbarPanels()
    }

    const closeMenu = () => {
      closeTopbarPanels()
      desktopMenuOpen.value = ''
    }

    watch(theme, applyTheme, { immediate: true })

    watch(() => activePage.value, (nextPage) => {
      runOverviewRefreshCycle().catch(() => {})
      if (nextPage === 'fix-messages') {
        fixMsgOffset.value = 0
        loadFixMessages().catch(() => {})
      }
    })

    watch(() => selectedProfileName.value, () => {
      selectedTemplateId.value = ''
      ordersOffset.value = 0
      loadOverview().catch(() => {})
      loadTemplates().catch(() => {})
      if (activePage.value === 'fix-messages') {
        fixMsgOffset.value = 0
        loadFixMessages().catch(() => {})
      }
    })

    watch([ordersLimit, ordersSearch, ordersStatusFilter, ordersSourceFilter], () => {
      ordersOffset.value = 0
    })

    watch([filteredOrdersTotal, ordersLimit], () => {
      if (!filteredOrdersTotal.value) {
        ordersOffset.value = 0
        return
      }
      if (ordersOffset.value >= filteredOrdersTotal.value) {
        ordersOffset.value = Math.max(0, Math.floor((filteredOrdersTotal.value - 1) / ordersLimit.value) * ordersLimit.value)
      }
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
      runtimeSessions,
      messageTemplates,
      apiStatus,
      activePage,
      activeMode,
      singleMessageViewMode,
      menuOpen,
      themeMenuOpen,
      refreshMenuOpen,
      pageOptions,
      busyAction,
      theme,
      themeChoices,
      currentThemeLabel,
      refreshFrequencyOptions,
      activeRefreshSeconds,
      currentRefreshFrequencyLabel,
      desktopMenuOpen,
      isSessionScopedPage,
      activeSessionProfileLabel,
      selectedRuntimeSession,
      canResetSelectedSession,
      sessionControlCards,
      aboutComponents,
      aboutCapabilities,
      editingProfileOriginalName,
      orderDraft,
      bulkDraft,
      preview,
      previewStatusLabel,
      previewWarnings,
      rawFixDelimiter,
      rawFixInputDraft,
      rawFixDraft,
      rawFixParseNotice,
      rawFixParseWarnings,
      normalizedRawFixDelimiterLabel,
      formattedRawFixMessage,
      fixMsgDetailRawMessage,
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
      isBulkFlowRunning,
      canStartBulkFlow,
      canStopBulkFlow,
      bulkFlowStartButtonClass,
      bulkFlowStopButtonClass,
      needsLimitPrice,
      needsStopPrice,
      selectedSuggestedTagKey,
      selectedTemplateId,
      amendModalOpen,
      cancelDialogOpen,
      cancelTarget,
      amendDraft,
      fixMessages,
      fixMsgTotal,
      fixMsgLimit,
      fixMsgOffset,
      fixMsgSearch,
      fixMsgDirectionFilter,
      fixMsgFlowFilter,
      fixMsgHideHeartbeats,
      fixMsgDetailOpen,
      fixMsgDetail,
      fixMsgDetailFormatted,
      filteredFixMessages,
      filteredRecentOrders,
      filteredOrdersTotal,
      paginatedRecentOrders,
      ordersRangeStart,
      ordersRangeEnd,
      ordersCountLabel,
      loadFixMessages,
      fixMsgPrev,
      fixMsgNext,
      ordersLimit,
      ordersOffset,
      ordersSearch,
      ordersStatusFilter,
      ordersSourceFilter,
      orderStatusOptions,
      orderSourceOptions,
      ordersPrev,
      ordersNext,
      openFixMsgDetail,
      closeFixMsgDetail,
      sessionActionBusy,
      sessionButtonLabel,
      runtimeSessionToneClass,
      runtimeSessionCardClass,
      runtimeSessionIsConnecting,
      runtimeSessionButtonLabel,
      runtimeSessionButtonClass,
      runtimeSessionButtonDisabled,
      sessionControlCardCopy,
      canDeleteSessionCard,
      fixFieldLabel,
      execTypeBadge,
      blotterSideClass,
      blotterStatusClass,
      blotterSourceClass,
      orderRowClass,
      workspaceSettingsState,
      sessionProfilesState,
      settingsDraft,
      selectedProfileName,
      storagePathDraft,
      regionLabel,
      connectSession,
      disconnectSession,
      connectProfile,
      disconnectProfile,
      loadSessionCard,
      editSessionCard,
      deleteSessionCard,
      deleteSelectedProfile,
      selectRuntimeProfile,
      toggleRuntimeSession,
      pulseTest,
      resetFixSequenceNumbers,
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
      parseRawFixInputByDelimiter,
      parseRawFixMessageToFields,
      toggleSingleMessageViewMode,
      applySelectedTemplate,
      saveCurrentTemplate,
      openAmendModal,
      closeAmendModal,
      submitAmend,
      openCancelDialog,
      closeCancelDialog,
      submitCancel,
      onRegionChange,
      onMarketChange,
      goBack,
      goForward,
      goHome,
      toggleMenu,
      toggleThemeMenu,
      toggleRefreshMenu,
      toggleDesktopMenu,
      selectTheme,
      selectRefreshFrequency,
      refreshFrequencyLabel,
      openPage
    }
  }
}).mount('#app')
