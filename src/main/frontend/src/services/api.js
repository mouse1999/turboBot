import axios from 'axios';

const api = axios.create({
    baseURL: '/api/v1',
    timeout: 30000, // Increased to 30 seconds
    headers: {
        'Content-Type': 'application/json',
    },
});

// Request interceptor for debugging
api.interceptors.request.use(
    (config) => {
        console.log('🚀 API Request:', {
            method: config.method,
            url: config.baseURL + config.url,
            fullUrl: window.location.origin + config.baseURL + config.url,
        });
        return config;
    },
    (error) => {
        console.error('❌ Request Error:', error);
        return Promise.reject(error);
    }
);

// Response interceptor
api.interceptors.response.use(
    (response) => {
        console.log('✅ API Response:', {
            url: response.config.url,
            status: response.status,
            data: response.data,
            dataType: typeof response.data,
        });

        // Ensure response.data exists and has the expected structure
        if (!response.data) {
            console.warn('⚠️ Empty response data, returning default structure');
            return { success: true, opportunities: [], count: 0 };
        }

        return response.data;
    },
    (error) => {
        console.error('❌ API Error:', {
            message: error.message,
            code: error.code,
            url: error.config?.url,
            status: error.response?.status,
            data: error.response?.data,
        });

        // Provide more helpful error messages
        if (error.code === 'ECONNABORTED') {
            throw new Error('Request timeout - the server took too long to respond');
        } else if (error.response) {
            // Server responded with error status
            throw new Error(error.response.data?.message || `Server error: ${error.response.status}`);
        } else if (error.request) {
            // Request made but no response
            throw new Error('No response from server - check if backend is running');
        } else {
            throw error;
        }
    }
);

export const arbitrageApi = {
    // Get all arbitrage opportunities
    getArbitrages: () => api.get('/arbitrage'),

    /**
     * Place a bet by queueing an arbitrage opportunity with custom stakes
     *
     * @param {string|number} arbitrageId - External ID (string) or database ID (number)
     * @param {Object} stakes - Stakes for each bookmaker. Format: { BET365: 476.19, BETWAY: 285.71, ... }
     * @param {Object} options - Additional options (optional)
     * @returns {Promise} API response with queued arbitrage details
     *
     * Example usage:
     * arbitrageApi.placeBet('ARB-2025-001', { BET365: 476.19, BETWAY: 285.71, SPORTYBET: 250.00 })
     * arbitrageApi.placeBet(12345, { BET365: 500, BETWAY: 300 }, { validateAllStakes: false })
     */
    placeBet: (arbitrageId, stakes, options = {}) => {
        const isExternalId = typeof arbitrageId === 'string';

        const payload = {
            ...(isExternalId ? { externalId: arbitrageId } : { id: arbitrageId }),
            stakes: stakes,
            validateAllStakes: options.validateAllStakes !== undefined ? options.validateAllStakes : true
        };

        return api.post('/api/orchestrator/queue-with-stakes', payload);
    },

    /**
     * Alternative: Place bet with total amount that gets distributed across bookmakers
     * Automatically calculates optimal stakes based on odds
     *
     * @param {string|number} arbitrageId - External ID or database ID
     * @param {number} totalAmount - Total amount to bet across all bookmakers
     * @param {Array} outcomes - Array of outcomes with odds: [{ bookmaker: 'BET365', odds: 2.10 }, ...]
     * @returns {Promise} API response
     */
    placeBetWithAmount: (arbitrageId, totalAmount, outcomes) => {
        // Calculate optimal stakes
        const inverseOddsSum = outcomes.reduce((sum, o) => sum + (1 / o.odds), 0);

        const stakes = {};
        outcomes.forEach(outcome => {
            const stake = (totalAmount / outcome.odds) / inverseOddsSum;
            stakes[outcome.bookmaker] = parseFloat(stake.toFixed(2));
        });

        const isExternalId = typeof arbitrageId === 'string';

        return api.post('/api/orchestrator/queue-with-stakes', {
            ...(isExternalId ? { externalId: arbitrageId } : { id: arbitrageId }),
            stakes: stakes
        });
    },

    // Get betting history
    // getBettingHistory: () => api.get('/bets/history'),

    // Get predefined bet amounts
    getBetAmounts: () => Promise.resolve([100000, 70000, 50000, 20000, 10000, 5000, 1000]),

    // Orchestrator endpoints
    orchestrator: {
        // Get orchestrator status
        getStatus: () => api.get('/api/orchestrator/status'),

        // Get queue statistics
        getQueueStats: () => api.get('/api/orchestrator/queue-stats'),

        // Get registered workers
        getWorkers: () => api.get('/api/orchestrator/workers'),

        // Force cleanup queues
        forceCleanup: () => api.post('/api/orchestrator/cleanup'),

        // Health check
        health: () => api.get('/api/orchestrator/health'),

        // Queue by external ID (simple, no stake updates)
        queueByExternalId: (externalId) =>
            api.post('/api/orchestrator/queue/by-external-id', { externalId }),

        // Queue by ID (simple, no stake updates)
        queueById: (id) =>
            api.post('/api/orchestrator/queue/by-id', { id }),
    },

    // Polling service endpoints
    polling: {
        // Get polling status
        getStatus: () => api.get('/api/arb-polling/status'),

        // Get system status
        getSystemStatus: () => api.get('/api/arb-polling/system-status'),

        // Start polling
        start: () => api.post('/api/arb-polling/start'),

        // Stop polling
        stop: () => api.post('/api/arb-polling/stop'),

        // Restart polling
        restart: () => api.post('/api/arb-polling/restart'),

        // Trigger manual poll
        trigger: () => api.post('/api/arb-polling/trigger'),

        // Update allowed bookmakers
        updateBookmakers: (bookmakers) =>
            api.put('/api/arb-polling/bookmakers', bookmakers),

        // Update minimum profit
        updateMinProfit: (minProfit) =>
            api.put('/api/arb-polling/min-profit', { minProfit }),

        // Force cleanup
        cleanup: () => api.post('/api/arb-polling/cleanup'),

        // Health check
        health: () => api.get('/api/arb-polling/health'),
    }
};

export default api;