const $ = (selector, root = document) => root.querySelector(selector);
const $$ = (selector, root = document) => [...root.querySelectorAll(selector)];
const state = { trips: [], destinations: [], invitations: [], activeTrip: null, currentUser: null, csrfToken: null, canEdit: false, canContribute: false, shared: false, authMode: 'login', toastTimer: null };

const money = value => new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 }).format(Number(value || 0));
const esc = value => String(value ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const dateLong = value => value ? new Date(`${value}T12:00:00`).toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' }) : '';
const dateShort = value => value ? new Date(`${value}T12:00:00`).toLocaleDateString('en-IN', { day: 'numeric', month: 'short' }) : '';
const tripDays = trip => Math.max(1, Math.round((new Date(`${trip.endDate}T12:00:00`) - new Date(`${trip.startDate}T12:00:00`)) / 86400000) + 1);
const emojiFor = name => ({ Jaipur: '🕌', Coorg: '☕', Kyoto: '⛩️', Bali: '🌴', Lisbon: '🚋', Paris: '🥐', Reykjavik: '🌌' }[name] || '✳');
const tripRange = trip => `${dateShort(trip.startDate)} — ${dateShort(trip.endDate)}`;

async function api(path, options = {}) {
  const method = (options.method || 'GET').toUpperCase();
  const headers = { 'Content-Type': 'application/json', ...(options.headers || {}) };
  if (method !== 'GET' && state.csrfToken) headers['X-CSRF-TOKEN'] = state.csrfToken;
  const response = await fetch(path, { credentials: 'same-origin', ...options, method, headers });
  if (!response.ok) {
    let message = 'Something went wrong. Please try again.';
    try { const body = await response.json(); message = body.message || message; } catch (_) {}
    throw new Error(message);
  }
  if (response.status === 204) return null;
  const body = await response.text();
  return body ? JSON.parse(body) : null;
}

function toast(message) {
  const node = $('#toast'); node.textContent = message; node.classList.add('show');
  clearTimeout(state.toastTimer); state.toastTimer = setTimeout(() => node.classList.remove('show'), 3000);
}

function showScreen(which) {
  $('#authScreen').classList.toggle('hidden', which !== 'auth');
  $('#dashboardScreen').classList.toggle('hidden', which !== 'dashboard');
  $('#tripScreen').classList.toggle('hidden', which !== 'trip');
  $('#sharedErrorScreen').classList.toggle('hidden', which !== 'error');
  $('#topActions').classList.toggle('hidden', !state.currentUser);
  if (which !== 'trip') state.activeTrip = null;
  window.scrollTo({ top: 0, behavior: 'smooth' });
}

function updateHeader() {
  const user = state.currentUser;
  $('#topActions').classList.toggle('hidden', !user);
  $('#mainNav').classList.toggle('hidden', !user);
  if (user) {
    $('#userGreeting').textContent = `Hi, ${user.username}`;
    $('#userAvatar').textContent = user.username.slice(0, 1).toUpperCase();
  }
}

function setAuthMode(mode) {
  state.authMode = mode;
  const signup = mode === 'signup';
  $('#authTitle').textContent = signup ? 'Start your journal.' : 'Welcome back.';
  $('#authDescription').textContent = signup ? 'Create an account and keep every trip in one place.' : 'Sign in to pick up where your next trip left off.';
  $('#authSubmit').innerHTML = signup ? 'Create account <span>↗</span>' : 'Log in <span>↗</span>';
  $('#authToggle').innerHTML = signup ? 'Already have an account? <strong>Log in</strong>' : 'New to Wayfarer? <strong>Create an account</strong>';
  $('#authPassword').autocomplete = signup ? 'new-password' : 'current-password';
  $('#authError').textContent = '';
}

function renderStats() {
  $('#statTrips').textContent = state.trips.length;
  $('#statBudget').textContent = money(state.trips.reduce((sum, trip) => sum + trip.plannedBudget, 0));
  $('#statPlaces').textContent = new Set(state.trips.map(trip => trip.destination.toLowerCase())).size;
}

function renderTrips() {
  const grid = $('#tripsGrid');
  if (!state.trips.length) {
    grid.innerHTML = '<div class="empty-card"><span class="empty-icon">✳</span><strong>A blank map is full of possibility.</strong><span>Start planning your first trip and put a pin in somewhere new.</span><br><button class="button button-dark" data-create-trip>Plan a trip ↗</button></div>';
    return;
  }
  grid.innerHTML = state.trips.map((trip, index) => {
    const progress = trip.plannedBudget ? Math.min(100, trip.actualSpend / trip.plannedBudget * 100) : 0;
    const art = ['peach', 'sage', 'lavender', 'blue'][index % 4];
    return `<article class="trip-card" data-trip="${trip.id}" tabindex="0" aria-label="Open ${esc(trip.title)}">
      <div class="trip-card-art trip-art-${art}"><span class="trip-art-emblem">${emojiFor(trip.destination)}</span><span class="trip-date-chip">${esc(tripRange(trip))}</span></div>
      <div class="trip-card-body"><div class="trip-card-top"><div><h3>${esc(trip.title)}</h3><div class="trip-destination">⌖ &nbsp;${esc(trip.destination)}</div></div><span class="card-arrow">↗</span></div>
      <div class="trip-card-meta"><span><span class="trip-meta-icon">◷</span> &nbsp;${tripDays(trip)} days</span><span><span class="trip-meta-icon">✦</span> &nbsp;${esc(trip.transport)}</span><span>${trip.ownerUsername !== state.currentUser?.username ? `✧ invited by @${esc(trip.ownerUsername)}` : trip.publiclyVisible ? '🌐 Public' : '🔒 Private'}</span></div>
      <div class="trip-card-footer"><div class="budget-mini"><span>${money(trip.actualSpend)} spent · ${money(trip.plannedBudget)} planned</span><div class="progress-track"><i style="width:${progress}%"></i></div></div><strong class="budget-value">${Math.round(progress)}%</strong></div></div></article>`;
  }).join('');
  $$('.trip-card', grid).forEach(card => {
    card.addEventListener('click', () => openTrip(Number(card.dataset.trip)));
    card.addEventListener('keydown', event => { if (event.key === 'Enter') openTrip(Number(card.dataset.trip)); });
  });
}

function renderInvitations() {
  const section = $('#invitationsSection'), list = $('#invitationsList');
  section.classList.toggle('hidden', state.invitations.length === 0);
  if (!state.invitations.length) { list.innerHTML = ''; return; }
  list.innerHTML = state.invitations.map(invite => `<article class="invitation-card"><span class="invitation-icon">✳</span><div class="invitation-copy"><strong>${esc(invite.tripTitle)}</strong><span>${esc(invite.destination)} · invited by @${esc(invite.invitedBy)}</span></div><div class="invitation-actions"><button class="button button-dark" data-accept-invite="${invite.id}">Accept</button><button class="button decline-invite" data-decline-invite="${invite.id}">Pass</button></div></article>`).join('');
  $$('[data-accept-invite]', list).forEach(button => button.addEventListener('click', () => respondToInvitation(button.dataset.acceptInvite, true)));
  $$('[data-decline-invite]', list).forEach(button => button.addEventListener('click', () => respondToInvitation(button.dataset.declineInvite, false)));
}

async function respondToInvitation(id, accept) {
  try {
    await api(`/api/invitations/${id}/${accept ? 'accept' : 'decline'}`, { method: 'POST', body: '{}' });
    await loadDashboard();
    toast(accept ? 'You joined the trip. Open it to add shared expenses.' : 'Invitation passed.');
  } catch (error) { toast(error.message); }
}

function renderDestinations(list = state.destinations) {
  const grid = $('#destinationGrid');
  if (!list.length) { grid.innerHTML = '<div class="empty-card">No places found. Try another search.</div>'; return; }
  grid.innerHTML = list.map(place => `<button class="destination-card" data-destination="${esc(place.name)}" aria-label="Plan a trip to ${esc(place.name)}">
    <div class="destination-art ${esc(place.tone)}">${esc(place.emoji)}</div><div class="destination-copy"><div class="destination-name">${esc(place.name)}</div><div class="destination-country">${esc(place.country)}</div><p>${esc(place.description)}</p><span class="best-season">Best from ${esc(place.season)}</span></div></button>`).join('');
  $$('.destination-card', grid).forEach(card => card.addEventListener('click', () => openTripDialog(card.dataset.destination)));
}

async function loadDashboard() {
  if (!state.currentUser) { showScreen('auth'); return; }
  try {
    const [trips, destinations, invitations] = await Promise.all([api('/api/trips'), api('/api/destinations'), api('/api/invitations')]);
    state.trips = trips; state.destinations = destinations; state.invitations = invitations;
    renderStats(); renderTrips(); renderDestinations();
    renderInvitations();
    $('#destinationOptions').innerHTML = destinations.map(place => `<option value="${esc(place.name)}, ${esc(place.country)}"></option>`).join('');
  } catch (error) { toast(error.message); $('#tripsGrid').innerHTML = '<div class="empty-card">Could not load your trips. Refresh to try again.</div>'; }
}

function openTripDialog(destination = '') {
  if (!state.currentUser) return showScreen('auth');
  const form = $('#tripForm'); form.reset();
  const start = new Date(); start.setDate(start.getDate() + 14);
  const end = new Date(start); end.setDate(end.getDate() + 3);
  form.elements.startDate.value = start.toISOString().slice(0, 10);
  form.elements.endDate.value = end.toISOString().slice(0, 10);
  form.elements.destination.value = destination;
  form.elements.publiclyVisible.value = 'false';
  $('#tripDialog').showModal();
}

function renderTripHeader(trip) {
  state.canEdit = !!state.currentUser && state.currentUser.username === trip.ownerUsername;
  const eyebrow = state.canEdit ? 'YOUR NEXT ESCAPE' : trip.publiclyVisible ? `PUBLIC TRIP BY @${esc(trip.ownerUsername)}` : `INVITED BY @${esc(trip.ownerUsername)}`;
  let actions = '';
  if (state.canEdit) {
    actions += trip.publiclyVisible
      ? '<button class="button share-button" id="shareTripButton">↗ Copy public link</button><button class="button button-outline" id="privacyButton">Make private</button>'
      : '<button class="button share-button" id="shareTripButton">🌐 Make public</button>';
    actions += '<button class="button delete-button" id="deleteTripButton">Delete trip</button>';
  } else if (trip.publiclyVisible) {
    actions = '<button class="button button-outline" id="shareTripButton">↗ Copy public link</button>';
  }
  $('#tripHeader').innerHTML = `<div class="trip-heading-copy"><div class="eyebrow dark-eyebrow">${eyebrow}</div><h1>${esc(trip.title)}</h1><div class="trip-subline"><span>⌖ ${esc(trip.destination)}</span><span>◷ ${esc(dateLong(trip.startDate))} — ${esc(dateLong(trip.endDate))}</span><span>✦ ${esc(trip.transport)}</span><span>${trip.publiclyVisible ? '🌐 Public' : '🔒 Private'}</span></div></div><div class="trip-header-actions">${actions}</div>`;
  if ($('#shareTripButton')) $('#shareTripButton').addEventListener('click', toggleShare);
  if ($('#privacyButton')) $('#privacyButton').addEventListener('click', makePrivate);
  if ($('#deleteTripButton')) $('#deleteTripButton').addEventListener('click', deleteTrip);
}

function renderItinerary(trip) {
  const target = $('#itineraryContent');
  if (!trip.itinerary.length) { target.innerHTML = '<div class="itinerary-empty">No plans on the page yet. Add a little something to look forward to.</div>'; return; }
  const groups = new Map(); trip.itinerary.forEach(item => { if (!groups.has(item.dayNumber)) groups.set(item.dayNumber, []); groups.get(item.dayNumber).push(item); });
  target.innerHTML = [...groups.entries()].sort((a, b) => a[0] - b[0]).map(([day, items]) => {
    const date = new Date(`${trip.startDate}T12:00:00`); date.setDate(date.getDate() + day - 1);
    return `<div class="day-block"><div class="day-label"><span class="day-number">${day}</span><strong>Day ${day}</strong><span>${date.toLocaleDateString('en-IN', { weekday: 'long', day: 'numeric', month: 'short' })}</span></div>
      ${items.map(item => `<div class="activity-row"><span class="activity-time">${item.startTime ? esc(item.startTime.slice(0, 5)) : 'Anytime'}</span><i class="activity-dot"></i><div class="activity-copy"><strong>${esc(item.activity)}</strong><span>${esc(item.location || item.category)}${item.notes ? ` · ${esc(item.notes)}` : ''}</span></div><span class="activity-cost">${item.cost ? money(item.cost) : ''}</span>${state.canEdit ? `<button class="remove-activity" data-delete-item="${item.id}" title="Remove activity" aria-label="Remove ${esc(item.activity)}">×</button>` : ''}</div>`).join('')}</div>`;
  }).join('');
  $$('[data-delete-item]', target).forEach(button => button.addEventListener('click', () => removeActivity(button.dataset.deleteItem)));
}

function renderBudget(trip) {
  const planned = Number(trip.plannedBudget || 0), spent = Number(trip.actualSpend || 0);
  const percent = planned ? Math.min(100, spent / planned * 100) : 0;
  const categories = [['Transport', trip.transportBudget], ['Stay', trip.accommodationBudget], ['Food', trip.foodBudget], ['Activities', trip.activitiesBudget], ['Other', trip.otherBudget]];
  $('#budgetSummary').innerHTML = `<div class="budget-total"><strong>${money(spent)}</strong><span>of ${money(planned)}</span></div><div class="budget-track"><i style="width:${percent}%"></i></div><div class="budget-caption"><span>Spent so far</span><span>${Math.round(percent)}% of total</span></div><div class="category-list">${categories.map(([label, value]) => `<div class="category-line"><i></i><span>${label}</span><b>${money(value)}</b></div>`).join('')}</div>`;
}

async function renderWeather(city) {
  const card = $('#weatherCard'); card.innerHTML = '<div class="weather-loading">Checking the skies…</div>';
  try {
    const data = await api(`/api/weather?city=${encodeURIComponent(city)}`);
    const icon = /clear/i.test(data.condition) ? '☀' : /cloud|overcast|fog/i.test(data.condition) ? '☁' : /snow/i.test(data.condition) ? '❄' : /rain|drizzle|storm/i.test(data.condition) ? '☂' : '⛅';
    const days = data.forecast.map(day => `<div class="forecast-day"><span>${new Date(`${day.date}T12:00:00`).toLocaleDateString('en-IN', { weekday: 'short' })}</span><b>${Math.round(day.high)}° / ${Math.round(day.low)}°</b></div>`).join('');
    card.innerHTML = `<div class="weather-top"><div><div class="weather-label">THE SKY RIGHT NOW</div><div class="weather-location">${esc(data.location)}</div></div><span class="weather-icon">${icon}</span></div><div class="weather-temp-row"><span class="weather-temp">${Math.round(data.temperature)}°</span><span class="weather-unit">C</span></div><div class="weather-condition">${esc(data.condition)} · feels like ${Math.round(data.feelsLike)}°</div><div class="weather-meta"><span>↗ ${Math.round(data.windKph)} km/h wind</span><span>☂ ${data.precipitationMm} mm rain</span></div><div class="forecast-row">${days}</div>`;
  } catch (error) { card.innerHTML = `<div class="weather-label">LOCAL FORECAST</div><div class="weather-location">${esc(city)}</div><p class="weather-loading" style="margin-top:12px;line-height:1.5">${esc(error.message)} Weather needs an internet connection.</p>`; }
}

async function loadReviews(destination) {
  $('#reviewDestinationName').textContent = destination;
  const list = $('#reviewList'); list.innerHTML = '<div class="no-reviews">Loading traveler notes…</div>';
  try {
    const reviews = await api(`/api/reviews?destination=${encodeURIComponent(destination)}`);
    list.innerHTML = reviews.length ? reviews.map(review => `<article class="review-item"><div class="review-avatar">${esc(review.author.charAt(0).toUpperCase())}</div><div class="review-content"><div class="review-topline"><strong>${esc(review.author)}</strong><span class="review-stars">${'★'.repeat(review.rating)}${'☆'.repeat(5 - review.rating)}</span><span class="review-date">${new Date(review.createdAt).toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' })}</span></div><p>${esc(review.comment)}</p></div></article>`).join('') : '<div class="no-reviews">No traveler notes yet. Be the first to leave one.</div>';
  } catch (error) { list.innerHTML = `<div class="no-reviews">${esc(error.message)}</div>`; }
}

async function loadCollaboration(trip) {
  state.canContribute = false;
  $('#inviteForm').classList.toggle('hidden', !state.canEdit);
  $('#expenseForm').classList.add('hidden');
  $('#groupAccessMessage').classList.add('hidden');
  $('#memberList').innerHTML = '<div class="expense-empty">Loading trip crew…</div>';
  try {
    const group = await api(`/api/trips/${trip.id}/members`);
    state.canContribute = group.viewerCanAddExpenses;
    $('#crewCount').textContent = `${group.members.length} ${group.members.length === 1 ? 'person' : 'people'}`;
    $('#memberList').innerHTML = group.members.map(member => {
      const isYou = member.username === state.currentUser?.username;
      const status = member.status === 'PENDING' ? '<span class="pending-badge">INVITED</span>' : '';
      const remove = state.canEdit && !member.owner ? `<button class="remove-activity" data-remove-member="${member.id}" title="Remove from trip" aria-label="Remove @${esc(member.username)}">×</button>` : '';
      return `<span class="member-chip ${member.owner ? 'owner-chip' : ''}"><i class="member-avatar">${esc(member.username.charAt(0).toUpperCase())}</i><span>${member.owner ? 'Owner' : '@' + esc(member.username)}${isYou ? ' · you' : ''}</span>${status}${remove}</span>`;
    }).join('');
    $$('[data-remove-member]', $('#memberList')).forEach(button => button.addEventListener('click', () => removeMember(button.dataset.removeMember)));
    $('#inviteHint').textContent = state.canEdit
      ? 'Accepted friends can view private trips and add expenses. Only you can edit the itinerary.'
      : 'Expenses are split equally among the owner and accepted invitees.';
    $('#expenseForm').classList.toggle('hidden', !state.canContribute);
    $('#expensePayerHint').classList.toggle('hidden', !state.canContribute);
    $('#groupAccessMessage').classList.toggle('hidden', state.canContribute);
    const summary = await api(`/api/trips/${trip.id}/expenses`);
    renderExpenses(summary);
  } catch (error) {
    state.canContribute = false;
    $('#memberList').innerHTML = '<div class="expense-empty">Trip crew is available to the owner and accepted invitees.</div>';
    $('#crewCount').textContent = '';
    $('#inviteForm').classList.toggle('hidden', !state.canEdit);
    $('#expenseForm').classList.add('hidden');
    $('#expensePayerHint').classList.add('hidden');
    $('#groupAccessMessage').classList.remove('hidden');
    $('#groupAccessMessage').textContent = 'Shared expenses and trip members are visible to the owner and accepted invitees. Ask the owner to invite your account.';
    $('#expenseList').innerHTML = '';
    $('#balanceSummary').innerHTML = '';
    $('#expenseTotal').textContent = '';
  }
}

function renderExpenses(summary) {
  $('#expenseTotal').textContent = money(summary.totalSpent);
  const balances = $('#balanceSummary');
  balances.innerHTML = summary.balances.length ? summary.balances.map(person => {
    const balance = Number(person.balance || 0), current = person.username === state.currentUser?.username;
    const kind = balance > 0.009 ? 'positive' : balance < -0.009 ? 'negative' : 'neutral';
    const label = balance > 0.009 ? `gets back ${money(balance)}` : balance < -0.009 ? `owes ${money(Math.abs(balance))}` : 'all settled';
    return `<div class="balance-card ${kind} ${current ? 'current-balance' : ''}"><span>${current ? 'You' : '@' + esc(person.username)} · paid ${money(person.paid)}</span><strong>${label}</strong></div>`;
  }).join('') : '<div class="expense-empty">Add a friend to start sharing trip costs.</div>';
  const expenseList = $('#expenseList');
  if (!summary.expenses.length) { expenseList.innerHTML = '<div class="expense-empty">No shared expenses yet. Add the first one above.</div>'; return; }
  expenseList.innerHTML = [...summary.expenses].reverse().map(expense => {
    const splitText = expense.shares.map(share => `@${esc(share.username)} ${money(share.share)}`).join(' · ');
    const paidBy = expense.paidBy === state.currentUser?.username ? 'You' : `@${esc(expense.paidBy)}`;
    return `<article class="expense-row"><div class="expense-description"><strong>${esc(expense.description)}</strong><span>${esc(expense.category)} · paid by ${paidBy} · ${new Date(expense.createdAt).toLocaleDateString('en-IN', { day: 'numeric', month: 'short' })}</span></div><div class="expense-amount-copy"><strong>${money(expense.amount)}</strong><span>total</span></div><div class="expense-split-note">Equal shares: ${splitText}</div></article>`;
  }).join('');
}

async function removeMember(memberId) {
  try {
    await api(`/api/trips/${state.activeTrip.id}/members/${memberId}`, { method: 'DELETE' });
    await loadCollaboration(state.activeTrip); toast('Traveler removed from this trip.');
  } catch (error) { toast(error.message); }
}

function openTrip(id) {
  const trip = state.trips.find(item => item.id === id); if (!trip) return;
  state.activeTrip = trip; state.shared = false;
  history.replaceState({}, '', '/');
  showScreen('trip'); renderTripHeader(trip); renderItinerary(trip); renderBudget(trip);
  $('#sharedNotice').classList.toggle('hidden', state.canEdit);
  $('#sharedNotice').textContent = state.canEdit ? '' : '✦ You joined this trip as an invitee. The owner manages the itinerary; add your expenses below.';
  $('#addActivityButton').classList.toggle('hidden', !state.canEdit);
  $('#reviewForm').classList.remove('hidden'); $('#routeFrom').value = 'My location'; $('#routeTo').value = trip.destination;
  renderWeather(trip.destination); loadReviews(trip.destination); loadCollaboration(trip);
}

async function openSharedTrip(token) {
  try {
    const trip = await api(`/api/shared/${encodeURIComponent(token)}`);
    state.activeTrip = trip;
    state.canEdit = !!state.currentUser && state.currentUser.username === trip.ownerUsername;
    state.shared = !state.canEdit;
    showScreen('trip'); renderTripHeader(trip); renderItinerary(trip); renderBudget(trip);
    $('#sharedNotice').classList.toggle('hidden', state.canEdit);
    $('#sharedNotice').textContent = state.canEdit ? '' : trip.publiclyVisible
      ? '✦ Public itinerary · read-only for visitors'
      : '✦ You joined this private trip as an invitee. The owner manages the itinerary; add your expenses below.';
    $('#addActivityButton').classList.toggle('hidden', !state.canEdit); $('#reviewForm').classList.remove('hidden');
    $('#routeFrom').value = 'My location'; $('#routeTo').value = trip.destination;
    renderWeather(trip.destination); loadReviews(trip.destination); loadCollaboration(trip);
  } catch (_) { showScreen('error'); }
}

async function copyLink(trip) {
  const link = `${location.origin}/?share=${encodeURIComponent(trip.shareToken)}`;
  try { await navigator.clipboard.writeText(link); toast('Public trip link copied.'); }
  catch (_) { window.prompt('Copy this public trip link:', link); }
}

async function toggleShare() {
  const trip = state.activeTrip;
  if (trip.publiclyVisible) return copyLink(trip);
  try {
    const updated = await api(`/api/trips/${trip.id}/share`, { method: 'PATCH', body: JSON.stringify({ publiclyVisible: true }) });
    syncTrip(updated); await copyLink(updated);
  } catch (error) { toast(error.message); }
}

async function makePrivate() {
  try {
    const updated = await api(`/api/trips/${state.activeTrip.id}/share`, { method: 'PATCH', body: JSON.stringify({ publiclyVisible: false }) });
    syncTrip(updated); toast('Trip is private again. Its public link no longer works.');
  } catch (error) { toast(error.message); }
}

async function deleteTrip() {
  if (!state.activeTrip || !confirm(`Delete “${state.activeTrip.title}” and its itinerary?`)) return;
  try {
    await api(`/api/trips/${state.activeTrip.id}`, { method: 'DELETE' });
    state.activeTrip = null; await loadDashboard(); showScreen('dashboard'); toast('Trip removed.');
  } catch (error) { toast(error.message); }
}

function openActivityDialog() {
  const trip = state.activeTrip; if (!trip || !state.canEdit) return;
  const select = $('#activityDay');
  select.innerHTML = Array.from({ length: tripDays(trip) }, (_, i) => `<option value="${i + 1}">Day ${i + 1}</option>`).join('');
  $('#activityForm').reset(); $('#activityForm').elements.cost.value = 0; $('#activityDialog').showModal();
}

async function removeActivity(id) {
  try {
    const trip = await api(`/api/trips/${state.activeTrip.id}/itinerary/${id}`, { method: 'DELETE' });
    syncTrip(trip); toast('Plan removed from your itinerary.');
  } catch (error) { toast(error.message); }
}

function syncTrip(trip) {
  state.activeTrip = trip;
  state.trips = state.trips.map(item => item.id === trip.id ? trip : item);
  renderStats(); renderTrips(); renderTripHeader(trip); renderItinerary(trip); renderBudget(trip);
}

$('#authToggle').addEventListener('click', () => setAuthMode(state.authMode === 'login' ? 'signup' : 'login'));
$('#authForm').addEventListener('submit', async event => {
  event.preventDefault(); $('#authError').textContent = '';
  const username = $('#authUsername').value.trim(), password = $('#authPassword').value;
  try {
    const result = await api(`/api/auth/${state.authMode}`, { method: 'POST', body: JSON.stringify({ username, password }) });
    state.currentUser = result.user; state.csrfToken = result.csrfToken; updateHeader();
    showScreen('dashboard'); await loadDashboard();
  } catch (error) { $('#authError').textContent = error.message; }
});

$('#logoutButton').addEventListener('click', async () => {
  try { await api('/api/auth/logout', { method: 'POST', body: '{}' }); } catch (_) {}
  state.currentUser = null; state.csrfToken = null; state.trips = []; state.activeTrip = null; updateHeader();
  try { const auth = await api('/api/auth/me'); state.csrfToken = auth.csrfToken; } catch (_) {}
  setAuthMode('login'); showScreen('auth');
});

$('#newTripButton').addEventListener('click', () => openTripDialog());
$('#newTripButtonSecondary').addEventListener('click', () => openTripDialog());
$('#backButton').addEventListener('click', () => { if (state.currentUser) { showScreen('dashboard'); loadDashboard(); } else { setAuthMode('login'); showScreen('auth'); } });
$('#addActivityButton').addEventListener('click', openActivityDialog);

$('#tripForm').addEventListener('submit', async event => {
  event.preventDefault(); const form = event.currentTarget; const data = Object.fromEntries(new FormData(form));
  data.plannedBudget = Number(data.plannedBudget); data.publiclyVisible = data.publiclyVisible === 'true';
  ['transportBudget', 'accommodationBudget', 'foodBudget', 'activitiesBudget', 'otherBudget'].forEach(key => data[key] = Number(data[key] || 0));
  if (data.endDate < data.startDate) { toast('Return date must be after departure.'); return; }
  try {
    const trip = await api('/api/trips', { method: 'POST', body: JSON.stringify(data) });
    $('#tripDialog').close(); await loadDashboard(); openTrip(trip.id);
    if (trip.publiclyVisible) await copyLink(trip); else toast('Your private trip is ready to plan.');
  } catch (error) { toast(error.message); }
});

$('#activityForm').addEventListener('submit', async event => {
  event.preventDefault(); const data = Object.fromEntries(new FormData(event.currentTarget));
  data.dayNumber = Number(data.dayNumber); data.cost = Number(data.cost || 0); data.startTime ||= null;
  try {
    const trip = await api(`/api/trips/${state.activeTrip.id}/itinerary`, { method: 'POST', body: JSON.stringify(data) });
    $('#activityDialog').close(); syncTrip(trip); toast('Added to your itinerary.');
  } catch (error) { toast(error.message); }
});

$('#routeForm').addEventListener('submit', event => {
  event.preventDefault(); const origin = $('#routeFrom').value.trim(), destination = $('#routeTo').value.trim(), mode = $('#routeMode').value;
  const url = `https://www.google.com/maps/dir/?api=1&origin=${encodeURIComponent(origin)}&destination=${encodeURIComponent(destination)}&travelmode=${encodeURIComponent(mode)}`;
  window.open(url, '_blank', 'noopener,noreferrer');
});

$('#reviewForm').addEventListener('submit', async event => {
  event.preventDefault(); const data = Object.fromEntries(new FormData(event.currentTarget));
  data.destination = state.activeTrip.destination; data.rating = Number(data.rating);
  try { await api('/api/reviews', { method: 'POST', body: JSON.stringify(data) }); event.currentTarget.reset(); await loadReviews(data.destination); toast('Thanks for sharing a helpful travel note.'); }
  catch (error) { toast(error.message); }
});

$('#inviteForm').addEventListener('submit', async event => {
  event.preventDefault();
  const username = $('#inviteUsername').value.trim();
  try {
    await api(`/api/trips/${state.activeTrip.id}/invitations`, { method: 'POST', body: JSON.stringify({ username }) });
    $('#inviteUsername').value = '';
    await loadCollaboration(state.activeTrip);
    toast(`Invitation sent to @${username}.`);
  } catch (error) { toast(error.message); }
});

$('#expenseForm').addEventListener('submit', async event => {
  event.preventDefault();
  const data = {
    description: $('#expenseDescription').value.trim(),
    category: $('#expenseCategory').value,
    amount: Number($('#expenseAmount').value)
  };
  try {
    const summary = await api(`/api/trips/${state.activeTrip.id}/expenses`, { method: 'POST', body: JSON.stringify(data) });
    event.currentTarget.reset(); renderExpenses(summary); toast('Expense added and split equally across the trip crew.');
  } catch (error) { toast(error.message); }
});

$('#destinationSearch').addEventListener('input', event => {
  const query = event.currentTarget.value.trim().toLowerCase();
  renderDestinations(state.destinations.filter(place => `${place.name} ${place.country} ${place.description}`.toLowerCase().includes(query)));
});
$$('[data-close]').forEach(button => button.addEventListener('click', () => $(`#${button.dataset.close}`).close()));
document.addEventListener('click', event => { if (event.target.matches('[data-create-trip]')) openTripDialog(); });

async function startApp() {
  try {
    const auth = await api('/api/auth/me');
    state.currentUser = auth.user; state.csrfToken = auth.csrfToken; updateHeader();
    $('#mainNav').classList.toggle('hidden', !state.currentUser);
    const sharedToken = new URLSearchParams(location.search).get('share');
    if (sharedToken) { await openSharedTrip(sharedToken); return; }
    if (state.currentUser) { showScreen('dashboard'); await loadDashboard(); }
    else { setAuthMode('login'); showScreen('auth'); }
  } catch (_) { setAuthMode('login'); showScreen('auth'); }
}

startApp();
