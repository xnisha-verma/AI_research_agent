/* ============================================================
   AI RESEARCH AGENT — FRONTEND
   Connects to Spring Boot backend at localhost:8080
   ============================================================ */

let rawApiBase = import.meta.env.VITE_API_BASE || 'http://localhost:8080/api';
if (!rawApiBase.endsWith('/api') && !rawApiBase.endsWith('/api/')) {
    if (rawApiBase.endsWith('/')) {
        rawApiBase = rawApiBase.slice(0, -1);
    }
    rawApiBase += '/api';
}
const API_BASE = rawApiBase;

// ---- State ----
let currentPage = 'dashboard';
let allTrends = [];

// ---- DOM Ready ----
document.addEventListener('DOMContentLoaded', () => {
    initLanding();
});

// ============================================================
// LANDING PAGE
// ============================================================

function initLanding() {
    drawGridCanvas();
    initContribGrid();
    initSnapObserver();
    initSlideDots();
    initFlashcardObserver();

    // Enter dashboard buttons
    document.getElementById('hero-enter-dashboard').addEventListener('click', enterDashboard);
    document.getElementById('enter-dashboard-bottom').addEventListener('click', enterDashboard);
}

function enterDashboard() {
    document.getElementById('landing').style.display = 'none';
    document.getElementById('slide-dots').style.display = 'none';
    const app = document.getElementById('app');
    app.classList.remove('app-hidden');
    app.style.display = 'flex';
    initNavigation();
    initRunButton();
    initFilters();
    loadDashboard();

    // Back button
    document.getElementById('back-to-landing').addEventListener('click', () => {
        app.classList.add('app-hidden');
        app.style.display = 'none';
        document.getElementById('landing').style.display = '';
        document.getElementById('slide-dots').style.display = '';
    });
}

function drawGridCanvas() {
    const canvas = document.getElementById('grid-canvas');
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    const resize = () => {
        canvas.width = window.innerWidth;
        canvas.height = window.innerHeight;
        const gap = 28;
        ctx.clearRect(0, 0, canvas.width, canvas.height);
        ctx.fillStyle = 'rgba(90, 90, 114, 0.25)';
        for (let x = gap; x < canvas.width; x += gap) {
            for (let y = gap; y < canvas.height; y += gap) {
                ctx.beginPath();
                ctx.arc(x, y, 0.8, 0, Math.PI * 2);
                ctx.fill();
            }
        }
    };
    resize();
    window.addEventListener('resize', resize);
}

function initContribGrid() {
    const grid = document.getElementById('contrib-grid');
    if (!grid) return;
    const levels = ['', 'l1', 'l2', 'l3', 'l4'];
    for (let i = 0; i < 364; i++) {
        const cell = document.createElement('div');
        cell.className = 'contrib-cell';
        if (Math.random() > 0.55) {
            const w = [1, 1, 1, 2, 2, 3, 4];
            cell.classList.add(levels[w[Math.floor(Math.random() * w.length)]]);
        }
        grid.appendChild(cell);
    }
}

function initSnapObserver() {
    const slides = document.querySelectorAll('.snap-slide');
    const dots = document.querySelectorAll('.slide-dot');
    const observer = new IntersectionObserver((entries) => {
        entries.forEach(entry => {
            if (entry.isIntersecting) {
                const idx = Array.from(slides).indexOf(entry.target);
                dots.forEach((d, i) => d.classList.toggle('active', i === idx));
            }
        });
    }, { root: document.getElementById('snap-container'), threshold: 0.6 });
    slides.forEach(s => observer.observe(s));
}

function initSlideDots() {
    const container = document.getElementById('snap-container');
    document.querySelectorAll('.slide-dot').forEach(dot => {
        dot.addEventListener('click', () => {
            const idx = parseInt(dot.dataset.slide);
            const target = document.querySelectorAll('.snap-slide')[idx];
            if (target) target.scrollIntoView({ behavior: 'smooth' });
        });
    });
}

function initFlashcardObserver() {
    const cards = document.querySelectorAll('.flashcard');
    const observer = new IntersectionObserver((entries) => {
        entries.forEach(entry => {
            if (entry.isIntersecting) {
                const delay = parseInt(entry.target.dataset.step) * 120;
                setTimeout(() => entry.target.classList.add('visible'), delay);
            }
        });
    }, { threshold: 0.3 });
    cards.forEach(c => observer.observe(c));
}

// ============================================================
// NAVIGATION
// ============================================================

function initNavigation() {
    document.querySelectorAll('.nav-link').forEach(link => {
        link.addEventListener('click', (e) => {
            e.preventDefault();
            const page = link.dataset.page;
            navigateTo(page);
        });
    });

    document.getElementById('link-view-all-trends').addEventListener('click', (e) => {
        e.preventDefault();
        navigateTo('trends');
    });

    // Platform card click handlers
    document.getElementById('platform-reddit').addEventListener('click', () => {
        navigateToPlatformScrape('REDDIT');
    });
    document.getElementById('platform-hackernews').addEventListener('click', () => {
        navigateToPlatformScrape('HACKERNEWS');
    });
    document.getElementById('platform-producthunt').addEventListener('click', () => {
        navigateToPlatformScrape('PRODUCTHUNT');
    });
}

function navigateToPlatformScrape(platform) {
    // Navigate to scrape status page
    navigateTo('scrape-status');
    
    // Set the filter button active
    document.querySelectorAll('[data-platform]').forEach(b => {
        if (b.dataset.platform === platform) {
            b.classList.add('active');
        } else {
            b.classList.remove('active');
        }
    });
    
    // Load posts for that platform
    loadScrapedPosts(platform);
}

function navigateTo(page) {
    currentPage = page;

    // Update nav links
    document.querySelectorAll('.nav-link').forEach(l => l.classList.remove('active'));
    document.querySelector(`[data-page="${page}"]`)?.classList.add('active');

    // Update pages
    document.querySelectorAll('.page').forEach(p => p.classList.remove('active'));
    document.getElementById(`page-${page}`)?.classList.add('active');

    // Update header
    const titles = {
        'dashboard': ['Dashboard', 'AI-powered trend detection across Reddit, HN & Product Hunt'],
        'trends': ['Trends', 'Detected trends from scraped posts'],
        'scrape-status': ['Scrape Status', 'All scraped posts across platforms']
    };
    const [title, subtitle] = titles[page] || ['Dashboard', ''];
    document.getElementById('page-title').textContent = title;
    document.getElementById('page-subtitle').textContent = subtitle;

    // Load page-specific data
    if (page === 'trends') loadAllTrends();
    if (page === 'scrape-status') loadScrapedPosts();
}

// ============================================================
// RUN RESEARCH CYCLE
// ============================================================

function initRunButton() {
    const btn = document.getElementById('btn-run-cycle');
    btn.addEventListener('click', async () => {
        btn.classList.add('loading');
        btn.disabled = true;
        btn.innerHTML = `
            <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor"><polygon points="5 3 19 12 5 21 5 3"/></svg>
            Running...
        `;
        showToast('Research cycle started...', 'info');

        try {
            const res = await fetch(`${API_BASE}/scrape/run`, { method: 'POST' });
            if (res.status === 409) {
                const data = await res.json();
                showToast(data.error || 'A scrape cycle is already in progress.', 'warning');
                return;
            }
            if (!res.ok) throw new Error(`HTTP ${res.status}`);
            const data = await res.json();
            showToast(`Cycle complete! ${data.postAnyalzed || 0} posts analyzed.`, 'success');
            loadDashboard();
        } catch (err) {
            showToast(`Cycle failed: ${err.message}`, 'error');
        } finally {
            btn.classList.remove('loading');
            btn.disabled = false;
            btn.innerHTML = `
                <svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor"><polygon points="5 3 19 12 5 21 5 3"/></svg>
                Run Research Cycle
            `;
        }
    });
}

// ============================================================
// DASHBOARD
// ============================================================

async function loadDashboard() {
    loadStats();
    loadTopTrends();
    loadRecentPosts();
}

async function loadStats() {
    try {
        const res = await fetch(`${API_BASE}/trends/stats`);
        if (!res.ok) throw new Error();
        const stats = await res.json();

        document.getElementById('stat-total-posts').textContent =
            formatNumber(stats.totalPosts || 0);
        document.getElementById('stat-total-trends').textContent =
            formatNumber(stats.totalTrends || 0);
        document.getElementById('count-reddit').textContent =
            formatNumber(stats.redditPosts || 0);
        document.getElementById('count-hackernews').textContent =
            formatNumber(stats.hackerNewsPosts || 0);
        document.getElementById('count-producthunt').textContent =
            formatNumber(stats.productHuntPosts || 0);

        if (stats.LastAnalysis) {
            document.getElementById('stat-last-analysis').textContent =
                formatTimeAgo(stats.LastAnalysis);
        }

        try {
            const postsRes = await fetch(`${API_BASE}/scrape/posts`);
            if (postsRes.ok) {
                const posts = await postsRes.json();
                const platforms = ['REDDIT', 'HACKERNEWS', 'PRODUCTHUNT'];
                const targetIds = {
                    'REDDIT': 'time-reddit',
                    'HACKERNEWS': 'time-hackernews',
                    'PRODUCTHUNT': 'time-producthunt'
                };
                platforms.forEach(p => {
                    const firstPost = posts.find(post => post.platform === p);
                    const el = document.getElementById(targetIds[p]);
                    if (el) {
                        if (firstPost && firstPost.scrapedAt) {
                            el.textContent = `Last scraped: ${formatTimeAgo(firstPost.scrapedAt)}`;
                        } else {
                            el.textContent = 'Never scraped';
                        }
                    }
                });
            }
        } catch (e) {
            console.error('Error fetching platform scrape times:', e);
        }
    } catch {
        // Stats will just show 0
    }
}

async function loadTopTrends() {
    const container = document.getElementById('trends-list');
    try {
        const res = await fetch(`${API_BASE}/trends/latest`);
        if (!res.ok) throw new Error();
        const trends = await res.json();

        if (!trends.length) {
            container.innerHTML = emptyState('search', 'No trends detected yet', 'Click "Run Research Cycle" to get started');
            return;
        }

        container.innerHTML = trends.slice(0, 8).map((t, i) => trendItemHtml(t, i + 1)).join('');
    } catch {
        container.innerHTML = emptyState('search', 'Could not load trends', 'Make sure the backend is running');
    }
}

async function loadRecentPosts() {
    const container = document.getElementById('posts-list');
    try {
        const res = await fetch(`${API_BASE}/scrape/posts`);
        if (!res.ok) throw new Error();
        const posts = await res.json();

        if (!posts.length) {
            container.innerHTML = emptyState('file', 'No posts scraped yet', 'Data will appear after the first scrape cycle');
            return;
        }

        container.innerHTML = posts.slice(0, 30).map(p => postItemHtml(p)).join('');
    } catch {
        container.innerHTML = emptyState('file', 'Could not load posts', 'Make sure the backend is running');
    }
}

// ============================================================
// TRENDS PAGE
// ============================================================

async function loadAllTrends(category = 'all') {
    const container = document.getElementById('all-trends-list');
    container.innerHTML = '<div class="empty-state"><p class="pulse">Loading trends...</p></div>';

    try {
        let url = `${API_BASE}/trends/latest`;
        if (category !== 'all') {
            url = `${API_BASE}/trends/category/${encodeURIComponent(category)}`;
        }

        const res = await fetch(url);
        if (!res.ok) throw new Error();
        const trends = await res.json();
        allTrends = trends;

        if (!trends.length) {
            container.innerHTML = emptyState('chart', 'No trends available', 'Run a research cycle to detect trends');
            return;
        }

        container.innerHTML = trends.map((t, i) => trendItemHtml(t, i + 1)).join('');
    } catch {
        container.innerHTML = emptyState('chart', 'Could not load trends', 'Check backend connectivity');
    }
}

function initFilters() {
    // Trend category filters
    document.querySelectorAll('[data-filter]').forEach(btn => {
        btn.addEventListener('click', () => {
            document.querySelectorAll('[data-filter]').forEach(b => b.classList.remove('active'));
            btn.classList.add('active');
            loadAllTrends(btn.dataset.filter);
        });
    });

    // Platform filters on scrape status page
    document.querySelectorAll('[data-platform]').forEach(btn => {
        btn.addEventListener('click', () => {
            document.querySelectorAll('[data-platform]').forEach(b => b.classList.remove('active'));
            btn.classList.add('active');
            loadScrapedPosts(btn.dataset.platform);
        });
    });
}

// ============================================================
// SCRAPE STATUS PAGE
// ============================================================

async function loadScrapedPosts(platform = 'all') {
    const container = document.getElementById('scraped-posts-list');
    container.innerHTML = '<div class="empty-state"><p class="pulse">Loading posts...</p></div>';

    try {
        let url = `${API_BASE}/scrape/posts`;
        if (platform !== 'all') {
            url += `?platform=${platform}`;
        }

        const res = await fetch(url);
        if (!res.ok) throw new Error();
        const posts = await res.json();

        if (!posts.length) {
            container.innerHTML = emptyState('file', 'No posts yet', 'Run a scrape cycle first');
            return;
        }

        container.innerHTML = posts.map(p => postItemHtml(p)).join('');
    } catch {
        container.innerHTML = emptyState('file', 'Could not load posts', 'Check backend connectivity');
    }
}

// ============================================================
// HTML TEMPLATES
// ============================================================

function trendItemHtml(trend, rank) {
    const score = trend.trendScore != null ? trend.trendScore.toFixed(1) : '—';
    return `
        <div class="trend-item">
            <div class="trend-rank">${rank}</div>
            <div class="trend-content">
                <div class="trend-topic">${escapeHtml(trend.topic || '')}</div>
                <div class="trend-summary">${escapeHtml(trend.summary || trend.reasoning || '')}</div>
                <div class="trend-meta">
                    ${trend.category ? `<span class="trend-badge badge-category">${escapeHtml(trend.category)}</span>` : ''}
                    ${trend.platform ? `<span class="trend-badge badge-platform">${formatPlatform(trend.platform)}</span>` : ''}
                    ${trend.mentionCount ? `<span class="trend-badge badge-mentions">${trend.mentionCount} mentions</span>` : ''}
                </div>
            </div>
            <div class="trend-score">
                <div class="trend-score-value">${score}</div>
                <div class="trend-score-label">Score</div>
            </div>
        </div>
    `;
}

function postItemHtml(post) {
    const platformClass = (post.platform || '').toLowerCase();
    const url = post.url || '#';
    return `
        <div class="post-item">
            <span class="post-platform-badge ${platformClass}">${formatPlatform(post.platform)}</span>
            <div class="post-content">
                <div class="post-title"><a href="${escapeHtml(url)}" target="_blank" rel="noopener">${escapeHtml(post.title || 'Untitled')}</a></div>
                <div class="post-meta">
                    ${post.author ? `<span class="post-meta-item">by ${escapeHtml(post.author)}</span>` : ''}
                    ${post.score ? `<span class="post-meta-item"><span class="post-score">▲ ${post.score}</span></span>` : ''}
                    ${post.commentCount ? `<span class="post-meta-item">💬 ${post.commentCount}</span>` : ''}
                    ${post.subReddit ? `<span class="post-meta-item">r/${escapeHtml(post.subReddit)}</span>` : ''}
                    ${post.scrapedAt ? `<span class="post-meta-item">${formatTimeAgo(post.scrapedAt)}</span>` : ''}
                </div>
            </div>
        </div>
    `;
}

function emptyState(icon, title, subtitle = '') {
    const icons = {
        search: '<circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/>',
        file: '<path d="M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8z"/><polyline points="14 2 14 8 20 8"/>',
        chart: '<polyline points="22 12 18 12 15 21 9 3 6 12 2 12"/>'
    };
    return `
        <div class="empty-state">
            <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="var(--text-muted)" stroke-width="1.5">${icons[icon] || icons.file}</svg>
            <p>${title}</p>
            ${subtitle ? `<span>${subtitle}</span>` : ''}
        </div>
    `;
}

// ============================================================
// UTILITIES
// ============================================================

function formatNumber(n) {
    if (n >= 1000000) return (n / 1000000).toFixed(1) + 'M';
    if (n >= 1000) return (n / 1000).toFixed(1) + 'K';
    return String(n);
}

function formatPlatform(p) {
    const map = {
        'REDDIT': 'Reddit',
        'HACKERNEWS': 'HN',
        'PRODUCTHUNT': 'PH'
    };
    return map[p] || p || '—';
}

function formatTimeAgo(dateStr) {
    if (!dateStr) return '';
    try {
        // Handle Java LocalDateTime format (array or string)
        let date;
        if (Array.isArray(dateStr)) {
            // [year, month, day, hour, min, sec, nano]
            date = new Date(dateStr[0], dateStr[1] - 1, dateStr[2], dateStr[3] || 0, dateStr[4] || 0, dateStr[5] || 0);
        } else {
            date = new Date(dateStr);
        }
        const now = new Date();
        const diffMs = now - date;
        const diffMins = Math.floor(diffMs / 60000);
        if (diffMins < 1) return 'just now';
        if (diffMins < 60) return `${diffMins}m ago`;
        const diffHours = Math.floor(diffMins / 60);
        if (diffHours < 24) return `${diffHours}h ago`;
        const diffDays = Math.floor(diffHours / 24);
        if (diffDays < 7) return `${diffDays}d ago`;
        return date.toLocaleDateString();
    } catch {
        return String(dateStr);
    }
}

function escapeHtml(str) {
    if (!str) return '';
    const div = document.createElement('div');
    div.textContent = str;
    return div.innerHTML;
}

// ============================================================
// TOAST NOTIFICATIONS
// ============================================================

function showToast(message, type = 'info') {
    const container = document.getElementById('toast-container');
    const icons = { success: '✓', error: '✕', info: 'ℹ' };
    const toast = document.createElement('div');
    toast.className = `toast ${type}`;
    toast.innerHTML = `
        <span class="toast-icon">${icons[type] || 'ℹ'}</span>
        <span>${escapeHtml(message)}</span>
    `;
    container.appendChild(toast);
    setTimeout(() => toast.remove(), 4000);
}
