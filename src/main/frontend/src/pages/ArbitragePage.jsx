import React, { useState, useCallback, useMemo, useEffect } from 'react';
import useArbitrages from '../hooks/useArbitrages';
import ArbitrageHeader from '../components/arbitrage/ArbitrageHeader';
import ArbitrageFilters from '../components/arbitrage/ArbitrageFilters';
import ArbitrageList from '../components/arbitrage/ArbitrageList';
import LoadingSpinner from '../components/common/LoadingSpinner';
import ErrorMessage from '../components/common/ErrorMessage';
import { arbitrageApi } from "../services/api.js";
import {
    TrendingUp,
    Clock,
    Zap,
    Shield,
    AlertTriangle,
    CheckCircle2,
    XCircle,
    BarChart3,
    RefreshCw,
    Target,
    Wallet,
    ChevronRight,
    Sparkles
} from 'lucide-react';

// Bet Result Modal Component
const BetResultModal = ({ isOpen, onClose, betResult, betError }) => {
    if (!isOpen) return null;

    const isSuccess = !!betResult && !betError;

    return (
        <div className="fixed inset-0 z-50">
            {/* Backdrop */}
            <div
                className="absolute inset-0 bg-black/50 backdrop-blur-sm transition-opacity"
                onClick={onClose}
            />

            {/* Modal */}
            <div className="relative min-h-screen flex items-center justify-center p-4">
                <div
                    className="relative bg-gradient-to-br from-white to-gray-50 rounded-2xl shadow-2xl max-w-lg w-full overflow-hidden animate-scale-in"
                    onClick={e => e.stopPropagation()}
                >
                    {/* Header with gradient */}
                    <div className={`relative overflow-hidden ${isSuccess ? 'bg-gradient-to-r from-emerald-500 to-green-500' : 'bg-gradient-to-r from-rose-500 to-red-500'}`}>
                        <div className="absolute inset-0 bg-black/10" />
                        <div className="relative p-6">
                            <div className="flex items-center gap-4">
                                <div className={`p-3 rounded-xl ${isSuccess ? 'bg-white/20' : 'bg-white/20'} backdrop-blur-sm`}>
                                    {isSuccess ? (
                                        <CheckCircle2 className="w-8 h-8 text-white" />
                                    ) : (
                                        <XCircle className="w-8 h-8 text-white" />
                                    )}
                                </div>
                                <div>
                                    <h2 className="text-2xl font-bold text-white">
                                        {isSuccess ? 'Bet Placed Successfully!' : 'Bet Failed'}
                                    </h2>
                                    <p className="text-white/90 mt-1">
                                        {isSuccess ? 'Your arbitrage bet has been queued' : 'There was an issue placing your bet'}
                                    </p>
                                </div>
                            </div>
                        </div>
                    </div>

                    {/* Body */}
                    <div className="p-6 space-y-4">
                        {isSuccess && betResult ? (
                            <>
                                {/* Arbitrage Summary */}
                                <div className="bg-gradient-to-r from-blue-50 to-indigo-50 rounded-xl p-5 border border-blue-100">
                                    <div className="flex items-center justify-between mb-3">
                                        <h3 className="font-semibold text-gray-900 flex items-center gap-2">
                                            <Target className="w-4 h-4" />
                                            Arbitrage Summary
                                        </h3>
                                        <span className="px-3 py-1 bg-green-100 text-green-800 text-xs font-medium rounded-full">
                                            ID: {betResult.arbitrage?.externalId?.slice(0, 8) || '—'}
                                        </span>
                                    </div>
                                    <div className="grid grid-cols-2 gap-3">
                                        <div className="space-y-1">
                                            <p className="text-xs text-gray-500">Status</p>
                                            <p className="font-semibold text-blue-600">{betResult.arbitrage?.status}</p>
                                        </div>
                                        <div className="space-y-1">
                                            <p className="text-xs text-gray-500">Profit</p>
                                            <p className="font-bold text-green-600 text-lg">
                                                {betResult.arbitrage?.profitPercentage}%
                                            </p>
                                        </div>
                                    </div>
                                </div>

                                {/* Financial Overview */}
                                <div className="bg-gradient-to-r from-emerald-50 to-green-50 rounded-xl p-5 border border-emerald-100">
                                    <h3 className="font-semibold text-gray-900 mb-4 flex items-center gap-2">
                                        <Wallet className="w-4 h-4" />
                                        Financial Overview
                                    </h3>
                                    <div className="space-y-3">
                                        <div className="flex items-center justify-between">
                                            <div>
                                                <p className="text-sm text-gray-600">Total Stake</p>
                                                <p className="text-xs text-gray-500">Amount invested across all bookmakers</p>
                                            </div>
                                            <p className="text-xl font-bold text-gray-900">
                                                ₦{betResult.totalStake?.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
                                            </p>
                                        </div>
                                        <div className="h-px bg-gradient-to-r from-transparent via-gray-200 to-transparent" />
                                        <div className="flex items-center justify-between">
                                            <div>
                                                <p className="text-sm text-gray-600">Expected Profit</p>
                                                <p className="text-xs text-gray-500">Estimated return on investment</p>
                                            </div>
                                            <p className="text-xl font-bold text-green-600">
                                                ₦{((betResult.totalStake * betResult.arbitrage?.profitPercentage) / 100)?.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
                                            </p>
                                        </div>
                                    </div>
                                </div>

                                {/* Stakes Breakdown */}
                                <div className="rounded-xl border border-gray-200 overflow-hidden">
                                    <div className="bg-gray-50 px-5 py-3 border-b border-gray-200">
                                        <h3 className="font-semibold text-gray-900 flex items-center gap-2">
                                            <BarChart3 className="w-4 h-4" />
                                            Stakes Breakdown
                                        </h3>
                                    </div>
                                    <div className="divide-y divide-gray-100">
                                        {Object.entries(betResult.stakes || {}).map(([bookmaker, stake]) => (
                                            <div key={bookmaker} className="px-5 py-3 hover:bg-gray-50 transition-colors">
                                                <div className="flex items-center justify-between">
                                                    <div className="flex items-center gap-3">
                                                        <div className="w-8 h-8 rounded-lg bg-blue-100 flex items-center justify-center">
                                                            <span className="text-xs font-bold text-blue-600">
                                                                {bookmaker.slice(0, 2)}
                                                            </span>
                                                        </div>
                                                        <div>
                                                            <p className="font-medium text-gray-900">{bookmaker}</p>
                                                            <p className="text-xs text-gray-500">Bookmaker</p>
                                                        </div>
                                                    </div>
                                                    <div className="text-right">
                                                        <p className="font-semibold text-gray-900">
                                                            ₦{stake.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
                                                        </p>
                                                        <p className="text-xs text-gray-500">Stake</p>
                                                    </div>
                                                </div>
                                            </div>
                                        ))}
                                    </div>
                                </div>

                                {/* Queue Status */}
                                {betResult.queueStats && (
                                    <div className="bg-gradient-to-r from-violet-50 to-purple-50 rounded-xl p-5 border border-violet-100">
                                        <div className="flex items-center justify-between">
                                            <div>
                                                <h3 className="font-semibold text-gray-900 mb-1">Queue Status</h3>
                                                <p className="text-xs text-gray-600">Processing status</p>
                                            </div>
                                            <div className="text-right">
                                                <p className="text-sm font-semibold text-violet-700">
                                                    {betResult.queueStats.arbQueueSize}/{betResult.queueStats.workerQueueCount} slots
                                                </p>
                                                <p className="text-xs text-violet-600">
                                                    {betResult.queueStats.totalLegTasks} active tasks
                                                </p>
                                            </div>
                                        </div>
                                        <div className="mt-3 w-full bg-gray-200 rounded-full h-2">
                                            <div
                                                className="bg-gradient-to-r from-violet-500 to-purple-500 h-2 rounded-full transition-all duration-500"
                                                style={{
                                                    width: `${(betResult.queueStats.arbQueueSize / betResult.queueStats.workerQueueCount) * 100}%`
                                                }}
                                            />
                                        </div>
                                    </div>
                                )}

                                {/* Success Message */}
                                <div className="bg-gradient-to-r from-emerald-50 to-green-50 border border-emerald-200 rounded-xl p-4">
                                    <div className="flex items-start gap-3">
                                        <div className="p-2 bg-emerald-100 rounded-lg">
                                            <CheckCircle2 className="w-5 h-5 text-emerald-600" />
                                        </div>
                                        <div>
                                            <p className="font-medium text-emerald-900">Bet Queued Successfully</p>
                                            <p className="text-sm text-emerald-700 mt-1">
                                                Your arbitrage bet has been added to the processing queue. You can track its progress in your bets history.
                                            </p>
                                        </div>
                                    </div>
                                </div>
                            </>
                        ) : (
                            <>
                                {/* Error Details */}
                                <div className="bg-gradient-to-r from-rose-50 to-red-50 rounded-xl p-5 border border-rose-100">
                                    <div className="flex items-start gap-3">
                                        <div className="p-2 bg-rose-100 rounded-lg flex-shrink-0">
                                            <AlertTriangle className="w-5 h-5 text-rose-600" />
                                        </div>
                                        <div className="space-y-2">
                                            <h3 className="font-semibold text-rose-900">Bet Placement Failed</h3>
                                            <p className="text-rose-800 whitespace-pre-line leading-relaxed text-sm">
                                                {betError}
                                            </p>
                                        </div>
                                    </div>
                                </div>

                                {/* Troubleshooting Guide */}
                                <div className="rounded-xl border border-gray-200 overflow-hidden">
                                    <div className="bg-gray-50 px-5 py-3 border-b border-gray-200">
                                        <h3 className="font-semibold text-gray-900">Troubleshooting Guide</h3>
                                    </div>
                                    <div className="divide-y divide-gray-1s00">
                                        {[
                                            { text: "Check if the arbitrage opportunity is still active", icon: Clock },
                                            { text: "Wait a moment and try again if the queue is full", icon: RefreshCw },
                                            { text: "Refresh the page to see updated opportunities", icon: Zap },
                                            {
                                                text: "Contact the developer if the problem persists: kufreedward26@gmail.com",
                                                icon: Shield
                                            }
                                        ].map((item, index) => (
                                            <div key={index} className="px-5 py-3 hover:bg-gray-50 transition-colors">
                                                <div className="flex items-center gap-3">
                                                    <div className="p-2 bg-gray-100 rounded-lg">
                                                        <item.icon className="w-4 h-4 text-gray-600" />
                                                    </div>
                                                    <span className="text-sm text-gray-700">{item.text}</span>
                                                </div>
                                            </div>
                                        ))}
                                    </div>
                                </div>
                            </>
                        )}
                    </div>

                    {/* Footer */}
                    <div className="px-6 py-5 bg-gray-50 border-t border-gray-200">
                        <button
                            onClick={onClose}
                            className="w-full py-3 bg-gradient-to-r from-gray-900 to-black text-white rounded-xl hover:shadow-lg transition-all duration-300 font-medium flex items-center justify-center gap-2 group"
                        >
                            <span>Continue Exploring</span>
                            <ChevronRight className="w-4 h-4 group-hover:translate-x-1 transition-transform" />
                        </button>
                    </div>
                </div>
            </div>
        </div>
    );
};

// Quick Stats Bar Component
const QuickStatsBar = ({ arbitrages, isPolling }) => {
    const stats = useMemo(() => {
        const totalProfit = arbitrages.reduce((sum, arb) => sum + arb.profitPercentage, 0);
        const avgProfit = arbitrages.length > 0 ? totalProfit / arbitrages.length : 0;
        const liveCount = arbitrages.filter(arb => arb.isLive).length;
        const highProfitCount = arbitrages.filter(arb => arb.profitPercentage >= 5).length;

        return { avgProfit, liveCount, highProfitCount };
    }, [arbitrages]);

    return (
        <div className="grid grid-cols-1 md:grid-cols-3 gap-3 mb-6">
            <div className="bg-gradient-to-r from-blue-50 to-indigo-50 rounded-xl p-4 border border-blue-100">
                <div className="flex items-center justify-between">
                    <div>
                        <p className="text-sm text-gray-600">Avg. Profit</p>
                        <p className="text-2xl font-bold text-gray-900 mt-1">
                            {stats.avgProfit.toFixed(1)}%
                        </p>
                    </div>
                    <div className="p-3 bg-white rounded-lg shadow-sm">
                        <TrendingUp className="w-6 h-6 text-blue-600" />
                    </div>
                </div>
                <div className="mt-2 flex items-center gap-2">
                    <div className="w-full bg-blue-100 rounded-full h-2">
                        <div
                            className="bg-gradient-to-r from-blue-500 to-indigo-500 h-2 rounded-full"
                            style={{ width: `${Math.min(stats.avgProfit * 10, 100)}%` }}
                        />
                    </div>
                </div>
            </div>

            <div className="bg-gradient-to-r from-emerald-50 to-green-50 rounded-xl p-4 border border-emerald-100">
                <div className="flex items-center justify-between">
                    <div>
                        <p className="text-sm text-gray-600">Live Events</p>
                        <p className="text-2xl font-bold text-gray-900 mt-1">
                            {stats.liveCount}
                        </p>
                    </div>
                    <div className="p-3 bg-white rounded-lg shadow-sm">
                        <Zap className="w-6 h-6 text-emerald-600" />
                    </div>
                </div>
                <p className="text-xs text-emerald-700 mt-2">
                    {stats.liveCount > 0 ? 'Active betting opportunities' : 'No live events'}
                </p>
            </div>

            <div className="bg-gradient-to-r from-amber-50 to-orange-50 rounded-xl p-4 border border-amber-100">
                <div className="flex items-center justify-between">
                    <div>
                        <p className="text-sm text-gray-600">High Profit</p>
                        <p className="text-2xl font-bold text-gray-900 mt-1">
                            {stats.highProfitCount}
                        </p>
                    </div>
                    <div className="p-3 bg-white rounded-lg shadow-sm">
                        <Sparkles className="w-6 h-6 text-amber-600" />
                    </div>
                </div>
                <p className="text-xs text-amber-700 mt-2">
                    Opportunities with ≥5% profit
                </p>
            </div>
        </div>
    );
};

// Empty State Component
const EmptyState = ({ filter, onRefresh }) => {
    const messages = {
        all: "No arbitrage opportunities found. Try adjusting your filters or check back later.",
        live: "No live arbitrage opportunities at the moment. Prematch events may be available.",
        prematch: "No prematch arbitrage opportunities. Check live events or try refreshing."
    };

    return (
        <div className="text-center py-12 px-4">
            <div className="w-20 h-20 mx-auto mb-6 bg-gradient-to-r from-gray-100 to-gray-200 rounded-full flex items-center justify-center">
                <Target className="w-10 h-10 text-gray-400" />
            </div>
            <h3 className="text-lg font-semibold text-gray-900 mb-2">No Opportunities</h3>
            <p className="text-gray-600 max-w-md mx-auto mb-6">
                {messages[filter] || messages.all}
            </p>
            <button
                onClick={onRefresh}
                className="inline-flex items-center gap-2 px-6 py-3 bg-gradient-to-r from-blue-600 to-indigo-600 text-white rounded-xl hover:shadow-lg transition-all duration-300 font-medium"
            >
                <RefreshCw className="w-4 h-4" />
                Refresh Opportunities
            </button>
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

    // Animation state
    const [animateRefresh, setAnimateRefresh] = useState(false);

    const toggleExpanded = useCallback((id) => {
        setExpandedCards(prev => ({
            ...prev,
            [id]: !prev[id]
        }));
    }, []);

    const handlePlaceBet = useCallback(async (arbitrage, amount, outcomesWithStakes) => {
        // Show confirmation dialog (commented out but kept for reference)
        // const confirmMessage = `Placing ₦${(amount / 1000).toFixed(0)}k bet...\n\nDo you want to proceed?`;
        // if (!confirm(confirmMessage)) return;

        setPlacingBet(true);
        setBetError(null);
        setBetResult(null);

        try {
            // Transform outcomesWithStakes to the format expected by the API
            const outcomes = outcomesWithStakes.map(outcome => ({
                bookmaker: outcome.bookmakerName,
                odds: outcome.odds
            }));

            const arbitrageId = arbitrage.externalId || arbitrage.id;

            console.log('🎯 Placing bet:', {
                arbitrageId,
                amount,
                outcomes
            });

            const response = await arbitrageApi.placeBetWithAmount(
                arbitrageId,
                amount,
                outcomes
            );

            console.log('✅ Bet placed successfully:', response);

            // Show success with animation delay
            setTimeout(() => {
                setBetResult(response);
                setShowBetModal(true);
            }, 300);

            // Refresh arbitrages list after successful bet
            setTimeout(() => {
                refresh();
            }, 2000);

            return response;

        } catch (err) {
            console.error('❌ Error placing bet:', err);

            let detailedError = err.message || 'Failed to place bet';

            // Enhanced error handling
            if (err.response?.data?.error) {
                const errorData = err.response.data;
                switch (errorData.error) {
                    case 'Queue full':
                        detailedError = `⏳ Queue is Currently Full\n\nThe betting system is processing another arbitrage opportunity.\nPlease try again in a few moments.`;
                        break;
                    case 'Arbitrage not found':
                        detailedError = `❌ Arbitrage Not Found\n\nThe arbitrage opportunity may have expired or been removed.\nPlease refresh and try another opportunity.`;
                        break;
                    case 'Missing stakes':
                        const missing = errorData.missingBookmakers?.join(', ') || 'unknown';
                        detailedError = `❌ Missing Stakes\n\nStakes could not be calculated for: ${missing}\nPlease check the arbitrage details.`;
                        break;
                    default:
                        detailedError = errorData.error;
                }
            }

            setBetError(detailedError);
            setShowBetModal(true);

            throw err;

        } finally {
            setPlacingBet(false);
        }
    }, [refresh]);

    const handleCloseModal = useCallback(() => {
        setShowBetModal(false);
        setTimeout(() => {
            setBetResult(null);
            setBetError(null);
        }, 300);
    }, []);

    const handleRefresh = useCallback(async () => {
        setAnimateRefresh(true);
        await refresh();
        setTimeout(() => setAnimateRefresh(false), 1000);
    }, [refresh]);

    // Memoize filtered arbitrages
    const filteredArbitrages = useMemo(() => {
        return arbitrages.filter(arb => {
            if (filter === 'live') return arb.isLive;
            if (filter === 'prematch') return !arb.isLive;
            return true;
        }).sort((a, b) => b.profitPercentage - a.profitPercentage); // Sort by profit
    }, [arbitrages, filter]);

    // Show shimmer loading effect
    if (loading && arbitrages.length === 0) {
        return (
            <div className="min-h-screen bg-gradient-to-br from-gray-50 to-gray-100">
                <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
                    <div className="space-y-6">
                        {/* Header shimmer */}
                        <div className="bg-white rounded-2xl p-6 shadow-sm animate-pulse">
                            <div className="h-8 bg-gray-200 rounded-lg w-1/3 mb-4"></div>
                            <div className="h-4 bg-gray-200 rounded w-1/2"></div>
                        </div>

                        {/* Stats shimmer */}
                        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                            {[1, 2, 3].map(i => (
                                <div key={i} className="h-32 bg-white rounded-2xl shadow-sm animate-pulse"></div>
                            ))}
                        </div>

                        {/* Cards shimmer */}
                        <div className="space-y-4">
                            {[1, 2, 3].map(i => (
                                <div key={i} className="h-48 bg-white rounded-2xl shadow-sm animate-pulse"></div>
                            ))}
                        </div>
                    </div>
                </div>
            </div>
        );
    }

    // Show error page
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
            <div className="sticky top-0 z-10 bg-white/80 backdrop-blur-lg border-b border-gray-200">
                <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-6">
                    <ArbitrageHeader
                        filteredCount={filteredArbitrages.length}
                        totalCount={arbitrages.length}
                        onRefresh={handleRefresh}
                        loading={loading}
                        isPolling={isPolling}
                        animateRefresh={animateRefresh}
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

            {/* Main Content */}
            <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
                {/* Quick Stats */}
                <QuickStatsBar arbitrages={filteredArbitrages} isPolling={isPolling} />

                {/* Arbitrage List or Empty State */}
                {filteredArbitrages.length > 0 ? (
                    <ArbitrageList
                        arbitrages={filteredArbitrages}
                        expandedCards={expandedCards}
                        onToggleCard={toggleExpanded}
                        onPlaceBet={handlePlaceBet}
                    />
                ) : (
                    <EmptyState filter={filter} onRefresh={handleRefresh} />
                )}
            </div>

            {/* Floating Refresh Button */}
            {filteredArbitrages.length > 0 && (
                <button
                    onClick={handleRefresh}
                    disabled={loading}
                    className="fixed bottom-6 right-6 p-4 bg-gradient-to-r from-blue-600 to-indigo-600 text-white rounded-full shadow-xl hover:shadow-2xl transition-all duration-300 hover:scale-105 disabled:opacity-50 disabled:cursor-not-allowed group"
                >
                    <RefreshCw className={`w-6 h-6 ${animateRefresh ? 'animate-spin' : ''}`} />
                    <div className="absolute inset-0 rounded-full bg-white opacity-0 group-hover:opacity-10 transition-opacity" />
                </button>
            )}

            {/* Status Bar */}
            <div className="fixed bottom-0 left-0 right-0 bg-white/90 backdrop-blur-lg border-t border-gray-200 py-3">
                <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
                    <div className="flex items-center justify-between">
                        <div className="flex items-center gap-3">
                            <div className="flex items-center gap-2">
                                <div className={`w-2 h-2 rounded-full ${isPolling ? 'bg-green-500 animate-pulse' : 'bg-gray-400'}`} />
                                <span className="text-sm text-gray-600">
                                    {isPolling ? 'Live updates' : 'Manual refresh'}
                                </span>
                            </div>
                            <div className="h-4 w-px bg-gray-300" />
                            <span className="text-sm text-gray-600">
                                {filteredArbitrages.length} opportunities • {arbitrages.length} total
                            </span>
                        </div>
                        <div className="text-sm text-gray-500">
                            Refreshes every 2s • {new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                        </div>
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

            {/* Global Loading Overlay */}
            {placingBet && (
                <div className="fixed inset-0 z-50 bg-black/50 backdrop-blur-sm flex items-center justify-center">
                    <div className="bg-gradient-to-br from-white to-gray-50 rounded-2xl p-8 shadow-2xl animate-scale-in">
                        <div className="relative">
                            <div className="absolute inset-0 animate-ping bg-blue-500/20 rounded-full" />
                            <div className="w-16 h-16 border-4 border-blue-200 border-t-blue-600 rounded-full animate-spin" />
                        </div>
                        <div className="mt-6 text-center">
                            <p className="text-lg font-semibold text-gray-900">Placing Your Bet</p>
                            <p className="text-gray-600 mt-2">Processing arbitrage across bookmakers...</p>
                            <div className="mt-4 flex justify-center gap-2">
                                <div className="w-2 h-2 bg-blue-600 rounded-full animate-bounce" />
                                <div className="w-2 h-2 bg-blue-600 rounded-full animate-bounce" style={{ animationDelay: '0.1s' }} />
                                <div className="w-2 h-2 bg-blue-600 rounded-full animate-bounce" style={{ animationDelay: '0.2s' }} />
                            </div>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
};

export default ArbitragePage;