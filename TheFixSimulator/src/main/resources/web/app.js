import { createApp, computed, ref, reactive, onMounted, onUnmounted, nextTick, watch } from 'https://unpkg.com/vue@3/dist/vue.esm-browser.prod.js'

const BEHAVIOR_TYPES = [
  { value: 'IMMEDIATE_FULL_FILL', label: 'Immediate Full Fill' },
  { value: 'PARTIAL_FILL', label: 'Partial Fill' },
  { value: 'DELAYED_FILL', label: 'Delayed Fill' },
  { value: 'REJECT', label: 'Reject All' },
  { value: 'PARTIAL_THEN_CANCEL', label: 'Partial Fill then Cancel' },
  { value: 'PRICE_IMPROVEMENT', label: 'Price Improvement' },
  { value: 'FILL_AT_ARRIVAL_PRICE', label: 'Fill at Arrival Price' },
  { value: 'RANDOM_FILL', label: 'Random Fill' },
  { value: 'NO_FILL_IOC_CANCEL', label: 'No Fill / IOC Cancel' },
  { value: 'RANDOM_REJECT_CANCEL', label: 'Random Reject / Cancel' }
]

const REJECT_REASONS = [
  'SIMULATOR_REJECT', 'UNKNOWN_SYMBOL', 'HALTED', 'INVALID_PRICE', 'NOT_AUTHORIZED', 'STALE_ORDER'
]

const METRICS_REFRESH_OPTIONS = [1, 5, 10, 30, 60]
const PAGE_OPTIONS = Object.freeze([
  { code: 'dashboard', label: 'Dashboard' },
  { code: 'settings', label: 'Settings' }
])
const PAGE_PATHS = Object.freeze({ dashboard: '/', settings: '/settings' })
const THEME_STORAGE_KEY = 'llexsimulator-theme'
const THEME_CHOICES = Object.freeze([
  { value: 'system', label: 'System' },
  { value: 'dark', label: 'Dark' },
  { value: 'light', label: 'Light' }
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
      return 'dashboard'
    case '/settings':
      return 'settings'
    default:
      return ''
  }
}

function pagePathForCode(pageCode) {
  return PAGE_PATHS[pageCode] || PAGE_PATHS.dashboard
}

createApp({
  template: `
  <div class="min-h-screen bg-dark-900">
    <header class="bg-dark-800 border-b border-dark-600 px-6 py-3 flex items-center justify-between gap-4 flex-wrap">
      <div class="flex items-center gap-3 flex-wrap">
        <div class="w-8 h-8 rounded-lg bg-brand-600 flex items-center justify-center text-xs font-bold text-white">FX</div>
        <div>
          <h1 class="text-lg font-semibold text-white">LLExSimulator</h1>
          <span class="text-xs text-slate-400 font-mono">FIX Exchange Simulator</span>
        </div>
        <div class="flex items-center gap-2 ml-2 flex-wrap">
          <button
            v-for="page in pageOptions"
            :key="page.code"
            @click="openPage(page.code)"
            class="text-xs font-semibold rounded-full px-3 py-1.5 transition-colors border"
            :class="activePage === page.code
              ? 'bg-brand-600 text-white border-brand-500'
              : 'text-slate-300 hover:text-white border-dark-600 hover:border-brand-500'">
            {{ page.label }}
          </button>
          <a href="/reports"
             class="text-xs font-semibold text-slate-300 hover:text-white border border-dark-600 hover:border-brand-500 rounded-full px-3 py-1.5 transition-colors">
            Reports
          </a>
        </div>
      </div>
      <div class="flex items-center gap-4 flex-wrap justify-end">
        <span :class="wsConnected ? 'text-brand-500' : 'text-red-400'" class="text-xs font-mono flex items-center gap-1">
          <span :class="wsConnected ? 'bg-brand-500' : 'bg-red-400'" class="w-2 h-2 rounded-full inline-block"></span>
          {{ wsConnected ? 'WS LIVE' : 'WS RETRYING' }}
        </span>
        <span :class="loggedOnSessionCount > 0 ? 'text-brand-500' : 'text-yellow-400'" class="text-xs font-mono flex items-center gap-1">
          <span :class="loggedOnSessionCount > 0 ? 'bg-brand-500' : 'bg-yellow-400'" class="w-2 h-2 rounded-full inline-block"></span>
          {{ loggedOnSessionCount > 0 ? 'FIX CONNECTED' : 'FIX IDLE' }}
        </span>
        <span class="text-xs text-slate-400 font-mono">FIX Sessions: {{ loggedOnSessionCount }}/{{ sessions.length }}</span>
      </div>
    </header>

    <main v-if="activePage === 'dashboard'" class="p-6 grid grid-cols-12 gap-6">
      <div class="col-span-12 bg-dark-800 rounded-xl border border-dark-600 px-5 py-4 flex flex-col md:flex-row md:items-center md:justify-between gap-4">
        <div>
          <h2 class="font-semibold text-white text-sm">Metrics Dashboard</h2>
          <p class="text-xs text-slate-400 mt-1">Control how often throughput, latency, fill rate, reject rate, and the percentile chart refresh on screen.</p>
        </div>
        <div class="flex flex-col sm:flex-row sm:items-center gap-3 sm:gap-4">
          <div class="flex items-center gap-3">
            <label class="text-xs text-slate-400 whitespace-nowrap">Refresh every</label>
            <select v-model.number="selectedRefreshSeconds"
                    @change="updateMetricsRefreshInterval"
                    class="bg-dark-700 border border-dark-600 rounded-lg px-3 py-2 text-sm text-white focus:outline-none focus:border-brand-500">
              <option v-for="seconds in metricsRefreshOptions" :key="seconds" :value="seconds">
                {{ seconds }} second{{ seconds === 1 ? '' : 's' }}
              </option>
            </select>
            <span class="text-xs text-brand-500 font-mono whitespace-nowrap">applied every {{ selectedRefreshSeconds }}s</span>
          </div>
          <button @click="resetMetrics"
                  :disabled="isResettingMetrics"
                  class="px-3 py-2 rounded-lg text-xs font-semibold transition-colors border border-dark-500"
                  :class="isResettingMetrics ? 'bg-dark-700 text-slate-500 cursor-not-allowed' : 'bg-dark-700 hover:bg-dark-600 text-white'">
            {{ isResettingMetrics ? 'Resetting…' : 'Reset Metrics' }}
          </button>
        </div>
      </div>

      <div class="col-span-12 grid grid-cols-2 md:grid-cols-4 gap-4">
        <div class="bg-dark-800 rounded-xl p-4 border border-dark-600">
          <p class="text-xs text-slate-400 uppercase tracking-wider mb-1">Throughput</p>
          <p class="text-2xl font-bold font-mono text-brand-500">{{ metrics.throughputPerSec.toLocaleString() }}</p>
          <p class="text-xs text-slate-500">orders/sec</p>
        </div>
        <div class="bg-dark-800 rounded-xl p-4 border border-dark-600">
          <p class="text-xs text-slate-400 uppercase tracking-wider mb-1">p50 Latency</p>
          <p class="text-2xl font-bold font-mono text-brand-500">{{ metrics.p50Us.toLocaleString() }}</p>
          <p class="text-xs text-slate-500">µs · rolling window</p>
        </div>
        <div class="bg-dark-800 rounded-xl p-4 border border-dark-600">
          <p class="text-xs text-slate-400 uppercase tracking-wider mb-1">p75 Latency</p>
          <p class="text-2xl font-bold font-mono text-emerald-400">{{ metrics.p75Us.toLocaleString() }}</p>
          <p class="text-xs text-slate-500">µs · rolling window</p>
        </div>
        <div class="bg-dark-800 rounded-xl p-4 border border-dark-600">
          <p class="text-xs text-slate-400 uppercase tracking-wider mb-1">p90 Latency</p>
          <p class="text-2xl font-bold font-mono" :class="metrics.p90Us < 100 ? 'text-yellow-400' : 'text-red-400'">{{ metrics.p90Us.toLocaleString() }}</p>
          <p class="text-xs text-slate-500">µs · rolling window</p>
        </div>
      </div>

      <div class="col-span-12 bg-dark-800 rounded-xl border border-dark-600 px-5 py-4">
        <p class="text-xs font-semibold text-slate-300 uppercase tracking-wider mb-3">Order Counts — Simulator Totals</p>
        <div class="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-5 gap-3">
          <div class="bg-dark-700 rounded-lg px-4 py-3 border border-dark-600">
            <p class="text-xs text-slate-400 mb-1">Received</p>
            <p class="text-xl font-bold font-mono text-white">{{ metrics.orders.toLocaleString() }}</p>
          </div>
          <div class="bg-dark-700 rounded-lg px-4 py-3 border border-dark-600">
            <p class="text-xs text-slate-400 mb-1">Acked (Exec Reports)</p>
            <p class="text-xl font-bold font-mono text-brand-400">{{ metrics.execReports.toLocaleString() }}</p>
          </div>
          <div class="bg-dark-700 rounded-lg px-4 py-3 border border-dark-600">
            <p class="text-xs text-slate-400 mb-1">Filled</p>
            <p class="text-xl font-bold font-mono text-brand-500">{{ metrics.fills.toLocaleString() }}</p>
          </div>
          <div class="bg-dark-700 rounded-lg px-4 py-3 border border-dark-600">
            <p class="text-xs text-slate-400 mb-1">Rejected</p>
            <p class="text-xl font-bold font-mono text-red-400">{{ metrics.rejects.toLocaleString() }}</p>
          </div>
          <div class="bg-dark-700 rounded-lg px-4 py-3 border border-dark-600">
            <p class="text-xs text-slate-400 mb-1">Cancelled</p>
            <p class="text-xl font-bold font-mono text-yellow-400">{{ metrics.cancels.toLocaleString() }}</p>
          </div>
        </div>
      </div>

      <div class="col-span-12 lg:col-span-4 flex flex-col gap-6">
        <div class="bg-dark-800 rounded-xl border border-dark-600 overflow-hidden">
          <div class="px-5 py-3 border-b border-dark-600 flex items-center justify-between">
            <h2 class="font-semibold text-white text-sm">Fill Behavior</h2>
            <span class="text-xs text-brand-500 font-mono bg-brand-900 px-2 py-0.5 rounded">{{ activeProfile }}</span>
          </div>
          <div class="p-5 flex flex-col gap-4">
            <div>
              <label class="text-xs text-slate-400 mb-1 block">Saved Profiles</label>
              <div class="flex gap-2">
                <select v-model="selectedProfile" class="flex-1 bg-dark-700 border border-dark-600 rounded-lg px-3 py-2 text-sm text-white focus:outline-none focus:border-brand-500">
                  <option v-for="p in profiles" :key="p.name" :value="p.name">{{ p.name }}</option>
                </select>
                <button @click="activateProfile" class="px-3 py-2 bg-brand-600 hover:bg-brand-700 rounded-lg text-xs font-semibold transition-colors text-white">Activate</button>
              </div>
            </div>

            <div class="border-t border-dark-600 pt-4">
              <p class="text-xs font-semibold text-slate-300 mb-3">Create / Update Profile</p>
              <div class="grid grid-cols-2 gap-3">
                <div class="col-span-2">
                  <label class="text-xs text-slate-400 mb-1 block">Name</label>
                  <input v-model="form.name" class="w-full bg-dark-700 border border-dark-600 rounded-lg px-3 py-2 text-sm text-white focus:outline-none focus:border-brand-500" placeholder="my-profile"/>
                </div>
                <div class="col-span-2">
                  <label class="text-xs text-slate-400 mb-1 block">Behavior Type</label>
                  <select v-model="form.behaviorType" class="w-full bg-dark-700 border border-dark-600 rounded-lg px-3 py-2 text-sm text-white focus:outline-none focus:border-brand-500">
                    <option v-for="b in behaviorTypes" :key="b.value" :value="b.value">{{ b.label }}</option>
                  </select>
                </div>
                <div v-if="showFillPct">
                  <label class="text-xs text-slate-400 mb-1 block">Fill % (bps)</label>
                  <input v-model.number="form.fillPctBps" type="number" min="0" max="10000" class="w-full bg-dark-700 border border-dark-600 rounded-lg px-3 py-2 text-sm text-white focus:outline-none focus:border-brand-500"/>
                </div>
                <div v-if="showFillPct">
                  <label class="text-xs text-slate-400 mb-1 block">Partial Legs</label>
                  <input v-model.number="form.numPartialFills" type="number" min="1" max="10" class="w-full bg-dark-700 border border-dark-600 rounded-lg px-3 py-2 text-sm text-white focus:outline-none focus:border-brand-500"/>
                </div>
                <div v-if="showDelay" class="col-span-2">
                  <label class="text-xs text-slate-400 mb-1 block">Delay (ms)</label>
                  <input v-model.number="form.delayMs" type="number" min="0" class="w-full bg-dark-700 border border-dark-600 rounded-lg px-3 py-2 text-sm text-white focus:outline-none focus:border-brand-500"/>
                </div>
                <div v-if="showReject" class="col-span-2">
                  <label class="text-xs text-slate-400 mb-1 block">Reject Reason</label>
                  <select v-model="form.rejectReason" class="w-full bg-dark-700 border border-dark-600 rounded-lg px-3 py-2 text-sm text-white focus:outline-none focus:border-brand-500">
                    <option v-for="r in rejectReasons" :key="r" :value="r">{{ r }}</option>
                  </select>
                </div>
                <div v-if="showRandomFillQty">
                  <label class="text-xs text-slate-400 mb-1 block">Min Fill %</label>
                  <input v-model.number="form.randomMinQtyPct" type="number" min="0" max="100" class="w-full bg-dark-700 border border-dark-600 rounded-lg px-3 py-2 text-sm text-white focus:outline-none focus:border-brand-500"/>
                </div>
                <div v-if="showRandomFillQty">
                  <label class="text-xs text-slate-400 mb-1 block">Max Fill %</label>
                  <input v-model.number="form.randomMaxQtyPct" type="number" min="0" max="100" class="w-full bg-dark-700 border border-dark-600 rounded-lg px-3 py-2 text-sm text-white focus:outline-none focus:border-brand-500"/>
                </div>
                <div v-if="showRandomDelay">
                  <label class="text-xs text-slate-400 mb-1 block">Min Delay (ms)</label>
                  <input v-model.number="form.randomMinDelayMs" type="number" min="0" class="w-full bg-dark-700 border border-dark-600 rounded-lg px-3 py-2 text-sm text-white focus:outline-none focus:border-brand-500"/>
                </div>
                <div v-if="showRandomDelay">
                  <label class="text-xs text-slate-400 mb-1 block">Max Delay (ms)</label>
                  <input v-model.number="form.randomMaxDelayMs" type="number" min="0" class="w-full bg-dark-700 border border-dark-600 rounded-lg px-3 py-2 text-sm text-white focus:outline-none focus:border-brand-500"/>
                </div>
                <div v-if="showPriceImprovement" class="col-span-2">
                  <label class="text-xs text-slate-400 mb-1 block">Price Improvement (bps)</label>
                  <input v-model.number="form.priceImprovementBps" type="number" min="0" class="w-full bg-dark-700 border border-dark-600 rounded-lg px-3 py-2 text-sm text-white focus:outline-none focus:border-brand-500"/>
                </div>
              </div>
              <button @click="saveAndActivate" class="mt-4 w-full py-2 bg-brand-600 hover:bg-brand-700 rounded-lg text-sm font-semibold transition-colors text-white">
                Save &amp; Activate
              </button>
            </div>
          </div>
        </div>

        <div class="bg-dark-800 rounded-xl border border-dark-600 overflow-hidden">
          <div class="px-5 py-3 border-b border-dark-600 flex items-center justify-between">
            <h2 class="font-semibold text-white text-sm">FIX Sessions</h2>
            <button @click="openPage('settings')" class="text-xs text-slate-300 hover:text-white border border-dark-600 hover:border-brand-500 rounded-full px-3 py-1 transition-colors">
              Manage in Settings
            </button>
          </div>
          <div class="p-2">
            <div v-if="sessions.length === 0" class="px-3 py-4 text-xs text-slate-500 text-center">No active sessions</div>
            <div v-for="s in sessions" :key="s.sessionKey" class="flex items-center justify-between px-3 py-2 rounded-lg hover:bg-dark-700 transition-colors gap-4">
              <div>
                <p class="text-xs font-mono text-white">{{ s.senderCompId }} → {{ s.targetCompId }}</p>
                <p class="text-xs text-slate-400">{{ s.beginString }} | {{ s.loggedOn ? 'LOGGED ON' : 'OFFLINE' }}</p>
              </div>
              <button @click="disconnectSession(s.sessionKey)"
                      :disabled="disconnectingSessionId === s.sessionKey"
                      class="text-xs text-red-400 hover:text-red-300 px-2 py-1 rounded hover:bg-dark-600 transition-colors">
                {{ disconnectingSessionId === s.sessionKey ? 'Disconnecting…' : 'Disconnect' }}
              </button>
            </div>
          </div>
        </div>
      </div>

      <div class="col-span-12 lg:col-span-8 flex flex-col gap-6">
        <div class="bg-dark-800 rounded-xl border border-dark-600 p-5">
          <div class="flex items-center justify-between mb-4">
            <div>
              <h2 class="font-semibold text-white text-sm">Latency Percentiles — History</h2>
              <p class="text-xs text-slate-500 mt-1">p50 · p75 · p90 · rolling window · last 60 refreshes</p>
            </div>
            <span class="text-xs text-slate-400 font-mono">{{ selectedRefreshSeconds }}s cadence</span>
          </div>
          <canvas ref="latencyChart" height="80"></canvas>
        </div>

        <div class="bg-dark-800 rounded-xl border border-dark-600 overflow-hidden">
          <div class="px-5 py-3 border-b border-dark-600 flex items-center justify-between">
            <h2 class="font-semibold text-white text-sm">Order Flow</h2>
            <span class="text-xs text-slate-400 font-mono">{{ orders.length }} recent</span>
          </div>
          <div class="overflow-x-auto">
            <table class="w-full text-xs">
              <thead>
                <tr class="text-slate-400 border-b border-dark-600">
                  <th class="text-left px-4 py-2 font-medium">Time</th>
                  <th class="text-left px-4 py-2 font-medium">Symbol</th>
                  <th class="text-left px-4 py-2 font-medium">Side</th>
                  <th class="text-right px-4 py-2 font-medium">Qty</th>
                  <th class="text-right px-4 py-2 font-medium">Price</th>
                  <th class="text-left px-4 py-2 font-medium">ExecType</th>
                  <th class="text-left px-4 py-2 font-medium">Status</th>
                  <th class="text-left px-4 py-2 font-medium">Behavior</th>
                  <th class="text-right px-4 py-2 font-medium">Latency µs</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="o in orders" :key="o.id" :class="[o.flash, 'border-b border-dark-700 hover:bg-dark-700 transition-colors font-mono']">
                  <td class="px-4 py-1.5 text-slate-400">{{ o.time }}</td>
                  <td class="px-4 py-1.5 text-white font-semibold">{{ o.symbol }}</td>
                  <td class="px-4 py-1.5" :class="o.side === 'BUY' ? 'text-brand-500' : 'text-red-400'">{{ o.side }}</td>
                  <td class="px-4 py-1.5 text-right text-white">{{ o.qty }}</td>
                  <td class="px-4 py-1.5 text-right text-slate-300">{{ o.price }}</td>
                  <td class="px-4 py-1.5">
                    <span :class="execTypeBadge(o.execType)" class="px-1.5 py-0.5 rounded text-xs">{{ o.execType }}</span>
                  </td>
                  <td class="px-4 py-1.5 text-slate-300">{{ o.status }}</td>
                  <td class="px-4 py-1.5 text-slate-400">{{ o.behavior }}</td>
                  <td class="px-4 py-1.5 text-right" :class="o.latencyUs < 100 ? 'text-brand-500' : o.latencyUs < 500 ? 'text-yellow-400' : 'text-red-400'">{{ o.latencyUs }}</td>
                </tr>
                <tr v-if="orders.length === 0">
                  <td colspan="9" class="px-4 py-8 text-center text-slate-500">Waiting for orders…</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </main>

    <main v-else class="p-6">
      <div class="grid grid-cols-1 xl:grid-cols-2 gap-6">
        <section class="bg-dark-800 rounded-xl border border-dark-600 overflow-hidden">
          <div class="px-5 py-3 border-b border-dark-600">
            <h2 class="font-semibold text-white text-sm">Appearance</h2>
          </div>
          <div class="p-5 space-y-4">
            <div>
              <p class="text-xs text-slate-400 mb-3">Choose how the simulator UI should render on this browser.</p>
              <div class="grid grid-cols-1 sm:grid-cols-3 gap-3">
                <button
                  v-for="choice in themeChoices"
                  :key="choice.value"
                  @click="selectTheme(choice.value)"
                  class="rounded-lg border px-4 py-3 text-left transition-colors"
                  :class="themePreference === choice.value
                    ? 'bg-brand-600 text-white border-brand-500'
                    : 'bg-dark-700 border-dark-600 text-slate-300 hover:bg-dark-600 hover:text-white'">
                  <div class="text-sm font-semibold">{{ choice.label }}</div>
                  <div class="text-xs mt-1" :class="themePreference === choice.value ? 'text-white/80' : 'text-slate-400'">
                    {{ choice.value === 'system' ? 'Follow operating system preference.' : 'Apply immediately to this simulator tab.' }}
                  </div>
                </button>
              </div>
            </div>
            <div class="bg-dark-700 rounded-lg border border-dark-600 px-4 py-3">
              <p class="text-xs text-slate-400">Current theme</p>
              <p class="text-sm font-semibold text-white mt-1">{{ currentThemeLabel }}</p>
              <p class="text-xs text-slate-500 mt-1">Effective mode: {{ effectiveThemeLabel }}</p>
            </div>
          </div>
        </section>

        <section class="bg-dark-800 rounded-xl border border-dark-600 overflow-hidden">
          <div class="px-5 py-3 border-b border-dark-600 flex items-center justify-between gap-4">
            <div>
              <h2 class="font-semibold text-white text-sm">FIX sequence reset</h2>
              <p class="text-xs text-slate-400 mt-1">Reset inbound and outbound FIX sequence numbers for a live simulator session.</p>
            </div>
            <button @click="fetchSessions" class="text-xs text-slate-300 hover:text-white border border-dark-600 hover:border-brand-500 rounded-full px-3 py-1.5 transition-colors">
              Refresh sessions
            </button>
          </div>
          <div class="p-5 space-y-4">
            <div v-if="settingsNotice.message" class="rounded-lg border px-4 py-3 text-sm"
                 :class="settingsNotice.type === 'success' ? 'bg-brand-900 text-brand-500 border-brand-600' : 'bg-red-950/40 text-red-300 border-red-800'">
              {{ settingsNotice.message }}
            </div>
            <div v-if="sessions.length === 0" class="rounded-lg border border-dark-600 bg-dark-700 px-4 py-6 text-center text-sm text-slate-400">
              No live FIX sessions are available right now.
            </div>
            <div v-for="s in sessions" :key="'settings-' + s.sessionKey" class="rounded-lg border border-dark-600 bg-dark-700 px-4 py-4">
              <div class="flex flex-col md:flex-row md:items-start md:justify-between gap-4">
                <div class="space-y-2 min-w-0">
                  <p class="text-sm font-semibold text-white break-all">{{ s.sessionKey }}</p>
                  <div class="grid grid-cols-1 sm:grid-cols-2 gap-3 text-xs">
                    <div>
                      <p class="text-slate-400">Connection</p>
                      <p class="text-slate-300 mt-1">{{ s.senderCompId }} → {{ s.targetCompId }}</p>
                    </div>
                    <div>
                      <p class="text-slate-400">Status</p>
                      <p class="mt-1" :class="s.loggedOn ? 'text-brand-500' : 'text-yellow-400'">{{ s.loggedOn ? 'LOGGED ON' : 'OFFLINE' }}</p>
                    </div>
                    <div>
                      <p class="text-slate-400">Next inbound</p>
                      <p class="text-slate-300 mt-1 font-mono">{{ s.lastReceivedMsgSeqNum }}</p>
                    </div>
                    <div>
                      <p class="text-slate-400">Next outbound</p>
                      <p class="text-slate-300 mt-1 font-mono">{{ s.lastSentMsgSeqNum }}</p>
                    </div>
                  </div>
                </div>
                <div class="flex items-center gap-2 flex-wrap md:justify-end">
                  <button @click="resetSessionSequenceNumbers(s.sessionKey)"
                          :disabled="resettingSessionId === s.sessionKey || !s.loggedOn"
                          class="px-3 py-2 rounded-lg text-xs font-semibold transition-colors border"
                          :class="resettingSessionId === s.sessionKey || !s.loggedOn
                            ? 'bg-dark-600 text-slate-500 border-dark-600 cursor-not-allowed'
                            : 'bg-brand-600 hover:bg-brand-700 text-white border-brand-500'">
                    {{ resettingSessionId === s.sessionKey ? 'Resetting…' : 'Reset Sequence Numbers' }}
                  </button>
                  <button @click="disconnectSession(s.sessionKey)"
                          :disabled="disconnectingSessionId === s.sessionKey"
                          class="px-3 py-2 rounded-lg text-xs font-semibold transition-colors border"
                          :class="disconnectingSessionId === s.sessionKey
                            ? 'bg-dark-600 text-slate-500 border-dark-600 cursor-not-allowed'
                            : 'bg-dark-600 hover:bg-dark-500 text-red-400 border-dark-600'">
                    {{ disconnectingSessionId === s.sessionKey ? 'Disconnecting…' : 'Disconnect' }}
                  </button>
                </div>
              </div>
            </div>
          </div>
        </section>
      </div>
    </main>
  </div>
  `,

  setup() {
    const wsConnected = ref(false)
    const activeProfile = ref('–')
    const profiles = ref([])
    const selectedProfile = ref('')
    const sessions = ref([])
    const orders = ref([])
    const activePage = ref(pageCodeFromPath(window.location.pathname) || 'dashboard')
    const themePreference = ref(window.localStorage.getItem(THEME_STORAGE_KEY) || 'system')
    const themeChoices = THEME_CHOICES
    const pageOptions = PAGE_OPTIONS
    const settingsNotice = reactive({ type: 'success', message: '' })

    const metrics = reactive({
      throughputPerSec: 0, p50Us: 0, p75Us: 0, p90Us: 0,
      fills: 0, rejects: 0, cancels: 0, execReports: 0, orders: 0
    })

    const selectedRefreshSeconds = ref(1)
    const metricsRefreshOptions = METRICS_REFRESH_OPTIONS
    const isResettingMetrics = ref(false)
    const disconnectingSessionId = ref('')
    const resettingSessionId = ref('')

    const latencyChart = ref(null)
    let chart = null
    const latencyHistory = { labels: [], p50: [], p75: [], p90: [] }
    let metricsRefreshTimer = null
    let pollTimer = null
    let latestMetricsSnapshot = null
    let metricsFetchInFlight = false
    let ws
    let reconnectTimer

    const form = reactive({
      name: '', description: '', behaviorType: 'IMMEDIATE_FULL_FILL',
      fillPctBps: 10000, numPartialFills: 1, delayMs: 0,
      rejectReason: 'SIMULATOR_REJECT', randomMinQtyPct: 50, randomMaxQtyPct: 100,
      randomMinDelayMs: 0, randomMaxDelayMs: 5, priceImprovementBps: 1
    })

    const behaviorTypes = BEHAVIOR_TYPES
    const rejectReasons = REJECT_REASONS
    const loggedOnSessionCount = computed(() => sessions.value.filter(s => s.loggedOn).length)
    const showFillPct = computed(() => ['PARTIAL_FILL', 'PARTIAL_THEN_CANCEL'].includes(form.behaviorType))
    const showDelay = computed(() => form.behaviorType === 'DELAYED_FILL')
    const showReject = computed(() => ['REJECT', 'RANDOM_REJECT_CANCEL'].includes(form.behaviorType))
    const showRandomFillQty = computed(() => form.behaviorType === 'RANDOM_FILL')
    const showRandomDelay = computed(() => ['RANDOM_FILL', 'RANDOM_REJECT_CANCEL'].includes(form.behaviorType))
    const showPriceImprovement = computed(() => form.behaviorType === 'PRICE_IMPROVEMENT')
    const themeMediaQuery = window.matchMedia ? window.matchMedia('(prefers-color-scheme: dark)') : null
    const normalizedThemePreference = computed(() => THEME_CHOICES.some(choice => choice.value === themePreference.value) ? themePreference.value : 'system')
    const effectiveTheme = computed(() => {
      if (normalizedThemePreference.value === 'system') {
        return themeMediaQuery?.matches ? 'dark' : 'light'
      }
      return normalizedThemePreference.value
    })
    const currentThemeLabel = computed(() => themeChoices.find(choice => choice.value === normalizedThemePreference.value)?.label || 'System')
    const effectiveThemeLabel = computed(() => effectiveTheme.value === 'dark' ? 'Dark' : 'Light')

    function setSettingsNotice(type, message) {
      settingsNotice.type = type
      settingsNotice.message = message
    }

    function clearSettingsNotice() {
      settingsNotice.message = ''
    }

    function connectWs() {
      const wsScheme = location.protocol === 'https:' ? 'wss' : 'ws'
      ws = new WebSocket(`${wsScheme}://${location.host}/ws`)
      ws.onopen = () => {
        wsConnected.value = true
        clearTimeout(reconnectTimer)
      }
      ws.onclose = () => {
        wsConnected.value = false
        reconnectTimer = setTimeout(connectWs, 2000)
      }
      ws.onerror = () => ws.close()
      ws.onmessage = ({ data }) => {
        const msg = JSON.parse(data)
        if (msg.type === 'metrics') handleMetrics(msg)
        else if (msg.type === 'order') handleOrder(msg)
      }
    }

    function handleMetrics(m) {
      latestMetricsSnapshot = m
    }

    function cssVar(name, fallback) {
      const value = getComputedStyle(document.documentElement).getPropertyValue(name).trim()
      return value || fallback
    }

    function syncChartTheme() {
      if (!chart) {
        return
      }
      chart.data.datasets[0].borderColor = cssVar('--chart-p90', '#f59e0b')
      chart.data.datasets[0].backgroundColor = cssVar('--chart-p90-fill', '#f59e0b18')
      chart.data.datasets[1].borderColor = cssVar('--chart-p75', '#10b981')
      chart.data.datasets[1].backgroundColor = cssVar('--chart-p75-fill', '#10b98118')
      chart.data.datasets[2].borderColor = cssVar('--chart-p50', '#22c55e')
      chart.data.datasets[2].backgroundColor = cssVar('--chart-p50-fill', '#22c55e18')
      chart.options.plugins.legend.labels.color = cssVar('--chart-text', '#94a3b8')
      chart.options.scales.y.grid.color = cssVar('--chart-grid', '#1e293b')
      chart.options.scales.y.ticks.color = cssVar('--chart-text', '#94a3b8')
      chart.options.scales.y.title.color = cssVar('--chart-subtle', '#64748b')
      chart.update('none')
    }

    function applyMetricsSnapshot(m) {
      if (!m) return

      metrics.throughputPerSec = m.throughputPerSec
      metrics.p50Us = m.p50Us ?? 0
      metrics.p75Us = m.p75Us ?? 0
      metrics.p90Us = m.p90Us ?? 0
      metrics.fills = m.fills ?? 0
      metrics.rejects = m.rejects ?? 0
      metrics.cancels = m.cancels ?? 0
      metrics.execReports = m.execReports ?? 0
      metrics.orders = m.ordersReceived ?? 0

      const now = new Date().toLocaleTimeString()
      if (latencyHistory.labels.length >= 60) {
        latencyHistory.labels.shift()
        latencyHistory.p50.shift()
        latencyHistory.p75.shift()
        latencyHistory.p90.shift()
      }
      latencyHistory.labels.push(now)
      latencyHistory.p50.push(m.p50Us ?? 0)
      latencyHistory.p75.push(m.p75Us ?? 0)
      latencyHistory.p90.push(m.p90Us ?? 0)
      if (chart) {
        chart.data.labels = latencyHistory.labels
        chart.data.datasets[0].data = latencyHistory.p90
        chart.data.datasets[1].data = latencyHistory.p75
        chart.data.datasets[2].data = latencyHistory.p50
        chart.update('none')
      }
    }

    function toMetricsSnapshot(source) {
      if (!source) return null
      return {
        throughputPerSec: source.throughputPerSec ?? 0,
        p50Us: source.p50LatencyUs ?? source.p50Us ?? 0,
        p75Us: source.p75LatencyUs ?? source.p75Us ?? 0,
        p90Us: source.p90LatencyUs ?? source.p90Us ?? 0,
        fills: source.fillsSent ?? source.fills ?? 0,
        rejects: source.rejectsSent ?? source.rejects ?? 0,
        cancels: source.cancelsSent ?? source.cancels ?? 0,
        execReports: source.execReportsSent ?? source.execReports ?? 0,
        ordersReceived: source.ordersReceived ?? source.orders ?? 0
      }
    }

    function clearLatencyChart() {
      latencyHistory.labels.length = 0
      latencyHistory.p50.length = 0
      latencyHistory.p75.length = 0
      latencyHistory.p90.length = 0
      if (chart) {
        chart.data.labels = latencyHistory.labels
        chart.data.datasets[0].data = latencyHistory.p90
        chart.data.datasets[1].data = latencyHistory.p75
        chart.data.datasets[2].data = latencyHistory.p50
        chart.update('none')
      }
    }

    function flushLatestMetrics(force = false) {
      if (!latestMetricsSnapshot) return
      if (!force && !wsConnected.value) return
      applyMetricsSnapshot(latestMetricsSnapshot)
    }

    function updateMetricsRefreshInterval() {
      if (metricsRefreshTimer) clearInterval(metricsRefreshTimer)
      metricsRefreshTimer = setInterval(refreshMetricsDashboard, selectedRefreshSeconds.value * 1000)
      refreshMetricsDashboard()
    }

    async function refreshMetricsDashboard() {
      await fetchStats({ silent: true })
    }

    function handleOrder(o) {
      const entry = {
        id: o.correlationId,
        time: new Date().toLocaleTimeString('en', { hour12: false, hour: '2-digit', minute: '2-digit', second: '2-digit', fractionalSecondDigits: 3 }),
        symbol: o.symbol,
        side: o.side,
        qty: o.orderQty,
        price: o.price,
        execType: o.execType,
        status: o.ordStatus,
        behavior: o.fillBehavior,
        latencyUs: Math.round(o.latencyNs / 1000),
        flash: o.execType === 'REJECTED' ? 'flash-reject' : 'flash-fill'
      }
      orders.value.unshift(entry)
      if (orders.value.length > 100) orders.value.pop()
      setTimeout(() => { entry.flash = '' }, 700)
    }

    async function fetchProfiles() {
      const r = await fetch('/api/fill-profiles')
      profiles.value = await r.json()
      if (profiles.value.length > 0 && !selectedProfile.value) {
        selectedProfile.value = profiles.value[0].name
      }
    }

    async function fetchSessions() {
      const r = await fetch('/api/sessions')
      sessions.value = await r.json()
    }

    async function fetchStats({ silent = false } = {}) {
      if (metricsFetchInFlight) return
      metricsFetchInFlight = true
      try {
        const r = await fetch('/api/statistics')
        if (!r.ok) throw new Error(`Statistics fetch failed with status ${r.status}`)
        const s = await r.json()
        activeProfile.value = s.activeProfile || '–'
        latestMetricsSnapshot = toMetricsSnapshot(s)
        flushLatestMetrics(true)
      } catch (e) {
        if (!silent) {
          console.error('Failed to fetch statistics', e)
        }
      } finally {
        metricsFetchInFlight = false
      }
    }

    async function resetMetrics() {
      if (isResettingMetrics.value) return
      isResettingMetrics.value = true
      try {
        const r = await fetch('/api/statistics/reset', { method: 'POST' })
        if (!r.ok) throw new Error(`Reset failed with status ${r.status}`)
        const s = await r.json()
        activeProfile.value = s.activeProfile || activeProfile.value
        latestMetricsSnapshot = toMetricsSnapshot(s)
        clearLatencyChart()
        flushLatestMetrics(true)
      } catch (e) {
        console.error('Failed to reset metrics', e)
        window.alert('Failed to reset metrics. Please try again.')
      } finally {
        isResettingMetrics.value = false
      }
    }

    async function activateProfile() {
      if (!selectedProfile.value) return
      await fetch(`/api/fill-profiles/${encodeURIComponent(selectedProfile.value)}/activate`, { method: 'PUT' })
      activeProfile.value = selectedProfile.value
    }

    async function saveAndActivate() {
      if (!form.name) {
        window.alert('Profile name is required')
        return
      }
      await fetch('/api/fill-profiles', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ ...form })
      })
      await fetchProfiles()
      selectedProfile.value = form.name
      await activateProfile()
    }

    async function disconnectSession(sessionKey) {
      if (disconnectingSessionId.value) return
      disconnectingSessionId.value = sessionKey
      try {
        const response = await fetch(`/api/sessions/${encodeURIComponent(sessionKey)}`, { method: 'DELETE' })
        if (!response.ok) {
          throw new Error(`Disconnect failed with status ${response.status}`)
        }
        setSettingsNotice('success', 'Session disconnect requested successfully.')
        setTimeout(async () => {
          try {
            await fetchSessions()
          } finally {
            disconnectingSessionId.value = ''
          }
        }, 1500)
      } catch (error) {
        disconnectingSessionId.value = ''
        console.error('Failed to disconnect session', error)
        window.alert('Failed to disconnect session. Please try again.')
      }
    }

    async function resetSessionSequenceNumbers(sessionKey) {
      if (resettingSessionId.value) return
      resettingSessionId.value = sessionKey
      clearSettingsNotice()
      try {
        const response = await fetch(`/api/sessions/${encodeURIComponent(sessionKey)}/reset-sequence`, { method: 'POST' })
        const payload = await response.json()
        if (!response.ok || !payload.reset) {
          throw new Error(`Sequence reset failed with status ${response.status}`)
        }
        setSettingsNotice('success', 'FIX sequence numbers reset to 1 for the selected simulator session.')
        await fetchSessions()
      } catch (error) {
        console.error('Failed to reset simulator session sequence numbers', error)
        setSettingsNotice('error', 'Unable to reset FIX sequence numbers for the selected simulator session.')
        window.alert('Failed to reset FIX sequence numbers. Please try again.')
      } finally {
        resettingSessionId.value = ''
      }
    }

    function execTypeBadge(t) {
      if (t === 'FILL' || t === 'TRADE') return 'bg-brand-900 text-brand-400'
      if (t === 'PARTIAL_FILL') return 'bg-yellow-900 text-yellow-400'
      if (t === 'REJECTED' || t === 'CANCELED') return 'bg-red-900 text-red-400'
      return 'bg-dark-600 text-slate-400'
    }

    function applyTheme(nextTheme) {
      const normalized = THEME_CHOICES.some(choice => choice.value === nextTheme) ? nextTheme : 'system'
      themePreference.value = normalized
      window.localStorage.setItem(THEME_STORAGE_KEY, normalized)
      document.documentElement.dataset.theme = effectiveTheme.value
      syncChartTheme()
    }

    function handleSystemThemeChange() {
      if (normalizedThemePreference.value === 'system') {
        document.documentElement.dataset.theme = effectiveTheme.value
        syncChartTheme()
      }
    }

    function syncPageFromLocation(replaceAlias = false) {
      const currentPath = normalizePagePath(window.location.pathname)
      const resolvedPage = pageCodeFromPath(currentPath)
      if (!resolvedPage) {
        activePage.value = 'dashboard'
        window.history.replaceState({ page: 'dashboard' }, '', pagePathForCode('dashboard'))
        return
      }
      activePage.value = resolvedPage
      if (replaceAlias && (currentPath === '/' || currentPath === '/index.html')) {
        window.history.replaceState({ page: resolvedPage }, '', pagePathForCode(resolvedPage))
      }
    }

    function navigateToPage(pageCode, replace = false) {
      const nextPath = pagePathForCode(pageCode)
      activePage.value = pageCode
      if (normalizePagePath(window.location.pathname) !== nextPath) {
        const method = replace ? 'replaceState' : 'pushState'
        window.history[method]({ page: pageCode }, '', nextPath)
      }
    }

    function openPage(pageCode) {
      navigateToPage(pageCode)
      clearSettingsNotice()
    }

    function selectTheme(nextTheme) {
      applyTheme(nextTheme)
    }

    function handlePopState() {
      syncPageFromLocation(false)
    }

    watch(themePreference, applyTheme, { immediate: true })

    onMounted(async () => {
      if (themeMediaQuery?.addEventListener) {
        themeMediaQuery.addEventListener('change', handleSystemThemeChange)
      } else if (themeMediaQuery?.addListener) {
        themeMediaQuery.addListener(handleSystemThemeChange)
      }
      window.addEventListener('popstate', handlePopState)
      syncPageFromLocation(true)
      await fetchProfiles()
      await fetchSessions()
      await fetchStats()
      pollTimer = setInterval(fetchSessions, 5000)

      await nextTick()
      if (latencyChart.value) {
        chart = new Chart(latencyChart.value, {
          type: 'line',
          data: {
            labels: latencyHistory.labels,
            datasets: [
              {
                label: 'p90',
                data: latencyHistory.p90,
                borderColor: cssVar('--chart-p90', '#f59e0b'),
                backgroundColor: cssVar('--chart-p90-fill', '#f59e0b18'),
                borderWidth: 2,
                pointRadius: 0,
                fill: false,
                tension: 0.3
              },
              {
                label: 'p75',
                data: latencyHistory.p75,
                borderColor: cssVar('--chart-p75', '#10b981'),
                backgroundColor: cssVar('--chart-p75-fill', '#10b98118'),
                borderWidth: 1.5,
                pointRadius: 0,
                fill: false,
                tension: 0.3
              },
              {
                label: 'p50',
                data: latencyHistory.p50,
                borderColor: cssVar('--chart-p50', '#22c55e'),
                backgroundColor: cssVar('--chart-p50-fill', '#22c55e18'),
                borderWidth: 1.5,
                pointRadius: 0,
                fill: false,
                tension: 0.3
              }
            ]
          },
          options: {
            responsive: true,
            maintainAspectRatio: true,
            animation: false,
            plugins: {
              legend: {
                display: true,
                labels: {
                  color: cssVar('--chart-text', '#94a3b8'),
                  font: { size: 11 },
                  boxWidth: 14,
                  padding: 12
                }
              }
            },
            scales: {
              x: { display: false },
              y: {
                grid: { color: cssVar('--chart-grid', '#1e293b') },
                ticks: { color: cssVar('--chart-text', '#94a3b8'), font: { size: 10 } },
                title: { display: true, text: 'µs', color: cssVar('--chart-subtle', '#64748b'), font: { size: 10 } }
              }
            }
          }
        })
      }

      connectWs()
      updateMetricsRefreshInterval()
    })

    onUnmounted(() => {
      if (pollTimer) clearInterval(pollTimer)
      if (metricsRefreshTimer) clearInterval(metricsRefreshTimer)
      if (reconnectTimer) clearTimeout(reconnectTimer)
      if (ws) ws.close()
      if (chart) chart.destroy()
      window.removeEventListener('popstate', handlePopState)
      if (themeMediaQuery?.removeEventListener) {
        themeMediaQuery.removeEventListener('change', handleSystemThemeChange)
      } else if (themeMediaQuery?.removeListener) {
        themeMediaQuery.removeListener(handleSystemThemeChange)
      }
    })

    return {
      wsConnected,
      activeProfile,
      profiles,
      selectedProfile,
      sessions,
      orders,
      metrics,
      form,
      behaviorTypes,
      rejectReasons,
      loggedOnSessionCount,
      showFillPct,
      showDelay,
      showReject,
      showRandomFillQty,
      showRandomDelay,
      showPriceImprovement,
      latencyChart,
      selectedRefreshSeconds,
      metricsRefreshOptions,
      isResettingMetrics,
      disconnectingSessionId,
      resettingSessionId,
      activePage,
      pageOptions,
      themePreference,
      themeChoices,
      currentThemeLabel,
      effectiveThemeLabel,
      settingsNotice,
      updateMetricsRefreshInterval,
      resetMetrics,
      activateProfile,
      saveAndActivate,
      disconnectSession,
      resetSessionSequenceNumbers,
      execTypeBadge,
      openPage,
      selectTheme,
      fetchSessions
    }
  }
}).mount('#app')
