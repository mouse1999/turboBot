import React, { useState, useCallback } from 'react';
import useArbitrages from '../hooks/useArbitrages';
import ArbitrageHeader from '../components/arbitrage/ArbitrageHeader';
import ArbitrageFilters from '../components/arbitrage/ArbitrageFilters';
import ArbitrageList from '../components/arbitrage/ArbitrageList';
import LoadingSpinner from '../components/common/LoadingSpinner';
import ErrorMessage from '../components/common/ErrorMessage';

const ArbitragePage = () => {
    const { arbitrages, loading, error, refresh } = useArbitrages(); // ← No parameter
    const [expandedCards, setExpandedCards] = useState({});
    const [filter, setFilter] = useState('all');

    const toggleExpanded = useCallback((id) => {
        setExpandedCards(prev => ({
            ...prev,
            [id]: !prev[id]
        }));
    }, []);

    const handlePlaceBet = useCallback((arbitrage, amount, outcomesWithStakes) => {
        const betAmount = (amount / 1000).toFixed(0) + 'k';

        // Create detailed bet summary
        const betSummary = outcomesWithStakes.map(outcome =>
            `${outcome.bookmakerName}: ₦${outcome.stake.toFixed(2)} @ ${outcome.odds.toFixed(2)}`
        ).join('\n');

        const expectedReturn = outcomesWithStakes[0]?.stake * outcomesWithStakes[0]?.odds || 0;
        const profit = expectedReturn - amount;

        alert(
            `Placing ₦${betAmount} bet for:\n` +
            `${arbitrage.homeTeam} vs ${arbitrage.awayTeam}\n\n` +
            `Stakes:\n${betSummary}\n\n` +
            `Total Stake: ₦${amount.toFixed(2)}\n` +
            `Expected Return: ₦${expectedReturn.toFixed(2)}\n` +
            `Profit: ₦${profit.toFixed(2)} (${arbitrage.profitPercentage.toFixed(2)}%)`
        );

        // In production, send this data to your backend API
        // await arbitrageApi.placeBet({
        //   arbitrageId: arbitrage.id,
        //   totalAmount: amount,
        //   outcomes: outcomesWithStakes
        // });
    }, []);

    const handleRefresh = useCallback(async () => {
        await refresh();
    }, [refresh]);

    const filteredArbitrages = arbitrages.filter(arb => {
        if (filter === 'live') return arb.isLive;
        if (filter === 'prematch') return !arb.isLive;
        return true;
    });

    if (loading && arbitrages.length === 0) {
        return (
            <div className="min-h-screen bg-gradient-to-br from-gray-50 to-gray-100 flex items-center justify-center p-4">
                <LoadingSpinner text="Loading arbitrage opportunities..." />
            </div>
        );
    }

    if (error && arbitrages.length === 0) {
        return (
            <div className="min-h-screen bg-gradient-to-br from-gray-50 to-gray-100 flex items-center justify-center p-4">
                <ErrorMessage message={error} onRetry={handleRefresh} />
            </div>
        );
    }

    return (
        <div className="min-h-screen bg-gradient-to-br from-gray-50 to-gray-100">
            {/* Header */}
            <div className="bg-white shadow-md border-b border-gray-200">
                <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-6">
                    <ArbitrageHeader
                        filteredCount={filteredArbitrages.length}
                        totalCount={arbitrages.length}
                        onRefresh={handleRefresh}
                        loading={loading}
                    />

                    {/* Filters */}
                    <div className="mt-6">
                        <ArbitrageFilters
                            filter={filter}
                            setFilter={setFilter}
                            arbitrages={arbitrages}
                        />
                    </div>
                </div>
            </div>

            {/* Content */}
            <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
                <ArbitrageList
                    arbitrages={filteredArbitrages}
                    expandedCards={expandedCards}
                    onToggleCard={toggleExpanded}
                    onPlaceBet={handlePlaceBet}
                />
            </div>

            {/* Footer */}
            <div className="bg-white border-t border-gray-200 py-4">
                <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
                    <p className="text-center text-sm text-gray-500">
                        Data refreshes every 2 seconds • {arbitrages.length} opportunities monitored
                    </p>
                </div>
            </div>
        </div>
    );
};

export default ArbitragePage;