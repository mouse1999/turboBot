import axios from 'axios';

const API_BASE_URL = 'http://localhost:8080/api/v1';

const api = axios.create({
    baseURL: API_BASE_URL,
    timeout: 5000,
    headers: {
        'Content-Type': 'application/json',
    },
});

// Add response interceptor for error handling
api.interceptors.response.use(
    (response) => response.data,
    (error) => {
        console.error('API Error:', error);
        throw error;
    }
);

export const arbitrageApi = {
    // Get arbitrage opportunities
    getArbitrages: () => api.get('/arbitrage'),

    // Place a bet
    placeBet: (arbitrageId, amount) =>
        api.post('/bets/place', { arbitrageId, amount }),

    // Get user's betting history
    getBettingHistory: () => api.get('/bets/history'),

    // Get available bet amounts (could be from config)
    getBetAmounts: () =>
        Promise.resolve([100000, 70000, 50000, 20000, 10000, 5000, 1000]),
};

export default api;