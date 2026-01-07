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
    getArbitrages: () => api.get('/arbitrage'),
    placeBet: (arbitrageId, amount) => api.post('/bets/place', { arbitrageId, amount }),
    getBettingHistory: () => api.get('/bets/history'),
    getBetAmounts: () => Promise.resolve([100000, 70000, 50000, 20000, 10000, 5000, 1000]),
};

export default api;