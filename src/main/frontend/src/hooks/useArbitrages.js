import { useState, useEffect, useCallback, useRef } from 'react';
import { arbitrageApi } from '../services/api';
import { POLLING_INTERVAL } from '../services/constants';

const useArbitrages = () => {  // ← Removed initialData parameter
    const [arbitrages, setArbitrages] = useState([]); // ← Always start with empty array
    const [loading, setLoading] = useState(true); // ← Start as true for initial load
    const [error, setError] = useState(null);
    const intervalRef = useRef(null);

    const fetchArbitrages = useCallback(async () => {
        try {
            setLoading(true);
            // In production, use the actual API:
            // const data = await arbitrageApi.getArbitrages();
            // setArbitrages(data.opportunities || []);

            // For demo, use mock data with simulated updates
            const mockData = {
                success: true,
                count: 3,
                opportunities: [
                    {
                        id: 1,
                        externalId: "arb_abc123xyz789",
                        eventId: "event_456",
                        sport: "Football",
                        sportId: 1,
                        leagueName: "Premier League",
                        country: "England",
                        homeTeam: "Manchester United",
                        awayTeam: "Liverpool",
                        matchStartTime: new Date(Date.now() + 24 * 60 * 60 * 1000).toISOString(),
                        isLive: false,
                        matchProgress: null,
                        marketType: "2-Way Market",
                        profitPercentage: 5.45 + (Math.random() * 0.5 - 0.25),
                        roiPercentage: 2.72,
                        status: "ACTIVE",
                        confidenceScore: 92.50,
                        createdAt: new Date().toISOString(),
                        lastCheckedAt: new Date().toISOString(),
                        outcomes: [
                            {
                                id: 1,
                                bookmakerId: 10,
                                bookmakerName: "Bet365",
                                outcomeName: "Home Win",
                                odds: 2.10 + (Math.random() * 0.1 - 0.05),
                                previousOdds: 2.05,
                                stake: 52.38,
                                subEventId: "sub_789"
                            },
                            {
                                id: 2,
                                bookmakerId: 25,
                                bookmakerName: "Pinnacle",
                                outcomeName: "Away Win",
                                odds: 2.15 + (Math.random() * 0.1 - 0.05),
                                previousOdds: 2.12,
                                stake: 47.62,
                                subEventId: "sub_790"
                            }
                        ]
                    },
                    {
                        id: 2,
                        externalId: "arb_def456uvw012",
                        eventId: "event_789",
                        sport: "Basketball",
                        sportId: 2,
                        leagueName: "NBA",
                        country: "USA",
                        homeTeam: "Los Angeles Lakers",
                        awayTeam: "Boston Celtics",
                        matchStartTime: new Date(Date.now() - 2 * 60 * 60 * 1000).toISOString(),
                        isLive: true,
                        matchProgress: "Q2 - " + Math.floor(Math.random() * 10) + ":" + Math.floor(Math.random() * 60).toString().padStart(2, '0'),
                        marketType: "2-Way Market",
                        profitPercentage: 3.20 + (Math.random() * 0.3 - 0.15),
                        roiPercentage: 1.60,
                        status: "ACTIVE",
                        confidenceScore: 78.00,
                        createdAt: new Date(Date.now() - 3 * 60 * 60 * 1000).toISOString(),
                        lastCheckedAt: new Date().toISOString(),
                        outcomes: [
                            {
                                id: 3,
                                bookmakerId: 15,
                                bookmakerName: "William Hill",
                                outcomeName: "Lakers Win",
                                odds: 1.95 + (Math.random() * 0.05 - 0.025),
                                previousOdds: 1.90,
                                stake: 51.28,
                                subEventId: "sub_123"
                            },
                            {
                                id: 4,
                                bookmakerId: 20,
                                bookmakerName: "Betway",
                                outcomeName: "Celtics Win",
                                odds: 2.05 + (Math.random() * 0.05 - 0.025),
                                previousOdds: 2.10,
                                stake: 48.72,
                                subEventId: "sub_124"
                            }
                        ]
                    },
                    {
                        id: 3,
                        externalId: "arb_ghi789rst345",
                        eventId: "event_012",
                        sport: "Tennis",
                        sportId: 3,
                        leagueName: "ATP Tour",
                        country: "Australia",
                        homeTeam: "Novak Djokovic",
                        awayTeam: "Rafael Nadal",
                        matchStartTime: new Date(Date.now() + 48 * 60 * 60 * 1000).toISOString(),
                        isLive: false,
                        matchProgress: null,
                        marketType: "2-Way Market",
                        profitPercentage: 2.80 + (Math.random() * 0.2 - 0.1),
                        roiPercentage: 1.40,
                        status: "ACTIVE",
                        confidenceScore: 85.00,
                        createdAt: new Date(Date.now() - 6 * 60 * 60 * 1000).toISOString(),
                        lastCheckedAt: new Date().toISOString(),
                        outcomes: [
                            {
                                id: 5,
                                bookmakerId: 30,
                                bookmakerName: "1xBet",
                                outcomeName: "Djokovic Win",
                                odds: 1.85 + (Math.random() * 0.05 - 0.025),
                                previousOdds: 1.82,
                                stake: 54.05,
                                subEventId: "sub_456"
                            },

                            {
                                id: 6,
                                bookmakerId: 35,
                                bookmakerName: "Unibet",
                                outcomeName: "Nadal Win",
                                odds: 2.20 + (Math.random() * 0.05 - 0.025),
                                previousOdds: 2.25,
                                stake: 45.95,
                                subEventId: "sub_457"
                            }
                        ]
                    }
                ]
            };

            setArbitrages(mockData.opportunities || []); // ← Ensure it's always an array
            setError(null);
        } catch (err) {
            console.error('Error fetching arbitrages:', err);
            setError('Failed to fetch arbitrage opportunities');
            setArbitrages([]); // ← Set empty array on error
        } finally {
            setLoading(false);
        }
    }, []);

    useEffect(() => {
        // Initial fetch
        fetchArbitrages();

        // Set up polling interval (every 2 seconds)
        intervalRef.current = setInterval(() => {
            fetchArbitrages();
        }, POLLING_INTERVAL);

        // Cleanup
        return () => {
            if (intervalRef.current) {
                clearInterval(intervalRef.current);
            }
        };
    }, [fetchArbitrages]);

    const refresh = useCallback(async () => {
        console.log('🔄 Manual refresh triggered');
        await fetchArbitrages();
        console.log('✅ Manual refresh completed');
    }, [fetchArbitrages]);

    return {
        arbitrages,
        loading,
        error,
        refresh,
    };
};

export default useArbitrages;