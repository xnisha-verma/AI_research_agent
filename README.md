# AI Research Agent

An autonomous research agent that scrapes top posts from Reddit, Hacker News, and Product Hunt — then uses LLM-powered analysis to surface emerging tech trends in a real-time dashboard.

---

## ⚡ Features

### - Multi-Platform Scraping
Scrapes public RSS feeds from **Reddit** (r/technology, r/artificial, r/machinelearning), **Hacker News** (top stories), and **Product Hunt** (latest launches) in a single click.

### - LLM-Powered Trend Analysis
Posts are fed into **Groq's LLM** to extract trending topics, assign categories (AI/ML, Security, DevTools, SaaS, Hardware), and compute trend scores. Falls back to a keyword-based analyzer when the LLM is unavailable.

### - Persistent Dashboard
Trends and scraped posts persist across sessions in PostgreSQL. The dashboard always shows the latest data — even before a new cycle is triggered.

### - Automatic Data Pruning
Auto-deletes posts older than 3 days and retains only the 5 most recent analysis sessions. Keeps the database under **500MB** (Supabase free-tier).

### - Rate-Limit Resilience
Built-in exponential backoff with up to 3 retries per request. Handles HTTP 429 responses gracefully without crashing the cycle.

---

## 🏗️ Tech Stack

| Layer | Technology |
|-------|-----------|
| Backend | Spring Boot (Java 21), Maven |
| Database | PostgreSQL (Supabase) |
| Frontend | Vanilla JS, Vite |
| LLM | Groq API (Llama 3) |
| Hosting | Render (Docker), Netlify (Static) |

---

## 📁 Project Structure

```
├── src/                    # Spring Boot backend
│   ├── controller/         # REST API endpoints (/api/scrape, /api/trends)
│   ├── scraper/            # Platform scrapers (Reddit, HN, ProductHunt)
│   ├── service/            # Orchestrator, LLM analysis, trend service
│   ├── model/              # JPA entities (ScrapedPost, TrendTopic, TrendAnalysis)
│   └── config/             # CORS, WebConfig
├── frontend/               # Vite + Vanilla JS dashboard
│   ├── index.html          # Landing page + dashboard UI
│   ├── main.js             # API calls, navigation, landing logic
│   └── style.css           # Design system + landing styles
└── Dockerfile              # Multi-stage build (Maven → JRE 21 Alpine)
```

---

## 🛠️ Local Development

### 1. Database
Ensure PostgreSQL is running with a database named `ai_agent`.

### 2. Backend
```bash
export DATABASE_URL=jdbc:postgresql://localhost:5432/ai_agent
export DB_USERNAME=postgres
export DB_PASSWORD=yourpassword
export GROQ_API_KEY=your_groq_api_key

mvn spring-boot:run
```

### 3. Frontend
```bash
cd frontend
npm install
npm run dev
```

---

## 🐳 Docker

```bash
docker build -t ai-research-backend .

docker run -p 8080:8080 \
  -e DATABASE_URL=jdbc:postgresql://host.docker.internal:5432/ai_agent \
  -e DB_USERNAME=postgres \
  -e DB_PASSWORD=yourpassword \
  -e GROQ_API_KEY=your_groq_api_key \
  ai-research-backend
```

---

## 🚀 Deployment
### Environment Variables

**Render (Backend):**
- `DATABASE_URL` — JDBC connection string with `?sslmode=require`
- `DB_USERNAME` — Supabase database user
- `DB_PASSWORD` — Supabase database password
- `GROQ_API_KEY` — Groq API key for LLM analysis

**Netlify (Frontend):**
- `VITE_API_BASE` — Render backend URL (auto-appends `/api`)

---

## 📡 API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/scrape/run` | Trigger a full research cycle |
| `GET` | `/api/scrape/posts` | Get all scraped posts |
| `GET` | `/api/scrape/posts?platform=REDDIT` | Filter posts by platform |
| `GET` | `/api/trends/latest` | Get latest trend analysis |
| `GET` | `/api/trends/stats` | Dashboard stats (counts, last analysis) |
| `GET` | `/api/trends/category/{category}` | Filter trends by category |
| `GET` | `/api/trends/platform/{platform}` | Filter trends by platform |
