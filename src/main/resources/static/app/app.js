const state = {
  modules: [
    { id: "overview", label: "Visão geral", route: "/", icon: "grid", enabled: true },
    { id: "finance", label: "Finanças", route: "/finance", icon: "wallet", enabled: true },
    { id: "health", label: "Saúde", route: "/health", icon: "heart", enabled: true },
    { id: "future", label: "Futuros módulos", route: "/future", icon: "plus", enabled: false },
    { id: "settings", label: "Definições", route: "/settings", icon: "settings", enabled: false, requiredPermission: "admin" }
  ],
  currentUser: null,
  accessConfigured: true,
  csrfToken: null,
  sidebarCollapsed: false,
  drawerOpen: false,
  pollingHandle: null
};

const money = new Intl.NumberFormat("pt-PT", { style: "currency", currency: "EUR" });
const number = new Intl.NumberFormat("pt-PT");

async function refreshSession() {
  const response = await fetch("/api/v1/auth/me", {
    credentials: "same-origin",
    headers: { "Accept": "application/json" }
  });
  if (!response.ok) {
    state.currentUser = null;
    state.csrfToken = null;
    return null;
  }
  const session = await response.json();
  state.currentUser = session.authenticated ? session : null;
  state.accessConfigured = session.accessConfigured;
  state.csrfToken = session.csrfToken || null;
  return session;
}

async function api(path, options = {}) {
  const headers = { "Accept": "application/json", ...(options.headers || {}) };
  const response = await fetch(path, { ...options, headers, credentials: "same-origin" });
  if (response.status === 401) {
    redirectToLogin();
    throw new Error("Sessão expirada. Inicia sessão novamente.");
  }
  if (!response.ok) {
    const problem = await response.json().catch(() => ({}));
    throw new Error(problem.detail || `Pedido falhou: ${response.status}`);
  }
  if (response.status === 204) return null;
  return response.json();
}

async function postJson(path, body = {}) {
  return api(path, {
    method: "POST",
    headers: { "Content-Type": "application/json", ...csrfHeaders() },
    body: JSON.stringify(body)
  });
}

async function postEmpty(path) {
  return api(path, { method: "POST", headers: csrfHeaders() });
}

function csrfHeaders() {
  return state.csrfToken ? { "X-XSRF-TOKEN": state.csrfToken } : {};
}

async function boot() {
  await refreshSession();
  window.addEventListener("popstate", render);

  if (!state.currentUser && !isLoginRoute()) {
    redirectToLogin();
    return;
  }
  if (state.currentUser && isLoginRoute()) {
    const next = new URLSearchParams(location.search).get("next") || "/";
    history.replaceState({}, "", next);
  }

  if (state.currentUser) {
    try {
      const modules = await api("/api/v1/dashboard/modules");
      state.modules = modules.filter(module => module.enabled);
    } catch {
      state.modules = state.modules.filter(module => module.enabled);
    }
  }
  render();
}

function isLoginRoute() {
  return location.pathname.startsWith("/login");
}

function redirectToLogin() {
  const current = `${location.pathname}${location.search}`;
  const next = current === "/login" ? "/" : current;
  history.replaceState({}, "", `/login?next=${encodeURIComponent(next)}`);
  renderLogin();
}

function navigate(route) {
  history.pushState({}, "", route);
  render();
}

function activeRoute() {
  if (location.pathname.startsWith("/finance")) return "finance";
  if (location.pathname.startsWith("/health")) return "health";
  return "overview";
}

function render() {
  if (state.pollingHandle) {
    clearInterval(state.pollingHandle);
    state.pollingHandle = null;
  }
  if (isLoginRoute()) {
    renderLogin();
    return;
  }
  if (!state.currentUser) {
    redirectToLogin();
    return;
  }

  const active = activeRoute();
  const app = document.getElementById("app");
  app.className = "app-shell";
  app.innerHTML = `
    <aside class="sidebar ${state.sidebarCollapsed ? "collapsed" : ""} ${state.drawerOpen ? "open" : ""}" aria-label="Navegação principal">
      <div class="brand">
        <div class="brand-mark" aria-hidden="true">C</div>
        <strong>ChiefAI</strong>
        <button class="icon-button desktop-only" title="Recolher menu" data-action="collapse">☰</button>
      </div>
      <nav>${state.modules.map(module => navItem(module, active)).join("")}</nav>
      <div class="sidebar-footer">
        <span>${escapeHtml(state.currentUser.username || "OWNER")}</span>
        <button class="nav-item logout-button" data-action="logout">Sair</button>
      </div>
    </aside>
    <div class="drawer-backdrop ${state.drawerOpen ? "visible" : ""}" data-action="drawer-close"></div>
    <main class="main">
      <header class="topbar">
        <button class="icon-button mobile-only" title="Abrir menu" data-action="drawer-open">☰</button>
        <div>
          <p class="eyebrow">ChiefAI</p>
          <h1>${titleFor(active)}</h1>
        </div>
      </header>
      <section id="view" class="view" tabindex="-1"></section>
    </main>`;
  bindShell();
  if (active === "finance") renderFinance();
  else if (active === "health") renderHealth();
  else renderOverview();
}

function renderLogin() {
  if (state.pollingHandle) {
    clearInterval(state.pollingHandle);
    state.pollingHandle = null;
  }
  const app = document.getElementById("app");
  app.className = "login-shell";
  app.innerHTML = `
    <main class="login-card" aria-labelledby="login-title">
      <div class="brand login-brand">
        <div class="brand-mark" aria-hidden="true">C</div>
        <strong>ChiefAI</strong>
      </div>
      <h1 id="login-title">Entrar</h1>
      <p class="login-copy">${state.accessConfigured ? "Acede ao dashboard privado." : "O acesso OWNER ainda não está configurado no backend."}</p>
      <form id="login-form" class="login-form">
        <label>Utilizador<input name="username" autocomplete="username" required></label>
        <label>Password<input name="password" type="password" autocomplete="current-password" required></label>
        <button class="primary-button" type="submit">Entrar</button>
        <p id="login-error" class="form-error" role="alert" hidden></p>
      </form>
    </main>`;
  document.getElementById("login-form").addEventListener("submit", handleLogin);
}

async function handleLogin(event) {
  event.preventDefault();
  const form = event.currentTarget;
  const error = document.getElementById("login-error");
  error.hidden = true;
  const body = Object.fromEntries(new FormData(form).entries());
  try {
    await api("/api/v1/auth/login", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body)
    });
    await refreshSession();
    const next = new URLSearchParams(location.search).get("next") || "/";
    history.replaceState({}, "", next);
    boot();
  } catch {
    error.textContent = "Credenciais inválidas ou acesso não configurado.";
    error.hidden = false;
  }
}

function navItem(module, active) {
  return `<button class="nav-item ${module.id === active ? "active" : ""}" data-route="${module.route}" aria-current="${module.id === active ? "page" : "false"}">
    <span class="nav-icon" aria-hidden="true">${icon(module.icon)}</span>
    <span>${module.label}</span>
  </button>`;
}

function icon(name) {
  const icons = { grid: "▦", wallet: "€", heart: "♡", plus: "+", settings: "⚙" };
  return icons[name] || "•";
}

function titleFor(active) {
  return active === "finance" ? "Finanças" : active === "health" ? "Saúde" : "Visão geral";
}

function bindShell() {
  bindRouteButtons(document);
  document.querySelector("[data-action='collapse']")?.addEventListener("click", () => {
    state.sidebarCollapsed = !state.sidebarCollapsed;
    render();
  });
  document.querySelector("[data-action='drawer-open']")?.addEventListener("click", () => {
    state.drawerOpen = true;
    render();
  });
  document.querySelector("[data-action='drawer-close']")?.addEventListener("click", () => {
    state.drawerOpen = false;
    render();
  });
  document.querySelector("[data-action='logout']")?.addEventListener("click", async () => {
    await api("/api/v1/auth/logout", { method: "POST", headers: csrfHeaders() }).catch(() => null);
    state.currentUser = null;
    state.csrfToken = null;
    redirectToLogin();
  });
}

function bindRouteButtons(root) {
  root.querySelectorAll("[data-route]").forEach(button => {
    button.addEventListener("click", () => {
      state.drawerOpen = false;
      navigate(button.dataset.route);
    });
  });
}

async function renderOverview() {
  const view = document.getElementById("view");
  view.innerHTML = loading();
  try {
    const data = await api("/api/v1/dashboard/overview");
    const finance = data.finance?.totals;
    const health = data.healthLatest;
    view.innerHTML = `
      <div class="grid cards">
        ${summaryCard("Total do mês", finance ? money.format(finance.total || 0) : "Sem dados", "Finanças", "/finance")}
        ${summaryCard("Variação financeira", finance?.previousPeriodVariationPercentage == null ? "Sem comparação" : `${finance.previousPeriodVariationPercentage}%`, "Finanças", "/finance")}
        ${summaryCard("Revisão pendente", data.finance ? number.format(data.finance.reviewPendingCount) : "0", "Finanças", "/finance")}
        ${summaryCard("Sleep Score", health?.sleepScore == null ? "Sem dados" : health.sleepScore, "Saúde", "/health")}
        ${summaryCard("Readiness", health?.readinessScore == null ? "Sem dados" : health.readinessScore, "Saúde", "/health")}
        ${summaryCard("Activity", health?.activityScore == null ? "Sem dados" : health.activityScore, "Saúde", "/health")}
      </div>
      <section class="panel">
        <h2>Connectors</h2>
        <div class="connector-grid">${Object.entries(data.connectors || {}).map(([name, value]) => `<span class="status-pill"><strong>${name}</strong> ${value.available ? "disponível" : "indisponível"}</span>`).join("") || "<p class=\"empty\">Sem connectors reportados.</p>"}</div>
      </section>`;
    bindRouteButtons(view);
  } catch (error) {
    view.innerHTML = errorState(error);
  }
}

function summaryCard(label, value, target, route) {
  return `<button class="metric-card" data-route="${route}">
    <span>${label}</span>
    <strong>${value}</strong>
    <small>Abrir ${target}</small>
  </button>`;
}

async function renderFinance() {
  const view = document.getElementById("view");
  view.innerHTML = financeSkeleton();
  bindFinanceFilters();
  await loadFinance();
  state.pollingHandle = setInterval(loadFinance, 60000);
}

function financeSkeleton() {
  const params = new URLSearchParams(location.search);
  const now = new Date();
  const yyyy = now.getFullYear();
  const mm = String(now.getMonth() + 1).padStart(2, "0");
  return `
    <div class="section-header">
      <div>
        <h2>Finanças</h2>
        <p id="finance-period">A carregar período</p>
      </div>
      <div class="header-actions">
        <span id="review-count" class="status-pill">Revisão: 0</span>
        <button id="export-excel" class="primary-button">Exportar para Excel</button>
      </div>
    </div>
    <form id="finance-filters" class="filters">
      <select name="preset" aria-label="Atalho de período">
        <option value="current">Mês atual</option>
        <option value="previous">Mês anterior</option>
        <option value="3m">Últimos 3 meses</option>
        <option value="6m">Últimos 6 meses</option>
        <option value="year">Ano atual</option>
        <option value="custom">Personalizado</option>
      </select>
      <input type="date" name="from" aria-label="Data inicial" value="${params.get("from") || `${yyyy}-${mm}-01`}">
      <input type="date" name="to" aria-label="Data final" value="${params.get("to") || new Date(yyyy, now.getMonth() + 1, 0).toISOString().slice(0,10)}">
      <select name="owner" aria-label="Titular"><option value="">Todos</option><option>DIOGO</option><option>MARIA</option></select>
      <select name="category" aria-label="Categoria"><option value="">Categorias</option><option>FIXED_COST</option><option>SUBSCRIPTION</option><option>VARIABLE_PURCHASE</option><option>OTHER</option></select>
      <input name="subcategory" placeholder="Subcategoria" value="${escapeHtml(params.get("subcategory") || "")}">
      <input name="supplier" placeholder="Fornecedor" value="${escapeHtml(params.get("supplier") || "")}">
      <select name="status" aria-label="Estado"><option value="">Todos estados</option><option>EXTRACTED</option><option>APPROVED</option><option>REVIEW_REQUIRED</option><option>IGNORED_OTHER_OWNER</option><option>FAILED</option></select>
      <input name="minAmount" type="number" step="0.01" placeholder="Valor mín.">
      <input name="maxAmount" type="number" step="0.01" placeholder="Valor máx.">
      <input name="search" placeholder="Pesquisar">
      <button class="secondary-button">Aplicar</button>
    </form>
    <div id="finance-content">${loading()}</div>`;
}

function bindFinanceFilters() {
  const form = document.getElementById("finance-filters");
  const params = new URLSearchParams(location.search);
  for (const [key, value] of params.entries()) {
    const input = form.elements[key];
    if (input) input.value = value;
  }
  form.elements.preset.addEventListener("change", () => applyPreset(form.elements.preset.value));
  form.addEventListener("submit", event => {
    event.preventDefault();
    const query = filtersToQuery();
    history.replaceState({}, "", `/finance?${query}`);
    loadFinance();
  });
  document.getElementById("export-excel").addEventListener("click", exportExcel);
}

function applyPreset(value) {
  const form = document.getElementById("finance-filters");
  const now = new Date();
  const start = new Date(now.getFullYear(), now.getMonth(), 1);
  const end = new Date(now.getFullYear(), now.getMonth() + 1, 0);
  if (value === "previous") {
    start.setMonth(start.getMonth() - 1);
    end.setDate(0);
  } else if (value === "3m") {
    start.setMonth(start.getMonth() - 2);
  } else if (value === "6m") {
    start.setMonth(start.getMonth() - 5);
  } else if (value === "year") {
    start.setMonth(0, 1);
  } else if (value === "custom") {
    return;
  }
  form.elements.from.value = toDateInput(start);
  form.elements.to.value = toDateInput(end);
}

function toDateInput(date) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
}

function filtersToQuery() {
  const data = new FormData(document.getElementById("finance-filters"));
  const params = new URLSearchParams();
  for (const [key, value] of data.entries()) {
    if (key !== "preset" && value) params.set(key, value);
  }
  return params.toString();
}

async function loadFinance() {
  const content = document.getElementById("finance-content");
  try {
    const query = filtersToQuery();
    const [dashboard, expenses] = await Promise.all([
      api(`/api/v1/finance/dashboard?${query}`),
      api(`/api/v1/finance/expenses?${query}&page=0&size=25&sort=documentDate,desc`)
    ]);
    document.getElementById("finance-period").textContent = `${dashboard.period.from} a ${dashboard.period.to} · última atualização ${dashboard.lastUpdatedAt || "sem dados"}`;
    document.getElementById("review-count").textContent = `Revisão: ${dashboard.reviewPendingCount}`;
    content.innerHTML = `
      ${kpis(dashboard.totals)}
      <div class="chart-grid">
        ${barChart("Evolução mensal", dashboard.monthlyEvolution.map(x => [x.month, x.total]))}
        ${barChart("Distribuição por categoria", dashboard.byCategory.map(x => [x.category, x.total]))}
        ${barChart("Fixos / subscrições / variáveis", dashboard.typeBreakdown.map(x => [x.subcategory || x.category, x.total]))}
        ${barChart("Por titular", dashboard.byOwner.map(x => [x.owner, x.total]))}
        ${barChart("Principais fornecedores", dashboard.topSuppliers.map(x => [x.supplier, x.total]))}
        ${comparisonCard(dashboard.previousPeriodComparison)}
      </div>
      ${expenseTable(expenses)}`;
    bindExpenseActions();
  } catch (error) {
    content.innerHTML = errorState(error);
  }
}

function kpis(totals) {
  const values = [
    ["Total", totals.total], ["Custos fixos", totals.fixedCosts], ["Subscrições", totals.subscriptions],
    ["Variáveis", totals.variablePurchases], ["Outros", totals.other], ["Diogo", totals.diogo],
    ["Maria", totals.maria], ["N.º despesas", totals.expenseCount], ["Variação", totals.previousPeriodVariationPercentage == null ? null : `${totals.previousPeriodVariationPercentage}%`]
  ];
  return `<div class="grid kpis">${values.map(([label, value]) => `<div class="kpi"><span>${label}</span><strong>${typeof value === "number" ? money.format(value) : value ?? "Sem dados"}</strong></div>`).join("")}</div>`;
}

function barChart(title, values) {
  const max = Math.max(...values.map(v => Number(v[1] || 0)), 0);
  if (!values.length || max === 0) return `<section class="panel chart"><h3>${title}</h3><p class="empty">Sem dados para este período.</p></section>`;
  return `<section class="panel chart"><h3>${title}</h3>${values.map(([label, value]) => {
    const width = Math.max(4, (Number(value || 0) / max) * 100);
    return `<div class="bar-row"><span>${escapeHtml(label)}</span><div class="bar-track"><div class="bar" style="width:${width}%"></div></div><strong>${money.format(value || 0)}</strong></div>`;
  }).join("")}</section>`;
}

function comparisonCard(c) {
  return `<section class="panel chart"><h3>Período anterior</h3><div class="comparison"><span>Atual ${money.format(c.currentTotal || 0)}</span><span>Anterior ${money.format(c.previousTotal || 0)}</span><strong>${c.variationPercentage == null ? "Sem comparação" : `${c.variationPercentage}%`}</strong></div></section>`;
}

function expenseTable(page) {
  if (!page.content.length) return `<section class="panel"><p class="empty">Sem despesas para os filtros atuais.</p></section>`;
  return `<section class="panel table-panel"><table><thead><tr><th>Data</th><th>Titular</th><th>Fornecedor</th><th>Documento</th><th>Descrição</th><th>Categoria</th><th>Subcategoria</th><th>Tipo</th><th>Valor</th><th>Estado</th><th>Origem</th><th>Ações</th></tr></thead><tbody>
    ${page.content.map(row => `<tr class="${row.status === "REVIEW_REQUIRED" ? "needs-review" : ""}">
      <td>${row.documentDate || ""}</td><td>${row.owner}</td><td>${escapeHtml(row.supplierName || "")}</td><td>${escapeHtml(row.documentNumber || "")}</td><td>${escapeHtml(row.description || "")}</td>
      <td>${row.category}</td><td>${escapeHtml(row.subcategory || "")}</td><td>${row.recurring ? "recorrente" : "pontual"}</td><td>${money.format(row.grossAmount || 0)}</td><td>${row.status}</td><td>${row.source}</td>
      <td><button class="link-button" data-detail="${row.id}">Detalhe</button>${row.status === "REVIEW_REQUIRED" ? `<button class="link-button" data-approve="${row.id}">Aprovar</button>` : ""}${row.status === "FAILED" ? `<button class="link-button" data-retry="${row.id}">Retry</button>` : ""}</td>
    </tr>`).join("")}
  </tbody></table><p class="table-meta">Página ${page.page + 1} de ${Math.max(page.totalPages, 1)} · ${page.totalElements} registos</p><div id="expense-detail"></div></section>`;
}

function bindExpenseActions() {
  document.querySelectorAll("[data-detail]").forEach(button => button.addEventListener("click", async () => {
    const detail = await api(`/api/v1/finance/expenses/${button.dataset.detail}`);
    document.getElementById("expense-detail").innerHTML = `<aside class="drawer-panel"><h3>Detalhe da despesa</h3><dl>
      <dt>Fornecedor</dt><dd>${escapeHtml(detail.expense.supplierName || "")}</dd>
      <dt>Valor</dt><dd>${money.format(detail.expense.grossAmount || 0)}</dd>
      <dt>Confiança</dt><dd>${detail.expense.extractionConfidence}</dd>
      <dt>Evidência</dt><dd>${(detail.expense.evidence || []).map(escapeHtml).join("; ") || "Sem evidência"}</dd>
      <dt>Referência Gmail</dt><dd>${detail.safeSourceReference}</dd>
      <dt>Histórico</dt><dd>${detail.history.map(h => `${h.action} ${h.createdAt}`).join("<br>") || "Sem alterações"}</dd>
    </dl></aside>`;
  }));
  document.querySelectorAll("[data-approve]").forEach(button => button.addEventListener("click", async () => {
    await postEmpty(`/api/v1/finance/expenses/${button.dataset.approve}/approve`);
    await loadFinance();
  }));
  document.querySelectorAll("[data-retry]").forEach(button => button.addEventListener("click", async () => {
    await postEmpty(`/api/v1/finance/expenses/${button.dataset.retry}/retry`);
    await loadFinance();
  }));
}

async function exportExcel() {
  const button = document.getElementById("export-excel");
  button.disabled = true;
  button.textContent = "A exportar...";
  try {
    const response = await fetch(`/api/v1/finance/expenses/export?${filtersToQuery()}`, {
      credentials: "same-origin",
      headers: { "Accept": "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" }
    });
    if (response.status === 401) {
      redirectToLogin();
      return;
    }
    if (!response.ok) throw new Error((await response.json().catch(() => ({}))).detail || "Exportação falhou");
    const blob = await response.blob();
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = filenameFromDisposition(response.headers.get("Content-Disposition")) || "chiefai-financas.xlsx";
    link.click();
    URL.revokeObjectURL(url);
  } catch (error) {
    alert(error.message);
  } finally {
    button.disabled = false;
    button.textContent = "Exportar para Excel";
  }
}

async function renderHealth() {
  const view = document.getElementById("view");
  view.innerHTML = loading();
  try {
    const [snapshots, trends] = await Promise.all([
      api("/api/v1/health/snapshots"),
      api("/api/v1/health/trends")
    ]);
    const latest = snapshots.at(-1);
    view.innerHTML = `<p class="medical-note">Esta análise é informativa e não constitui diagnóstico médico.</p>
      <div class="grid cards">
        ${summaryBlock("Hoje", latest ? `${latest.date} · ${latest.source}` : "Sem dados")}
        ${summaryBlock("Sono", latest?.sleepScore == null ? "Sem dados" : `Sleep ${latest.sleepScore}`)}
        ${summaryBlock("Recuperação e stress", latest?.readinessScore == null ? "Sem dados" : `Readiness ${latest.readinessScore}`)}
        ${summaryBlock("Coração", latest?.averageHrv == null ? "Sem dados" : `HRV ${latest.averageHrv}`)}
        ${summaryBlock("Frequência repouso", latest?.restingHeartRate == null ? "Sem dados" : `${latest.restingHeartRate} bpm`)}
        ${summaryBlock("Duração sono", latest?.sleepDurationMinutes == null ? "Sem dados" : `${latest.sleepDurationMinutes} min`)}
        ${summaryBlock("Atividade", latest?.activityScore == null ? "Sem dados" : `Activity ${latest.activityScore}`)}
        ${summaryBlock("Tendências", `${trends.count || 0} snapshots`)}
      </div>`;
  } catch (error) {
    view.innerHTML = errorState(error);
  }
}

function summaryBlock(label, value) {
  return `<div class="metric-card static"><span>${label}</span><strong>${value}</strong></div>`;
}

function loading() {
  return `<div class="loading" role="status">A carregar...</div>`;
}

function errorState(error) {
  return `<div class="error" role="alert"><strong>Não foi possível carregar.</strong><span>${escapeHtml(error.message)}</span></div>`;
}

function filenameFromDisposition(value) {
  const match = value && value.match(/filename="([^"]+)"/);
  return match ? match[1] : null;
}

function escapeHtml(value) {
  return String(value ?? "").replace(/[&<>"']/g, char => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#039;" }[char]));
}

boot();
