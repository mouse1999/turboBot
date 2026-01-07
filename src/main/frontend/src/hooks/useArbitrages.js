import { useState, useEffect, useCallback, useRef } from 'react';
import { arbitrageApi } from '../services/api';
import { POLLING_INTERVAL } from '../services/constants';

const useArbitrages = () => {
    const [arbitrages, setArbitrages] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [isPolling, setIsPolling] = useState(false);
    const intervalRef = useRef(null);
    const isMountedRef = useRef(true);

    const fetchArbitrages = useCallback(async (isInitial = false) => {
        console.log(`📡 Fetching arbitrages (initial: ${isInitial})`);

        try {
            // Only set loading to true on initial load
            if (isInitial) {
                setLoading(true);
            } else {
                setIsPolling(true);
            }

            const data = await arbitrageApi.getArbitrages();
            console.log(`✅ API response: ${data.opportunities?.length || 0} opportunities`);
            console.log(`response body : ${data}`);

            // Only update if component is still mounted
            if (isMountedRef.current) {
                // Only update state if data actually changed
                setArbitrages(prevArbitrages => {
                    const newOpportunities = data.opportunities || [];
                    // Simple check - you can make this more sophisticated
                    if (JSON.stringify(prevArbitrages) === JSON.stringify(newOpportunities)) {
                        console.log('⏭️ Data unchanged, skipping state update');
                        return prevArbitrages; // Return same reference to prevent re-render
                    }
                    console.log('🔄 Data changed, updating state');
                    return newOpportunities;
                });
                setError(null);
            }
        } catch (err) {
            console.error('❌ Error fetching arbitrages:', err);
            if (isMountedRef.current) {
                setError('Failed to fetch arbitrage opportunities');
                setArbitrages([]);
            }
        } finally {
            if (isMountedRef.current) {
                if (isInitial) {
                    setLoading(false);
                } else {
                    setIsPolling(false);
                }
            }
        }
    }, []);

    useEffect(() => {
        console.log('🚀 useArbitrages mounted');
        isMountedRef.current = true;

        // Initial fetch
        fetchArbitrages(true);

        // Set up polling interval (every 2 seconds)
        intervalRef.current = setInterval(() => {
            fetchArbitrages(false);
        }, POLLING_INTERVAL);

        // Cleanup
        return () => {
            console.log('🧹 useArbitrages cleanup');
            isMountedRef.current = false;
            if (intervalRef.current) {
                clearInterval(intervalRef.current);
            }
        };
    }, [fetchArbitrages]);

    const refresh = useCallback(async () => {
        console.log('🔄 Manual refresh triggered');
        await fetchArbitrages(false);
        console.log('✅ Manual refresh completed');
    }, [fetchArbitrages]);

    return {
        arbitrages,
        loading,
        error,
        refresh,
        isPolling,
    };
};

export default useArbitrages;