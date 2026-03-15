# Real-Time Event Processing and Automation System for Live Sport Arbing

This project is a real-time event processing system designed to identify and capitalize on live sports arbitrage opportunities. It specifically targets the Nigerian market, integrating with major local bookmakers such as **MSport**, **SportyBet**, and **Bet9ja**. By leveraging a powerful combination of Spring Boot, Playwright for web automation, and a robust, multi-threaded architecture, the system ensures high-speed data ingestion and automated execution.

This system is engineered to:

- Ingest real-time live event data from the `breaking-bet.com` API.
- Identify profitable arbitrage opportunities across **MSport**, **SportyBet**, and **Bet9ja**.
- Automate interactions with these web-based platforms to execute bets instantly.
- Provide a comprehensive and scalable solution for real-time data analysis and automated decision-making.

The project is built with a focus on clean code, scalability, and maintainability, making it an excellent showcase of modern Java development practices.

### Key Features

- **Real-Time Event Processing**: Ingests and processes a high volume of real-time data from multiple sources.
- **Arbitrage Opportunity Detection**: Implements a sophisticated algorithm to identify profitable opportunities.
- **Automated Web Interaction**: Uses Playwright to automate interactions with web platforms, ensuring fast and reliable execution.
- **Multi-Threaded Architecture**: Employs a robust, multi-threaded design to handle multiple tasks concurrently, maximizing performance.
- **Scalable and Maintainable**: Built with Spring Boot, following best practices for building scalable and maintainable applications.
- **Comprehensive Logging and Monitoring**: Includes extensive logging and monitoring to ensure system health and performance.
- **RESTful API**: Provides a RESTful API for monitoring and managing the system.

### Technologies Used

- **Backend**: Java 17, Spring Boot 4.0.1
- **Web Automation**: Playwright 1.57.0
- **HTTP Client**: OkHttp 4.12.0
- **API Documentation**: SpringDoc OpenAPI 2.6.0
- **Frontend**: Node.js 20.10.0, React
- **Database**: H2 (Runtime only)
- **Build Tool**: Gradle

### Architecture

The system is designed with a modular, multi-layered architecture:

- **Ingestion Layer**: Responsible for ingesting data from external APIs.
- **Processing Layer**: Contains the core logic for identifying arbitrage opportunities.
- **Orchestration Layer**: Manages the workflow, dispatching tasks to the appropriate workers.
- **Execution Layer**: Interacts with web platforms to execute trades.
- **Persistence Layer**: Manages data persistence using Spring Data JPA.

### Configuration

The application is configured using `src/main/resources/application.yml`. You must update this file with your credentials and preferences before running the application.

#### Bookmaker Credentials & Settings

Configure credentials and browser behavior (headless mode) for each supported platform.

```yaml
msport:
  username: "YOUR_USERNAME"
  password: "YOUR_PASSWORD"
  headless: true  # Set to false to see the browser UI

sporty:
  username: "YOUR_USERNAME"
  password: "YOUR_PASSWORD"
  headless: true

bet9ja:
  username: "YOUR_USERNAME"
  password: "YOUR_PASSWORD"
  headless: false
```

#### Arbitrage Logic

Control how the bot identifies and acts on opportunities.

```yaml
arb:
  polling:
    enabled: true
    interval:
      ms: 5000       # How often to check for new opportunities
    freshness:
      seconds: 10    # Max age of data to consider
    min:
      profit: 1.5    # Minimum profit percentage to trigger action
    bookmakers: SPORTYBET,MSPORT,BET9JA
  total:
    budget: 100      # Total budget per arbitrage opportunity
```

#### Data Source (Breaking-Bet)

Configure the connection to the data provider.

```yaml
breaking-bet:
  bearer:
    token: "YOUR_JWT_TOKEN"  # Token for authenticating with the API
  api:
    prematch:
      url: "https://arbs.prematch.api.breaking-bet.com/v1/users/..."
    live:
      url: "https://arbs.live.api.breaking-bet.com/v1/users/..."
```

### How It Works

1. **Data Ingestion**: The `IngestionService` fetches real-time data from the `breaking-bet.com` API.
2. **Opportunity Identification**: The system processes the ingested data to identify potential arbitrage opportunities.
3. **Task Orchestration**: The `Orchestrator` creates and manages tasks for each identified opportunity, dispatching them to the appropriate workers.
4. **Automated Execution**: The workers use Playwright to interact with the web platforms, executing the necessary actions to capitalize on the opportunity.
5. **Monitoring and Logging**: The system provides real-time monitoring and logging of all activities, ensuring transparency and traceability.

### Setup and Running

To get the project up and running, follow these steps:

1. **Clone the repository**:

   ```bash
   git clone <repository-url>
   ```
2. **Navigate to the project directory**:

   ```bash
   cd turboBot
   ```
3. **Build the project**:

   ```bash
   ./gradlew build
   ```
4. **Run the application**:

   ```bash
   ./gradlew bootRun
   ```

The application will start, and you can access the API at `http://localhost:8085`.

#### Starting the Workflow

1. Once the application is running, open your web browser and navigate to `http://localhost:8085/arbitrage`.
2. Click on the **menu** button to reveal the controls.
3. Click on **Start Orchestrator** to initiate the automated workflow. The system will then begin to fetch data and process opportunities according to your configuration.

### Codebase Maturity & Future Improvements

While the core functionality of the system is robust and fully operational, the project is currently in an active phase of refinement. The codebase demonstrates a functional implementation of complex real-time processing and automation logic.

Key areas identified for future refactoring and optimization (Technical Debt) include:

- **Authentication Automation**: Currently, some initial authentication steps involve manual intervention to capture tokens. Future iterations will fully automate this process using headless browser scripts.
- **Component Decoupling**: Further separation of concerns between the browser automation logic (Playwright) and the HTTP client (OkHttp) to enhance testability and modularity.
- **Enhanced Error Recovery**: Implementing more granular retry mechanisms and circuit breakers for external API interactions.
- **Logging Standardization**: Transitioning from custom logging formats to a structured logging approach for better integration with centralized monitoring tools.

This project serves as a practical demonstration of integrating diverse technologies to solve a real-time problem, prioritizing functionality and performance while acknowledging the continuous cycle of software improvement.
