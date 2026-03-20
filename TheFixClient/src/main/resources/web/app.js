import { createApp, computed, onMounted, reactive, ref } from 'https://unpkg.com/vue@3/dist/vue.esm-browser.prod.js'

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
              {{ session.connected ? 'Session primed' : 'Standby mode' }}
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
            <p class="mt-2 text-xs text-slate-400">Current workstation mode for this checkpoint.</p>
          </article>
          <article class="rounded-2xl border border-white/10 bg-dark-900 p-4">
            <p class="text-xs uppercase tracking-[0.2em] text-slate-500">Pulse checks</p>
            <p class="mt-3 text-2xl font-semibold text-cyan-300">{{ kpis.pulseChecks }}</p>
            <p class="mt-2 text-xs text-slate-400">Operator responsiveness checks executed through the shell API.</p>
          </article>
          <article class="rounded-2xl border border-white/10 bg-dark-900 p-4">
            <p class="text-xs uppercase tracking-[0.2em] text-slate-500">Open orders</p>
            <p class="mt-3 text-2xl font-semibold text-white">{{ kpis.openOrders }}</p>
            <p class="mt-2 text-xs text-slate-400">Illustrative desk workload for the UI rehearsal.</p>
          </article>
          <article class="rounded-2xl border border-white/10 bg-dark-900 p-4">
            <p class="text-xs uppercase tracking-[0.2em] text-slate-500">Session uptime</p>
            <p class="mt-3 text-2xl font-semibold text-white">{{ kpis.sessionUptime }}</p>
            <p class="mt-2 text-xs text-slate-400">Will become live FIX session uptime once connectivity is wired.</p>
          </article>
        </section>

        <section class="col-span-12 lg:col-span-4 space-y-6">
          <article class="rounded-2xl border border-white/10 bg-dark-900 p-5 shadow-glow">
            <div class="flex items-start justify-between gap-4">
              <div>
                <h2 class="text-base font-semibold text-white">Session command center</h2>
                <p class="mt-1 text-sm text-slate-400">A trader-oriented shell for simulator connectivity, with room for production controls later.</p>
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
                <h2 class="text-base font-semibold text-white">Order ticket rehearsal</h2>
                <p class="mt-1 text-sm text-slate-400">Preview trader intent, validation, and ticket notional before we attach live FIX routing.</p>
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
            </div>
            <div class="mt-5 flex flex-wrap gap-3">
              <button @click="previewTicket"
                      class="rounded-xl bg-white px-4 py-2 text-sm font-semibold text-dark-950 transition hover:bg-slate-200">
                Preview ticket
              </button>
              <div class="rounded-xl border border-white/10 bg-white/5 px-4 py-2 text-sm text-slate-300">
                Destination {{ session.targetCompId }} · {{ session.beginString }}
              </div>
            </div>

            <div class="mt-5 rounded-2xl border border-white/10 bg-gradient-to-br from-cyan-500/10 via-white/5 to-transparent p-4">
              <div class="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
                <div>
                  <p class="text-xs uppercase tracking-[0.2em] text-slate-400">Ticket summary</p>
                  <p class="mt-2 text-lg font-semibold text-white">{{ preview.summary || 'Generate a preview to review trader intent.' }}</p>
                  <p class="mt-2 text-sm text-slate-300">{{ preview.recommendation || 'The preview API is live and ready for the next checkpoint wiring.' }}</p>
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
        </section>

        <section class="col-span-12">
          <article class="rounded-2xl border border-white/10 bg-dark-900 p-5">
            <div class="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
              <div>
                <h2 class="text-base font-semibold text-white">Operator event stream</h2>
                <p class="mt-1 text-sm text-slate-400">Recent shell actions and workstation lifecycle events. This panel will evolve into the live activity stream.</p>
              </div>
              <div class="text-xs text-slate-500">Updated {{ generatedAtLabel }}</div>
            </div>
            <div class="mt-4 grid gap-3">
              <div v-for="event in recentEvents" :key="event.time + event.title"
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
      targetCompId: 'LLEXSIM'
    })
    const kpis = reactive({
      readyState: 'Loading…',
      pulseChecks: 0,
      openOrders: 0,
      sessionUptime: '—'
    })
    const watchlist = ref([])
    const launchChecklist = ref([])
    const recentEvents = ref([])
    const generatedAt = ref('')
    const apiStatus = ref('Connecting…')

    const orderDraft = reactive({
      symbol: 'AAPL',
      side: 'BUY',
      quantity: 100,
      price: 100.25,
      timeInForce: 'DAY'
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
      generatedAt.value = payload.generatedAt || ''
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
          detail: 'The client shell API could not be reached. Confirm the backend is running.'
        }]
      }
    }

    const connectSession = async () => applyOverview(await apiCall('/api/session/connect', { method: 'POST' }))
    const disconnectSession = async () => applyOverview(await apiCall('/api/session/disconnect', { method: 'POST' }))
    const pulseTest = async () => applyOverview(await apiCall('/api/session/pulse-test', { method: 'POST' }))

    const previewTicket = async () => {
      const payload = await apiCall('/api/order-ticket/preview', {
        method: 'POST',
        body: JSON.stringify(orderDraft)
      })
      Object.assign(preview, payload)
    }

    const eventClasses = (level) => EVENT_LEVEL_CLASSES[level] || EVENT_LEVEL_CLASSES.INFO

    onMounted(async () => {
      await loadOverview()
      await previewTicket()
    })

    return {
      overview,
      session,
      kpis,
      watchlist,
      launchChecklist,
      recentEvents,
      generatedAtLabel,
      apiStatus,
      orderDraft,
      preview,
      previewWarnings,
      connectSession,
      disconnectSession,
      pulseTest,
      previewTicket,
      eventClasses
    }
  }
}).mount('#app')

