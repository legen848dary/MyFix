import { createApp, computed, onMounted, onUnmounted, reactive, ref } from 'https://unpkg.com/vue@3/dist/vue.esm-browser.prod.js'

const EVENT_LEVEL_CLASSES = {
  SUCCESS: 'text-emerald-300 bg-emerald-500/10 border-emerald-500/20',
  WARN: 'text-amber-300 bg-amber-500/10 border-amber-500/20',
  INFO: 'text-cyan-200 bg-cyan-500/10 border-cyan-500/20'
}

createApp({
  template: `
    <div class="min-h-screen bg-dark-950">
      <header class="border-b border-white/10 bg-dark-900/90 backdrop-blur">
        <div class="mx-auto flex max-w-7xl flex-col gap-4 px-6 py-5 lg:flex-row lg:items-center lg:justify-between">
          <div class="flex items-start gap-4">
            <div class="flex h-12 w-12 items-center justify-center rounded-2xl bg-brand-600/20 text-lg font-semibold text-brand-100 shadow-glow">TF</div>
            <div>
              <p class="text-xs uppercase tracking-[0.3em] text-brand-300">HSBC Desk Experience</p>
              <h1 class="mt-1 text-2xl font-semibold text-white">{{ overview.applicationName || 'TheFixClient' }}</h1>
              <p class="mt-1 text-sm text-slate-400">{{ overview.subtitle || 'Trader workstation shell' }}</p>
            </div>
          </div>
          <div class="flex flex-col gap-3 sm:flex-row sm:flex-wrap sm:items-center sm:justify-end">
            <div class="rounded-full border px-3 py-1.5 text-xs font-medium"
                 :class="session.connected ? 'border-emerald-500/30 bg-emerald-500/10 text-emerald-200' : 'border-amber-500/30 bg-amber-500/10 text-amber-200'">
              {{ session.connected ? 'FIX session live' : 'Standby mode' }}
            </div>
            <div class="rounded-full border px-3 py-1.5 text-xs font-medium"
                 :class="session.autoFlowActive ? 'border-cyan-500/30 bg-cyan-500/10 text-cyan-100' : 'border-white/10 bg-white/5 text-slate-300'">
              {{ session.autoFlowActive ? ('Demo flow ' + session.autoFlowRate + '/s') : 'Demo flow idle' }}
            </div>
            <div class="rounded-full border border-cyan-500/20 bg-cyan-500/10 px-3 py-1.5 text-xs font-medium text-cyan-100">
              {{ overview.environment }}
            </div>
            <div class="text-right text-xs text-slate-400">
              <div>API <span class="font-mono text-slate-200">{{ apiStatus }}</span></div>
              <div class="mt-1 font-mono">{{ session.beginString }} · {{ session.senderCompId }} → {{ session.targetCompId }}</div>
            </div>
          </div>
        </div>
      </header>

      <main class="mx-auto grid max-w-7xl grid-cols-12 gap-6 px-6 py-6">
        <section class="col-span-12 grid grid-cols-2 gap-4 xl:grid-cols-4">
          <article class="rounded-2xl border border-white/10 bg-dark-900 p-4">
            <p class="text-xs uppercase tracking-[0.2em] text-slate-500">Ready state</p>
            <p class="mt-3 text-2xl font-semibold text-white">{{ kpis.readyState }}</p>
            <p class="mt-2 text-xs text-slate-400">Current workstation mode for the live FIX checkpoint.</p>
          </article>
          <article class="rounded-2xl border border-white/10 bg-dark-900 p-4">
            <p class="text-xs uppercase tracking-[0.2em] text-slate-500">Orders sent</p>
            <p class="mt-3 text-2xl font-semibold text-cyan-300">{{ kpis.sentOrders }}</p>
            <p class="mt-2 text-xs text-slate-400">Outbound NewOrderSingle messages accepted by QuickFIX/J.</p>
          </article>
          <article class="rounded-2xl border border-white/10 bg-dark-900 p-4">
            <p class="text-xs uppercase tracking-[0.2em] text-slate-500">Execution reports</p>
            <p class="mt-3 text-2xl font-semibold text-white">{{ kpis.executionReports }}</p>
            <p class="mt-2 text-xs text-slate-400">Simulator acknowledgements observed on the live session.</p>
          </article>
          <article class="rounded-2xl border border-white/10 bg-dark-900 p-4">
            <p class="text-xs uppercase tracking-[0.2em] text-slate-500">Rejects / failures</p>
            <p class="mt-3 text-2xl font-semibold text-white">{{ kpis.rejects }} / {{ kpis.sendFailures }}</p>
            <p class="mt-2 text-xs text-slate-400">Business rejects plus local send failures from the active route.</p>
          </article>
        </section>

        <section class="col-span-12 lg:col-span-4 space-y-6">
          <article class="rounded-2xl border border-white/10 bg-dark-900 p-5 shadow-glow">
            <div class="flex items-start justify-between gap-4">
              <div>
                <h2 class="text-base font-semibold text-white">Session command center</h2>
                <p class="mt-1 text-sm text-slate-400">QuickFIX/J initiator controls linked directly to the simulator acceptor.</p>
              </div>
              <span class="rounded-full px-3 py-1 text-xs font-semibold"
                    :class="session.connected ? 'bg-emerald-500/15 text-emerald-200' : 'bg-slate-700 text-slate-200'">
                {{ session.status }}
              </span>
            </div>
            <dl class="mt-5 grid grid-cols-2 gap-3 text-sm">
              <div class="rounded-xl border border-white/10 bg-white/5 p-3">
                <dt class="text-xs uppercase tracking-[0.18em] text-slate-500">Host</dt>
                <dd class="mt-2 font-mono text-slate-100">{{ session.host }}</dd>
              </div>
              <div class="rounded-xl border border-white/10 bg-white/5 p-3">
                <dt class="text-xs uppercase tracking-[0.18em] text-slate-500">Port</dt>
                <dd class="mt-2 font-mono text-slate-100">{{ session.port }}</dd>
              </div>
              <div class="rounded-xl border border-white/10 bg-white/5 p-3">
                <dt class="text-xs uppercase tracking-[0.18em] text-slate-500">Sender</dt>
                <dd class="mt-2 font-mono text-slate-100">{{ session.senderCompId }}</dd>
              </div>
              <div class="rounded-xl border border-white/10 bg-white/5 p-3">
                <dt class="text-xs uppercase tracking-[0.18em] text-slate-500">Target</dt>
                <dd class="mt-2 font-mono text-slate-100">{{ session.targetCompId }}</dd>
              </div>
              <div class="rounded-xl border border-white/10 bg-white/5 p-3">
                <dt class="text-xs uppercase tracking-[0.18em] text-slate-500">Uptime</dt>
                <dd class="mt-2 font-mono text-slate-100">{{ kpis.sessionUptime }}</dd>
              </div>
              <div class="rounded-xl border border-white/10 bg-white/5 p-3">
                <dt class="text-xs uppercase tracking-[0.18em] text-slate-500">Open orders</dt>
                <dd class="mt-2 font-mono text-slate-100">{{ kpis.openOrders }}</dd>
              </div>
            </dl>
            <div class="mt-5 flex flex-wrap gap-3">
              <button @click="connectSession"
                      class="rounded-xl bg-brand-600 px-4 py-2 text-sm font-semibold text-white transition hover:bg-brand-500">
                Prime session
              </button>
              <button @click="disconnectSession"
                      class="rounded-xl border border-white/10 bg-white/5 px-4 py-2 text-sm font-semibold text-slate-100 transition hover:bg-white/10">
                Return to standby
              </button>
              <button @click="pulseTest"
                      class="rounded-xl border border-cyan-400/20 bg-cyan-500/10 px-4 py-2 text-sm font-semibold text-cyan-100 transition hover:bg-cyan-500/20">
                Pulse test
              </button>
            </div>
          </article>

          <article class="rounded-2xl border border-white/10 bg-dark-900 p-5">
            <h2 class="text-base font-semibold text-white">Launch checklist</h2>
            <ul class="mt-4 space-y-3">
              <li v-for="item in launchChecklist" :key="item" class="flex gap-3 rounded-xl border border-white/10 bg-white/5 p-3 text-sm text-slate-300">
                <span class="mt-1 h-2.5 w-2.5 rounded-full bg-brand-400"></span>
                <span>{{ item }}</span>
              </li>
            </ul>
          </article>
        </section>

        <section class="col-span-12 lg:col-span-5 space-y-6">
          <article class="rounded-2xl border border-white/10 bg-dark-900 p-5">
            <div class="flex items-start justify-between gap-4">
              <div>
                <h2 class="text-base font-semibold text-white">Order ticket</h2>
                <p class="mt-1 text-sm text-slate-400">Preview, route, or repeat a live NewOrderSingle template through the simulator session.</p>
              </div>
              <span class="rounded-full px-3 py-1 text-xs font-semibold"
                    :class="preview.status === 'INVALID' ? 'bg-rose-500/15 text-rose-200' : 'bg-cyan-500/15 text-cyan-100'">
                {{ preview.status || 'Awaiting preview' }}
              </span>
            </div>
            <div class="mt-5 grid grid-cols-2 gap-4">
              <label class="col-span-2 text-sm text-slate-300">
                <span class="mb-2 block text-xs uppercase tracking-[0.18em] text-slate-500">Symbol</span>
                <input v-model="orderDraft.symbol" class="w-full rounded-xl border border-white/10 bg-white/5 px-4 py-3 text-white outline-none ring-0 transition focus:border-brand-500" />
              </label>
              <label class="text-sm text-slate-300">
                <span class="mb-2 block text-xs uppercase tracking-[0.18em] text-slate-500">Side</span>
                <select v-model="orderDraft.side" class="w-full rounded-xl border border-white/10 bg-white/5 px-4 py-3 text-white outline-none focus:border-brand-500">
                  <option>BUY</option>
                  <option>SELL</option>
                </select>
              </label>
              <label class="text-sm text-slate-300">
                <span class="mb-2 block text-xs uppercase tracking-[0.18em] text-slate-500">Time in force</span>
                <select v-model="orderDraft.timeInForce" class="w-full rounded-xl border border-white/10 bg-white/5 px-4 py-3 text-white outline-none focus:border-brand-500">
                  <option>DAY</option>
                  <option>IOC</option>
                  <option>FOK</option>
                </select>
              </label>
              <label class="text-sm text-slate-300">
                <span class="mb-2 block text-xs uppercase tracking-[0.18em] text-slate-500">Quantity</span>
                <input v-model.number="orderDraft.quantity" type="number" min="1" class="w-full rounded-xl border border-white/10 bg-white/5 px-4 py-3 text-white outline-none focus:border-brand-500" />
              </label>
              <label class="text-sm text-slate-300">
                <span class="mb-2 block text-xs uppercase tracking-[0.18em] text-slate-500">Limit price</span>
                <input v-model.number="orderDraft.price" type="number" min="0" step="0.01" class="w-full rounded-xl border border-white/10 bg-white/5 px-4 py-3 text-white outline-none focus:border-brand-500" />
              </label>
              <label class="col-span-2 text-sm text-slate-300">
                <span class="mb-2 block text-xs uppercase tracking-[0.18em] text-slate-500">Demo flow rate (msg/s)</span>
                <input v-model.number="flowDraft.ratePerSecond" type="number" min="1" class="w-full rounded-xl border border-white/10 bg-white/5 px-4 py-3 text-white outline-none focus:border-brand-500" />
              </label>
            </div>
            <div class="mt-5 flex flex-wrap gap-3">
              <button @click="previewTicket"
                      class="rounded-xl bg-white px-4 py-2 text-sm font-semibold text-dark-950 transition hover:bg-slate-200">
                Preview ticket
              </button>
              <button @click="sendTicket"
                      class="rounded-xl bg-brand-600 px-4 py-2 text-sm font-semibold text-white transition hover:bg-brand-500"
                      :disabled="busyAction === 'send'">
                Send live ticket
              </button>
              <button @click="startDemoFlow"
                      class="rounded-xl border border-cyan-400/20 bg-cyan-500/10 px-4 py-2 text-sm font-semibold text-cyan-100 transition hover:bg-cyan-500/20"
                      :disabled="busyAction === 'flow-start'">
                Start demo flow
              </button>
              <button @click="stopDemoFlow"
                      class="rounded-xl border border-white/10 bg-white/5 px-4 py-2 text-sm font-semibold text-slate-100 transition hover:bg-white/10"
                      :disabled="busyAction === 'flow-stop'">
                Stop demo flow
              </button>
            </div>

            <div class="mt-5 rounded-2xl border border-white/10 bg-gradient-to-br from-cyan-500/10 via-white/5 to-transparent p-4">
              <div class="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
                <div>
                  <p class="text-xs uppercase tracking-[0.2em] text-slate-400">Ticket summary</p>
                  <p class="mt-2 text-lg font-semibold text-white">{{ preview.summary || 'Generate a preview to review trader intent.' }}</p>
                  <p class="mt-2 text-sm text-slate-300">{{ preview.recommendation || 'The preview API is live and ready for FIX routing.' }}</p>
                </div>
                <div class="rounded-xl border border-white/10 bg-dark-950/60 px-4 py-3 text-right">
                  <p class="text-xs uppercase tracking-[0.2em] text-slate-500">Estimated notional</p>
                  <p class="mt-2 font-mono text-2xl text-white">{{ preview.notional || '$0.00' }}</p>
                </div>
              </div>
              <ul v-if="previewWarnings.length" class="mt-4 space-y-2 text-sm text-rose-200">
                <li v-for="warning in previewWarnings" :key="warning" class="rounded-xl border border-rose-500/20 bg-rose-500/10 px-3 py-2">
                  {{ warning }}
                </li>
              </ul>
            </div>
          </article>

          <article class="rounded-2xl border border-white/10 bg-dark-900 p-5">
            <div class="flex items-start justify-between gap-4">
              <div>
                <h2 class="text-base font-semibold text-white">Recent routed orders</h2>
                <p class="mt-1 text-sm text-slate-400">Latest manual and auto-flow orders observed by the workstation.</p>
              </div>
              <div class="text-xs text-slate-500">{{ recentOrders.length }} tracked</div>
            </div>
            <div class="mt-4 space-y-3">
              <div v-if="!recentOrders.length" class="rounded-xl border border-dashed border-white/10 bg-white/5 px-4 py-5 text-sm text-slate-400">
                No routed orders yet. Connect and send a ticket to populate this blotter.
              </div>
              <div v-for="order in recentOrders" :key="order.clOrdId" class="rounded-xl border border-white/10 bg-white/5 p-4">
                <div class="flex flex-col gap-3 xl:flex-row xl:items-start xl:justify-between">
                  <div>
                    <div class="flex flex-wrap items-center gap-2">
                      <p class="font-semibold text-white">{{ order.side }} {{ order.quantity }} {{ order.symbol }}</p>
                      <span class="rounded-full border border-white/10 px-2 py-0.5 text-[11px] uppercase tracking-[0.15em] text-slate-300">{{ order.source }}</span>
                    </div>
                    <p class="mt-1 text-xs font-mono text-slate-400">{{ order.clOrdId }} · {{ order.time }}</p>
                    <p class="mt-2 text-sm text-slate-300">{{ order.note }}</p>
                  </div>
                  <div class="grid grid-cols-2 gap-3 text-right text-xs text-slate-400">
                    <div>
                      <p>Status</p>
                      <p class="mt-1 font-semibold text-white">{{ order.status }}</p>
                    </div>
                    <div>
                      <p>Exec type</p>
                      <p class="mt-1 font-semibold text-white">{{ order.execType }}</p>
                    </div>
                    <div>
                      <p>Limit</p>
                      <p class="mt-1 font-mono text-white">{{ order.limitPrice }}</p>
                    </div>
                    <div>
                      <p>AvgPx</p>
                      <p class="mt-1 font-mono text-white">{{ order.avgPx }}</p>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </article>
        </section>

        <section class="col-span-12 lg:col-span-3 space-y-6">
          <article class="rounded-2xl border border-white/10 bg-dark-900 p-5">
            <h2 class="text-base font-semibold text-white">Desk watchlist</h2>
            <div class="mt-4 space-y-3">
              <div v-for="item in watchlist" :key="item.symbol" class="rounded-xl border border-white/10 bg-white/5 p-3">
                <div class="flex items-start justify-between gap-4">
                  <div>
                    <p class="font-semibold text-white">{{ item.symbol }}</p>
                    <p class="mt-1 text-xs uppercase tracking-[0.18em] text-slate-500">{{ item.venue }}</p>
                  </div>
                  <div class="text-right">
                    <p class="font-mono text-slate-100">{{ item.last }}</p>
                    <p class="mt-1 text-xs font-semibold" :class="item.trend === 'up' ? 'text-emerald-300' : 'text-rose-300'">{{ item.change }}</p>
                  </div>
                </div>
              </div>
            </div>
          </article>

          <article class="rounded-2xl border border-white/10 bg-dark-900 p-5">
            <h2 class="text-base font-semibold text-white">Session snapshot</h2>
            <dl class="mt-4 space-y-3 text-sm text-slate-300">
              <div class="rounded-xl border border-white/10 bg-white/5 p-3">
                <dt class="text-xs uppercase tracking-[0.18em] text-slate-500">Session mode</dt>
                <dd class="mt-1">{{ session.mode }}</dd>
              </div>
              <div class="rounded-xl border border-white/10 bg-white/5 p-3">
                <dt class="text-xs uppercase tracking-[0.18em] text-slate-500">Raw message logging</dt>
                <dd class="mt-1">{{ session.rawMessageLoggingEnabled ? 'Enabled' : 'Suppressed' }}</dd>
              </div>
              <div class="rounded-xl border border-white/10 bg-white/5 p-3">
                <dt class="text-xs uppercase tracking-[0.18em] text-slate-500">Pulse checks</dt>
                <dd class="mt-1">{{ kpis.pulseChecks }}</dd>
              </div>
            </dl>
          </article>
        </section>

        <section class="col-span-12">
          <article class="rounded-2xl border border-white/10 bg-dark-900 p-5">
            <div class="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
              <div>
                <h2 class="text-base font-semibold text-white">Operator event stream</h2>
                <p class="mt-1 text-sm text-slate-400">Live workstation activity including FIX logon, routing actions, and rejects.</p>
              </div>
              <div class="text-xs text-slate-500">Updated {{ generatedAtLabel }}</div>
            </div>
            <div class="mt-4 grid gap-3">
              <div v-for="event in recentEvents" :key="event.time + event.title + event.detail"
                   class="rounded-2xl border p-4"
                   :class="eventClasses(event.level)">
                <div class="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
                  <div>
                    <p class="font-semibold">{{ event.title }}</p>
                    <p class="mt-1 text-sm opacity-90">{{ event.detail }}</p>
                  </div>
                  <div class="text-xs font-mono opacity-80">{{ event.time }}</div>
                </div>
              </div>
            </div>
          </article>
        </section>
      </main>
    </div>
  `,

  setup() {
    const overview = reactive({
      applicationName: 'TheFixClient',
      subtitle: 'HSBC Trader Workstation',
      environment: 'Loading…'
    })
    const session = reactive({
      connected: false,
      status: 'Loading…',
      host: 'localhost',
      port: 9880,
      beginString: 'FIX.4.4',
      senderCompId: 'HSBC_TRDR01',
      targetCompId: 'LLEXSIM',
      mode: 'QuickFIX/J initiator',
      autoFlowActive: false,
      autoFlowRate: 0,
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
    const watchlist = ref([])
    const launchChecklist = ref([])
    const recentEvents = ref([])
    const recentOrders = ref([])
    const generatedAt = ref('')
    const apiStatus = ref('Connecting…')
    const busyAction = ref('')
    const refreshHandle = ref(null)

    const orderDraft = reactive({
      symbol: 'AAPL',
      side: 'BUY',
      quantity: 100,
      price: 100.25,
      timeInForce: 'DAY'
    })
    const flowDraft = reactive({
      ratePerSecond: 25
    })

    const preview = reactive({
      status: '',
      summary: '',
      notional: '',
      recommendation: '',
      warnings: []
    })

    const previewWarnings = computed(() => preview.warnings || [])
    const generatedAtLabel = computed(() => generatedAt.value ? new Date(generatedAt.value).toLocaleString() : '—')

    const applyOverview = (payload) => {
      Object.assign(overview, payload)
      Object.assign(session, payload.session || {})
      Object.assign(kpis, payload.kpis || {})
      watchlist.value = payload.watchlist || []
      launchChecklist.value = payload.launchChecklist || []
      recentEvents.value = payload.recentEvents || []
      recentOrders.value = payload.recentOrders || []
      generatedAt.value = payload.generatedAt || ''
      if (payload.defaults?.defaultFlowRate && !flowDraft.ratePerSecond) {
        flowDraft.ratePerSecond = payload.defaults.defaultFlowRate
      }
      apiStatus.value = 'Live'
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

    const loadOverview = async () => {
      try {
        const payload = await apiCall('/api/overview')
        applyOverview(payload)
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

    const runAction = async (name, work) => {
      busyAction.value = name
      try {
        await work()
      } finally {
        busyAction.value = ''
      }
    }

    const connectSession = async () => runAction('connect', async () => applyOverview(await apiCall('/api/session/connect', { method: 'POST' })))
    const disconnectSession = async () => runAction('disconnect', async () => applyOverview(await apiCall('/api/session/disconnect', { method: 'POST' })))
    const pulseTest = async () => runAction('pulse', async () => applyOverview(await apiCall('/api/session/pulse-test', { method: 'POST' })))

    const previewTicket = async () => {
      const payload = await apiCall('/api/order-ticket/preview', {
        method: 'POST',
        body: JSON.stringify(orderDraft)
      })
      Object.assign(preview, payload)
    }

    const sendTicket = async () => runAction('send', async () => {
      await previewTicket()
      applyOverview(await apiCall('/api/order-ticket/send', {
        method: 'POST',
        body: JSON.stringify(orderDraft)
      }))
    })

    const startDemoFlow = async () => runAction('flow-start', async () => {
      await previewTicket()
      applyOverview(await apiCall('/api/order-flow/start', {
        method: 'POST',
        body: JSON.stringify({ ...orderDraft, ratePerSecond: flowDraft.ratePerSecond })
      }))
    })

    const stopDemoFlow = async () => runAction('flow-stop', async () => applyOverview(await apiCall('/api/order-flow/stop', { method: 'POST' })))

    const eventClasses = (level) => EVENT_LEVEL_CLASSES[level] || EVENT_LEVEL_CLASSES.INFO

    onMounted(async () => {
      await loadOverview()
      if (!flowDraft.ratePerSecond && overview.defaults?.defaultFlowRate) {
        flowDraft.ratePerSecond = overview.defaults.defaultFlowRate
      }
      await previewTicket()
      refreshHandle.value = window.setInterval(loadOverview, 2000)
    })

    onUnmounted(() => {
      if (refreshHandle.value) {
        window.clearInterval(refreshHandle.value)
      }
    })

    return {
      overview,
      session,
      kpis,
      watchlist,
      launchChecklist,
      recentEvents,
      recentOrders,
      generatedAtLabel,
      apiStatus,
      busyAction,
      orderDraft,
      flowDraft,
      preview,
      previewWarnings,
      connectSession,
      disconnectSession,
      pulseTest,
      previewTicket,
      sendTicket,
      startDemoFlow,
      stopDemoFlow,
      eventClasses
    }
  }
}).mount('#app')

