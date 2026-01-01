ArbBot — Automated Arbitrage Betting Bot (Java + Playwright)
ArbBot is a personal, fully automated arbitrage betting system designed to detect and execute surebets across two bookmakers — MSport and SportyBet — using real-time opportunities from Breaking-Bet.
Built in Java 21 with Playwright for Java, the bot runs 24/7, logs in to both bookmakers, simulates realistic human behavior to avoid detection, validates odds before placing bets, and only executes when profit is guaranteed.
This is a private, educational/research project — use at your own risk and in compliance with all bookmaker terms of service and local laws.
Key Features

Fully Autonomous Execution
Detects arbitrage opportunities, calculates stakes, and places both legs automatically — no manual intervention needed.
Dual Bookmaker Support
Dedicated browser contexts for MSport and SportyBet running in parallel.
Advanced Anti-Detection
Realistic human simulation: random mouse movements, scrolling, gaussian-distributed delays, slow typing, and natural interaction patterns.
Real-Time Odds Validation
Never places a bet if odds have drifted beyond a configurable tolerance (default ±0.05).
In-Memory Coordination
Uses H2 in-memory database + thread-safe queues to dispatch tasks and track status across windows.
Robust Error Handling
Automatic retries (max 3), screenshots on failure, detailed logging, and graceful recovery.
Live Console Monitoring
Real-time dashboard showing active arbs, profit %, and per-leg status.
Session Persistence
Saves and reuses browser storage state (cookies, localStorage) to stay logged in across restarts.

Tech Stack

Java 21 — Modern, performant, with virtual threads support
Playwright for Java — Reliable browser automation with stealth capabilities
H2 Database — Lightweight in-memory storage for bet tasks and status
Lombok — Clean, concise code
SLF4J + Logback — Structured logging
Maven — Build and dependency management

Architecture Highlights

Ingestion Layer → Polls Breaking-Bet API
Transformation Layer → Converts raw arbs into structured BetLegTask objects
Dispatcher → Routes tasks to correct bookmaker queue
Betting Windows → Two parallel Playwright threads (one per bookie)
Monitoring → Central console view of all active opportunities