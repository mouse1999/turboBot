import axios from 'axios';

const api = axios.create({
    baseURL: '/api/v1',
    timeout: 30000,
    headers: {
        'Content-Type': 'application/json',
    },
});

// Request interceptor for debugging
api.interceptors.request.use(
    (config) => {
        console.group('🚀 API REQUEST');
        console.log('Method:', config.method?.toUpperCase());
        console.log('URL:', config.baseURL + config.url);
        console.log('Full URL:', window.location.origin + config.baseURL + config.url);
        console.log('Headers:', config.headers);
        console.log('Request Data:', config.data);
        console.log('Request Data Type:', typeof config.data);

        // Parse and log if it's a string
        if (typeof config.data === 'string') {
            try {
                const parsed = JSON.parse(config.data);
                console.log('Parsed Request Data:', parsed);
            } catch (e) {
                console.log('Could not parse request data as JSON');
            }
        }
        console.groupEnd();

        return config;
    },
    (error) => {
        console.group('❌ REQUEST ERROR');
        console.error('Error:', error);
        console.error('Error Message:', error.message);
        console.groupEnd();
        return Promise.reject(error);
    }
);

// Response interceptor
api.interceptors.response.use(
    (response) => {
        console.group('✅ API RESPONSE - SUCCESS');
        console.log('URL:', response.config.url);
        console.log('Status:', response.status);
        console.log('Status Text:', response.statusText);
        console.log('Response Data:', response.data);
        console.log('Response Data Type:', typeof response.data);
        console.log('Response Headers:', response.headers);
        console.groupEnd();

        if (!response.data) {
            console.warn('⚠️ Empty response data, returning default structure');
            return { success: true, opportunities: [], count: 0 };
        }

        return response.data;
    },
    (error) => {
        console.group('❌ API RESPONSE - ERROR');
        console.error('Error Message:', error.message);
        console.error('Error Code:', error.code);
        console.error('Request URL:', error.config?.url);
        console.error('Request Method:', error.config?.method);
        console.error('Request Data:', error.config?.data);

        if (error.response) {
            console.error('Response Status:', error.response.status);
            console.error('Response Status Text:', error.response.statusText);
            console.error('Response Data:', error.response.data);
            console.error('Response Headers:', error.response.headers);

            // Log detailed error info if available
            if (error.response.data) {
                console.error('Server Error Message:', error.response.data.message);
                console.error('Server Error Details:', error.response.data.details);
                console.error('Server Error Code:', error.response.data.code);
                console.error('Full Server Response:', JSON.stringify(error.response.data, null, 2));
            }
        } else if (error.request) {
            console.error('Request was made but no response received');
            console.error('Request:', error.request);
        } else {
            console.error('Error setting up request:', error.message);
        }

        console.error('Full Error Object:', error);
        console.groupEnd();

        // Provide more helpful error messages
        if (error.code === 'ECONNABORTED') {
            throw new Error('Request timeout - the server took too long to respond');
        } else if (error.response) {
            const serverMessage = error.response.data?.message || error.response.data?.error;
            const detailMessage = error.response.data?.details ?
                `\nDetails: ${JSON.stringify(error.response.data.details)}` : '';

            throw new Error(
                serverMessage
                    ? `${serverMessage}${detailMessage}`
                    : `Server error: ${error.response.status}${detailMessage}`
            );
        } else if (error.request) {
            throw new Error('No response from server - check if backend is running');
        } else {
            throw error;
        }
    }
);

export const arbitrageApi = {
    getArbitrages: () => {
        console.log('📞 Calling: getArbitrages()');
        return api.get('/arbitrage');
    },

    placeBet: (arbitrageId, stakes, options = {}) => {
        console.group('📞 Calling: placeBet()');
        console.log('Arbitrage ID:', arbitrageId);
        console.log('Arbitrage ID Type:', typeof arbitrageId);
        console.log('Stakes:', stakes);
        console.log('Stakes Type:', typeof stakes);
        console.log('Options:', options);

        const isExternalId = typeof arbitrageId === 'string';
        console.log('Is External ID:', isExternalId);

        const payload = {
            ...(isExternalId ? { externalId: arbitrageId } : { id: arbitrageId }),
            stakes: stakes,
            validateAllStakes: options.validateAllStakes !== undefined ? options.validateAllStakes : true
        };

        console.log('Final Payload:', payload);
        console.log('Final Payload (JSON):', JSON.stringify(payload, null, 2));
        console.groupEnd();

        return api.post('/orchestrator/queue-with-stakes', payload);
    },

    placeBetWithAmount: (arbitrageId, totalAmount, outcomes) => {
        console.group('📞 Calling: placeBetWithAmount()');
        console.log('Arbitrage ID:', arbitrageId);
        console.log('Total Amount:', totalAmount);
        console.log('Outcomes:', outcomes);
        console.log('Outcomes Count:', outcomes?.length);

        // Calculate optimal stakes
        const inverseOddsSum = outcomes.reduce((sum, o) => {
            console.log(`Processing outcome: ${o.bookmakerName}, odds: ${o.odds}, 1/odds: ${1/o.odds}`);
            return sum + (1 / o.odds);
        }, 0);

        console.log('Total Inverse Odds Sum:', inverseOddsSum);

        const stakes = {};
        outcomes.forEach(outcome => {
            const stake = (totalAmount / outcome.odds) / inverseOddsSum;
            stakes[outcome.bookmakerName] = parseFloat(stake.toFixed(2));
            console.log(`Stake for ${outcome.bookmakerName}: ${stakes[outcome.bookmakerName]}`);
        });

        console.log('Calculated Stakes:', stakes);

        const isExternalId = typeof arbitrageId === 'string';
        console.log('Is External ID:', isExternalId);

        const payload = {
            ...(isExternalId ? { externalId: arbitrageId } : { id: arbitrageId }),
            stakes: stakes,
            validateAllStakes: true
        };

        console.log('Final Payload:', payload);
        console.log('Final Payload (JSON):', JSON.stringify(payload, null, 2));
        console.groupEnd();

        return api.post('/orchestrator/queue-with-stakes', payload);
    },

    getBetResults: (arbitrageId) => {
        console.log('📞 Calling: getBetResults()', arbitrageId);
        return api.get(`/arbitrages/${arbitrageId}/bet-results`);
    },

    getBetAmounts: () => {
        console.log('📞 Calling: getBetAmounts()');
        return Promise.resolve([100000, 70000, 50000, 20000, 10000, 5000, 1000]);
    },

    orchestrator: {
        getStatus: () => {
            console.log('📞 Calling: orchestrator.getStatus()');
            return api.get('/orchestrator/status');
        },

        startOrchestrator: () => {
            console.log('📞 Calling: orchestrator.startOrchestrator()');
            return api.post('/orchestrator/start');
        },

        getQueueStats: () => {
            console.log('📞 Calling: orchestrator.getQueueStats()');
            return api.get('/orchestrator/queue-stats');
        },

        getWorkers: () => {
            console.log('📞 Calling: orchestrator.getWorkers()');
            return api.get('/orchestrator/workers');
        },

        forceCleanup: () => {
            console.log('📞 Calling: orchestrator.forceCleanup()');
            return api.post('/orchestrator/cleanup');
        },

        health: () => {
            console.log('📞 Calling: orchestrator.health()');
            return api.get('/orchestrator/health');
        },

        queueByExternalId: (externalId) => {
            console.log('📞 Calling: orchestrator.queueByExternalId()', externalId);
            return api.post('/orchestrator/queue/by-external-id', { externalId });
        },

        queueById: (id) => {
            console.log('📞 Calling: orchestrator.queueById()', id);
            return api.post('/orchestrator/queue/by-id', { id });
        },
    },

    polling: {
        getStatus: () => {
            console.log('📞 Calling: polling.getStatus()');
            return api.get('/arb-polling/status');
        },

        getSystemStatus: () => {
            console.log('📞 Calling: polling.getSystemStatus()');
            return api.get('/arb-polling/system-status');
        },

        start: () => {
            console.log('📞 Calling: polling.start()');
            return api.post('/arb-polling/start');
        },

        stop: () => {
            console.log('📞 Calling: polling.stop()');
            return api.post('/arb-polling/stop');
        },

        restart: () => {
            console.log('📞 Calling: polling.restart()');
            return api.post('/arb-polling/restart');
        },

        trigger: () => {
            console.log('📞 Calling: polling.trigger()');
            return api.post('/arb-polling/trigger');
        },

        updateBookmakers: (bookmakers) => {
            console.log('📞 Calling: polling.updateBookmakers()', bookmakers);
            return api.put('/arb-polling/bookmakers', bookmakers);
        },

        updateMinProfit: (minProfit) => {
            console.log('📞 Calling: polling.updateMinProfit()', minProfit);
            return api.put('/arb-polling/min-profit', { minProfit });
        },

        cleanup: () => {
            console.log('📞 Calling: polling.cleanup()');
            return api.post('/arb-polling/cleanup');
        },

        health: () => {
            console.log('📞 Calling: polling.health()');
            return api.get('/arb-polling/health');
        },
    }
};

export default api;