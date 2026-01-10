import React, { useState, useCallback, useMemo } from 'react';
import useArbitrages from '../hooks/useArbitrages';
import ArbitrageHeader from '../components/arbitrage/ArbitrageHeader';
import ArbitrageFilters from '../components/arbitrage/ArbitrageFilters';
import ArbitrageList from '../components/arbitrage/ArbitrageList';
import LoadingSpinner from '../components/common/LoadingSpinner';
import ErrorMessage from '../components/common/ErrorMessage';
import { arbitrageApi } from "../services/api.js";

// Bet Result Modal Component
const BetResultModal = ({ isOpen, onClose, betResult, betError }) => {
    if (!isOpen) return null;

    const isSuccess = !!betResult && !betError;

    return (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50 p-4 animate-fadeIn">
            <div className="bg-white rounded-xl shadow-2xl max-w-lg w-full max-h-[90vh] overflow-y-auto animate-slideUp">
                {/* Header */}
                <div className={`p-6 border-b ${isSuccess ? 'bg-green-50' : 'bg-red-50'}`}>
                    <div className="flex items-center gap-3">
                        {isSuccess ? (
                            <div className="w-12 h-12 bg-green-500 rounded-full flex items-center justify-center flex-shrink-0">
                                <svg className="w-6 h-6 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
                                </svg>
                            </div>
                        ) : (
                            <div className="w-12 h-12 bg-red-500 rounded-full flex items-center justify-center flex-shrink-0">
                                <svg className="w-6 h-6 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                                </svg>
                            </div>
                        )}
                        <h2 className={`text-2xl font-bold ${isSuccess ? 'text-green-900' : 'text-red-900'}`}>
                            {isSuccess ? 'Bet Placed Successfully!' : 'Bet Failed'}
                        </h2>
                    </div>
                </div>

                {/* Body */}
                <div className="p-6">
                    {isSuccess && betResult ? (
                        <div className="space-y-4">
                            {/* Arbitrage Details */}
                            <div className="bg-gray-50 p-4 rounded-lg">
                                <h3 className="font-semibold text-gray-700 mb-3">Arbitrage Details</h3>
                                <div className="space-y-2 text-sm">
                                    <div className="flex justify-between">
                                        <span className="text-gray-600">Arbitrage ID:</span>
                                        <span className="font-medium text-gray-900">{betResult.arbitrage?.externalId || '—'}</span>
                                    </div>
                                    <div className="flex justify-between">
                                        <span className="text-gray-600">Status:</span>
                                        <span className="font-semibold text-blue-600">{betResult.arbitrage?.status}</span>
                                    </div>
                                    <div className="flex justify-between">
                                        <span className="text-gray-600">Profit:</span>
                                        <span className="font-bold text-green-600">{betResult.arbitrage?.profitPercentage}%</span>
                                    </div>
                                </div>
                            </div>

                            {/* Financial Summary */}
                            <div className="bg-blue-50 p-4 rounded-lg border border-blue-200">
                                <h3 className="font-semibold text-blue-900 mb-3">Financial Summary</h3>
                                <div className="space-y-2 text-sm">
                                    <div className="flex justify-between">
                                        <span className="text-blue-700">Total Stake:</span>
                                        <span className="font-bold text-blue-900">₦{betResult.totalStake?.toFixed(2)}</span>
                                    </div>
                                    <div className="flex justify-between">
                                        <span className="text-blue-700">Expected Profit:</span>
                                        <span className="font-bold text-green-600">
                                            ₦{((betResult.totalStake * betResult.arbitrage?.profitPercentage) / 100)?.toFixed(2)}
                                        </span>
                                    </div>
                                </div>
                            </div>

                            {/* Stakes Applied */}
                            <div className="bg-gray-50 p-4 rounded-lg">
                                <h3 className="font-semibold text-gray-700 mb-3">Stakes Applied</h3>
                                <div className="space-y-2">
                                    {Object.entries(betResult.stakes || {}).map(([bookmaker, stake]) => (
                                        <div key={bookmaker} className="flex justify-between items-center text-sm">
                                            <span className="text-gray-600 font-medium">{bookmaker}</span>
                                            <span className="font-semibold text-gray-900">₦{stake.toFixed(2)}</span>
                                        </div>
                                    ))}
                                </div>
                            </div>

                            {/* Queue Stats */}
                            {betResult.queueStats && (
                                <div className="bg-purple-50 p-4 rounded-lg border border-purple-200">
                                    <h3 className="font-semibold text-purple-900 mb-2 text-sm">Queue Status</h3>
                                    <div className="text-xs text-purple-700 space-y-1">
                                        <p>Queue Size: {betResult.queueStats.arbQueueSize}/{betResult.queueStats.workerQueueCount}</p>
                                        <p>Active Tasks: {betResult.queueStats.totalLegTasks}</p>
                                    </div>
                                </div>
                            )}

                            {/* Success Message */}
                            <div className="bg-green-100 border border-green-300 p-3 rounded-lg">
                                <p className="text-green-800 text-sm text-center font-medium">
                                    ✓ Your bet has been queued for processing
                                </p>
                            </div>
                        </div>
                    ) : (
                        <div className="space-y-4">
                            {/* Error Message */}
                            <div className="bg-red-50 border border-red-200 p-4 rounded-lg">
                                <p className="text-red-800 whitespace-pre-line leading-relaxed">{betError}</p>
                            </div>

                            {/* Help Text */}
                            <div className="bg-gray-50 p-4 rounded-lg">
                                <h3 className="font-semibold text-gray-700 mb-2 text-sm">What to do next?</h3>
                                <ul className="text-sm text-gray-600 space-y-1 list-disc list-inside">
                                    <li>Check if the arbitrage opportunity is still active</li>
                                    <li>Wait a moment and try again if the queue is full</li>
                                    <li>Refresh the page to see updated opportunities</li>
                                    <li>Contact support if the problem persists</li>
                                </ul>
                            </div>
                        </div>
                    )}
                </div>

                {/* Footer */}
                <div className="p-6 border-t bg-gray-50 flex justify-end gap-3">
                    <button
                        onClick={onClose}
                        className="px-6 py-3 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors font-medium shadow-sm hover:shadow-md"
                    >
                        Close
                    </button>
                </div>
            </div>
        </div>
    );
};

// Main ArbitragePage Component
const ArbitragePage = () => {
    const { arbitrages, loading, error, refresh, isPolling } = useArbitrages();

    // UI states
    const [expandedCards, setExpandedCards] = useState({});
    const [filter, setFilter] = useState('all');

    // Bet modal states
    const [betResult, setBetResult] = useState(null);
    const [betError, setBetError] = useState(null);
    const [showBetModal, setShowBetModal] = useState(false);
    const [placingBet, setPlacingBet] = useState(false);

    const toggleExpanded = useCallback((id) => {
        setExpandedCards(prev => ({
            ...prev,
            [id]: !prev[id]
        }));
    }, []);

    const handlePlaceBet = useCallback(async (arbitrage, amount, outcomesWithStakes) => {
        const betAmount = (amount / 1000).toFixed(0) + 'k';

        // Create detailed bet summary
        const betSummary = outcomesWithStakes.map(outcome =>
            `${outcome.bookmakerName}: ₦${outcome.stake.toFixed(2)} @ ${outcome.odds.toFixed(2)}`
        ).join('\n');

        const expectedReturn = outcomesWithStakes[0]?.stake * outcomesWithStakes[0]?.odds || 0;
        const profit = expectedReturn - amount;

        // Show confirmation dialog
        // const confirmMessage =
        //     `Placing ₦${betAmount} bet for:\n` +
        //     `${arbitrage.homeTeam} vs ${arbitrage.awayTeam}\n\n` +
        //     `Stakes:\n${betSummary}\n\n` +
        //     `Total Stake: ₦${amount.toFixed(2)}\n` +
        //     `Expected Return: ₦${expectedReturn.toFixed(2)}\n` +
        //     `Profit: ₦${profit.toFixed(2)} (${arbitrage.profitPercentage.toFixed(2)}%)\n\n` +
        //     `Do you want to proceed?`;

        // if (!confirm(confirmMessage)) {
        //     return; // User cancelled
        // }

        setPlacingBet(true);
        setBetError(null);
        setBetResult(null);

        try {
            // Transform outcomesWithStakes to the format expected by the API
            const outcomes = outcomesWithStakes.map(outcome => ({
                bookmaker: outcome.bookmakerName, // BookMaker enum (e.g., 'BET365', 'BETWAY')
                odds: outcome.odds
            }));

            // Use arbitrage externalId or id
            const arbitrageId = arbitrage.externalId || arbitrage.id;

            console.log('🎯 Placing bet:', {
                arbitrageId,
                amount,
                outcomes
            });

            // Call the API
            const response = await arbitrageApi.placeBetWithAmount(
                arbitrageId,
                amount,
                outcomes
            );

            console.log('✅ Bet placed successfully:', response);

            // Show success in modal
            setBetResult(response);
            setBetError(null);
            setShowBetModal(true);

            // Optionally refresh arbitrages list after short delay
            setTimeout(() => {
                refresh();
            }, 2000);

            return response;

        } catch (err) {
            console.error('❌ Error placing bet:', err);

            const errorMessage = err.message || 'Failed to place bet';
            let detailedError = errorMessage;

            // Handle specific error cases
            if (errorMessage.includes('Queue full') || err.response?.data?.error === 'Queue full') {
                detailedError =
                    `⏳ Queue is Currently Full\n\n` +
                    `The betting system is processing another arbitrage opportunity.\n` +
                    `Please try again in a few moments.\n\n` +
                    `Current queue: ${err.response?.data?.queueStats?.arbQueueSize || 1}/1 slots filled`;
            } else if (errorMessage.includes('not found') || err.response?.data?.error === 'Arbitrage not found') {
                detailedError =
                    `❌ Arbitrage Not Found\n\n` +
                    `The arbitrage opportunity may have expired or been removed.\n` +
                    `Please refresh and try another opportunity.`;
            } else if (err.response?.data?.error === 'Missing stakes') {
                const missing = err.response.data.missingBookmakers?.join(', ') || 'unknown';
                detailedError =
                    `❌ Missing Stakes\n\n` +
                    `Stakes could not be calculated for: ${missing}\n` +
                    `Please check the arbitrage details.`;
            } else if (errorMessage.includes('Invalid arbitrage')) {
                detailedError =
                    `❌ Invalid Arbitrage\n\n` +
                    `This arbitrage opportunity has no valid outcomes.\n` +
                    `It may have been modified or removed.`;
            }

            // Show error in modal
            setBetError(detailedError);
            setBetResult(null);
            setShowBetModal(true);

            throw err;

        } finally {
            setPlacingBet(false);
        }
    }, [refresh]);

    const handleCloseModal = useCallback(() => {
        setShowBetModal(false);
        setBetResult(null);
        setBetError(null);
    }, []);

    const handleRefresh = useCallback(async () => {
        await refresh();
    }, [refresh]);

    // Memoize filtered arbitrages to prevent recalculation on every render
    const filteredArbitrages = useMemo(() => {
        return arbitrages.filter(arb => {
            if (filter === 'live') return arb.isLive;
            if (filter === 'prematch') return !arb.isLive;
            return true;
        });
    }, [arbitrages, filter]);

    // Only show full-page loading on initial load
    if (loading && arbitrages.length === 0) {
        return (
            <div className="min-h-screen bg-gradient-to-br from-gray-50 to-gray-100 flex items-center justify-center p-4">
                <LoadingSpinner text="Loading arbitrage opportunities..." />
            </div>
        );
    }

    // Show error page if there's an error
    if (error) {
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
                        isPolling={isPolling}
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

            {/* Footer with polling indicator */}
            <div className="bg-white border-t border-gray-200 py-4">
                <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
                    <div className="flex items-center justify-center space-x-2">
                        {/* Subtle polling indicator */}
                        {isPolling && (
                            <div className="flex items-center space-x-1 text-blue-500">
                                <div className="w-2 h-2 bg-blue-500 rounded-full animate-pulse"></div>
                                <span className="text-xs">Updating</span>
                            </div>
                        )}

                        <p className="text-center text-sm text-gray-500">
                            {!isPolling && '•'} Data refreshes every 2 seconds • {arbitrages.length} opportunities monitored
                        </p>
                    </div>
                </div>
            </div>

            {/* Bet Result Modal */}
            <BetResultModal
                isOpen={showBetModal}
                onClose={handleCloseModal}
                betResult={betResult}
                betError={betError}
            />

            {/* Global Loading Overlay for Placing Bet */}
            {placingBet && (
                <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
                    <div className="bg-white rounded-lg p-6 shadow-xl">
                        <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600 mx-auto mb-4"></div>
                        <p className="text-gray-700 font-medium">Placing your bet...</p>
                        <p className="text-gray-500 text-sm mt-2">Please wait...</p>
                    </div>
                </div>
            )}
        </div>
    );
};

export default ArbitragePage;