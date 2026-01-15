import React, { useState, useEffect } from 'react';
import {
    CheckCircle2,
    XCircle,
    Clock,
    Loader2,
    AlertTriangle,
    TrendingUp,
    X,
    Trophy,
    Target,
    Zap,
    Activity
} from 'lucide-react';
import {arbitrageApi} from "../../services/api.js";

// Hook to poll bet status
const useBetTracking = (arbitrageId, enabled) => {
    const [betResults, setBetResults] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);

    useEffect(() => {
        if (!enabled || !arbitrageId) return;

        let mounted = true;
        let pollCount = 0;
        const maxPolls = 30; // Stop after 30 attempts (60 seconds)

        const pollBetStatus = async () => {
            try {
                // Use arbitrageApi instead of fetch
                const data = await arbitrageApi.getBetResults(arbitrageId);

                if (mounted) {
                    if (data && Object.keys(data).length > 0) {
                        setBetResults(data);
                        setLoading(false);
                    } else if (pollCount >= maxPolls) {
                        setError('Bet results not available. Please check your bets history.');
                        setLoading(false);
                    } else {
                        pollCount++;
                        setTimeout(pollBetStatus, 2000);
                    }
                }
            } catch (err) {
                if (mounted) {
                    setError(err.message || 'Failed to fetch bet results');
                    setLoading(false);
                }
            }
        };

        pollBetStatus();

        return () => {
            mounted = false;
        };
    }, [arbitrageId, enabled]);

    return { betResults, loading, error };
};

// Bookmaker status badge
const BookmakerBadge = ({ bookmaker, result }) => {
    const getStatusColor = () => {
        if (!result) return 'bg-gray-100 text-gray-600';
        if (result.success) return 'bg-emerald-100 text-emerald-700';
        return 'bg-rose-100 text-rose-700';
    };

    const getStatusIcon = () => {
        if (!result) return <Clock className="w-4 h-4" />;
        if (result.success) return <CheckCircle2 className="w-4 h-4" />;
        return <XCircle className="w-4 h-4" />;
    };

    return (
        <div className={`px-3 py-1.5 rounded-lg font-medium text-sm flex items-center gap-2 ${getStatusColor()}`}>
            {getStatusIcon()}
            <span>{bookmaker}</span>
        </div>
    );
};

// Processing animation
const ProcessingAnimation = () => (
    <div className="space-y-6">
        <div className="flex justify-center">
            <div className="relative">
                <div className="absolute inset-0 animate-ping bg-blue-500/30 rounded-full" />
                <div className="relative w-24 h-24 border-4 border-blue-200 border-t-blue-600 rounded-full animate-spin" />
            </div>
        </div>

        <div className="text-center space-y-3">
            <h3 className="text-xl font-bold text-gray-900">Processing Your Bets</h3>
            <p className="text-gray-600">
                Placing simultaneous bets across multiple bookmakers...
            </p>

            <div className="flex justify-center gap-2 pt-4">
                <div className="w-3 h-3 bg-blue-600 rounded-full animate-bounce" />
                <div className="w-3 h-3 bg-blue-600 rounded-full animate-bounce" style={{ animationDelay: '0.1s' }} />
                <div className="w-3 h-3 bg-blue-600 rounded-full animate-bounce" style={{ animationDelay: '0.2s' }} />
            </div>
        </div>

        {/* Progress stages */}
        <div className="bg-gradient-to-r from-blue-50 to-indigo-50 rounded-xl p-4 border border-blue-100">
            <div className="space-y-3">
                {[
                    { icon: Activity, text: 'Navigating to bookmakers', delay: 0 },
                    { icon: Target, text: 'Locating markets', delay: 0.5 },
                    { icon: Zap, text: 'Placing bets', delay: 1 },
                    { icon: Trophy, text: 'Verifying placement', delay: 1.5 }
                ].map((stage, index) => (
                    <div
                        key={index}
                        className="flex items-center gap-3 animate-fade-in"
                        style={{ animationDelay: `${stage.delay}s` }}
                    >
                        <div className="p-2 bg-white rounded-lg shadow-sm">
                            <stage.icon className="w-4 h-4 text-blue-600" />
                        </div>
                        <span className="text-sm text-gray-700">{stage.text}</span>
                        <div className="flex-1 ml-auto">
                            <Loader2 className="w-4 h-4 text-blue-600 animate-spin" />
                        </div>
                    </div>
                ))}
            </div>
        </div>
    </div>
);

// Bet result details
const BetResultDetails = ({ betResults }) => {
    const results = Object.entries(betResults);
    const allSuccess = results.every(([_, result]) => result.success);
    const successCount = results.filter(([_, result]) => result.success).length;

    return (
        <div className="space-y-6">
            {/* Overall status */}
            <div className={`rounded-xl p-6 border-2 ${
                allSuccess
                    ? 'bg-gradient-to-r from-emerald-50 to-green-50 border-emerald-200'
                    : 'bg-gradient-to-r from-amber-50 to-orange-50 border-amber-200'
            }`}>
                <div className="flex items-center gap-4">
                    <div className={`p-3 rounded-xl ${
                        allSuccess ? 'bg-emerald-100' : 'bg-amber-100'
                    }`}>
                        {allSuccess ? (
                            <Trophy className="w-8 h-8 text-emerald-600" />
                        ) : (
                            <AlertTriangle className="w-8 h-8 text-amber-600" />
                        )}
                    </div>
                    <div>
                        <h3 className="text-xl font-bold text-gray-900">
                            {allSuccess ? 'All Bets Placed Successfully!' : 'Partial Success'}
                        </h3>
                        <p className="text-gray-600 mt-1">
                            {successCount} of {results.length} bets placed successfully
                        </p>
                    </div>
                </div>
            </div>

            {/* Individual bet results */}
            <div className="space-y-3">
                <h4 className="font-semibold text-gray-900">Bet Details</h4>
                {results.map(([bookmaker, result]) => (
                    <div
                        key={bookmaker}
                        className={`rounded-xl border-2 overflow-hidden transition-all ${
                            result.success
                                ? 'border-emerald-200 bg-emerald-50/50'
                                : 'border-rose-200 bg-rose-50/50'
                        }`}
                    >
                        {/* Header */}
                        <div className={`px-5 py-3 border-b ${
                            result.success ? 'border-emerald-200' : 'border-rose-200'
                        }`}>
                            <div className="flex items-center justify-between">
                                <div className="flex items-center gap-3">
                                    <div className={`w-10 h-10 rounded-lg flex items-center justify-center font-bold ${
                                        result.success
                                            ? 'bg-emerald-100 text-emerald-700'
                                            : 'bg-rose-100 text-rose-700'
                                    }`}>
                                        {bookmaker.slice(0, 2)}
                                    </div>
                                    <div>
                                        <p className="font-semibold text-gray-900">{bookmaker}</p>
                                        <p className="text-xs text-gray-500">Bookmaker</p>
                                    </div>
                                </div>
                                <BookmakerBadge bookmaker={result.success ? 'Success' : 'Failed'} result={result} />
                            </div>
                        </div>

                        {/* Details */}
                        <div className="px-5 py-4 space-y-3">
                            {result.success ? (
                                <>
                                    <div className="grid grid-cols-2 gap-4">
                                        <div>
                                            <p className="text-xs text-gray-500">Bet ID</p>
                                            <p className="font-mono text-sm font-semibold text-gray-900">
                                                {result.betId || 'N/A'}
                                            </p>
                                        </div>
                                        <div>
                                            <p className="text-xs text-gray-500">Stake</p>
                                            <p className="font-semibold text-gray-900">
                                                ₦{result.stakeAmount?.toLocaleString(undefined, {
                                                minimumFractionDigits: 2,
                                                maximumFractionDigits: 2
                                            }) || '0.00'}
                                            </p>
                                        </div>
                                    </div>

                                    <div className="grid grid-cols-2 gap-4">
                                        <div>
                                            <p className="text-xs text-gray-500">Odds</p>
                                            <div className="flex items-center gap-2">
                                                <p className="font-semibold text-gray-900">
                                                    {result.actualOdds?.toFixed(2) || 'N/A'}
                                                </p>
                                                {result.expectedOdds && result.actualOdds && (
                                                    <span className={`text-xs px-2 py-0.5 rounded ${
                                                        result.actualOdds >= result.expectedOdds
                                                            ? 'bg-green-100 text-green-700'
                                                            : 'bg-red-100 text-red-700'
                                                    }`}>
                                                        {result.actualOdds >= result.expectedOdds ? '+' : ''}
                                                        {((result.actualOdds - result.expectedOdds) / result.expectedOdds * 100).toFixed(1)}%
                                                    </span>
                                                )}
                                            </div>
                                        </div>
                                        <div>
                                            <p className="text-xs text-gray-500">Outcome</p>
                                            <p className="font-semibold text-gray-900">{result.outcome || 'N/A'}</p>
                                        </div>
                                    </div>

                                    {result.marketType && (
                                        <div>
                                            <p className="text-xs text-gray-500">Market</p>
                                            <p className="text-sm text-gray-700">{result.marketType}</p>
                                        </div>
                                    )}

                                    {result.executionTimeMs && (
                                        <div className="pt-2 border-t border-emerald-100">
                                            <p className="text-xs text-gray-500">Execution Time</p>
                                            <p className="text-sm text-gray-700">{(result.executionTimeMs / 1000).toFixed(2)}s</p>
                                        </div>
                                    )}
                                </>
                            ) : (
                                <>
                                    <div className="bg-white rounded-lg p-3 border border-rose-200">
                                        <div className="flex items-start gap-3">
                                            <XCircle className="w-5 h-5 text-rose-600 flex-shrink-0 mt-0.5" />
                                            <div className="space-y-1">
                                                <p className="font-medium text-rose-900">Error Details</p>
                                                <p className="text-sm text-rose-700">{result.message}</p>
                                                {result.errorCode && (
                                                    <p className="text-xs text-rose-600 font-mono">
                                                        Code: {result.errorCode}
                                                    </p>
                                                )}
                                            </div>
                                        </div>
                                    </div>

                                    {result.retryAttempts > 0 && (
                                        <div className="text-sm text-gray-600">
                                            Retry attempts: {result.retryAttempts}
                                        </div>
                                    )}

                                    {result.retryable && (
                                        <div className="bg-amber-50 border border-amber-200 rounded-lg p-3">
                                            <p className="text-sm text-amber-800">
                                                💡 This error is retryable. The system may attempt to place this bet again.
                                            </p>
                                        </div>
                                    )}
                                </>
                            )}
                        </div>
                    </div>
                ))}
            </div>

            {/* Summary */}
            {!allSuccess && (
                <div className="bg-gradient-to-r from-blue-50 to-indigo-50 rounded-xl p-5 border border-blue-100">
                    <div className="flex items-start gap-3">
                        <div className="p-2 bg-blue-100 rounded-lg">
                            <TrendingUp className="w-5 h-5 text-blue-600" />
                        </div>
                        <div>
                            <p className="font-medium text-blue-900">What happens next?</p>
                            <p className="text-sm text-blue-700 mt-1">
                                Successfully placed bets are active. Failed bets may be retried automatically
                                or you can place them manually from your bets history.
                            </p>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
};

// Error state
const ErrorState = ({ error }) => (
    <div className="space-y-4">
        <div className="bg-gradient-to-r from-rose-50 to-red-50 rounded-xl p-6 border border-rose-200">
            <div className="flex items-start gap-4">
                <div className="p-3 bg-rose-100 rounded-xl">
                    <AlertTriangle className="w-8 h-8 text-rose-600" />
                </div>
                <div>
                    <h3 className="text-xl font-bold text-rose-900">Unable to Load Bet Results</h3>
                    <p className="text-rose-700 mt-2">{error}</p>
                </div>
            </div>
        </div>

        <div className="bg-gradient-to-r from-blue-50 to-indigo-50 rounded-xl p-5 border border-blue-100">
            <p className="text-sm text-blue-800">
                💡 <strong>Tip:</strong> Check your bets history or contact support if the problem persists.
            </p>
        </div>
    </div>
);

// Main component
const BetTrackingModal = ({ isOpen, onClose, arbitrageId }) => {
    const { betResults, loading, error } = useBetTracking(arbitrageId, isOpen);

    if (!isOpen) return null;

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
                    className="relative bg-gradient-to-br from-white to-gray-50 rounded-2xl shadow-2xl max-w-2xl w-full max-h-[90vh] overflow-hidden animate-scale-in"
                    onClick={e => e.stopPropagation()}
                >
                    {/* Header */}
                    <div className="sticky top-0 bg-gradient-to-r from-blue-600 to-indigo-600 z-10">
                        <div className="absolute inset-0 bg-black/10" />
                        <div className="relative p-6">
                            <div className="flex items-center justify-between">
                                <div>
                                    <h2 className="text-2xl font-bold text-white">Bet Tracking</h2>
                                    <p className="text-white/90 mt-1">
                                        {loading ? 'Processing your bets...' : 'Bet placement results'}
                                    </p>
                                </div>
                                <button
                                    onClick={onClose}
                                    className="p-2 hover:bg-white/10 rounded-lg transition-colors"
                                >
                                    <X className="w-6 h-6 text-white" />
                                </button>
                            </div>
                        </div>
                    </div>

                    {/* Body */}
                    <div className="p-6 overflow-y-auto max-h-[calc(90vh-140px)]">
                        {loading && <ProcessingAnimation />}
                        {error && <ErrorState error={error} />}
                        {betResults && <BetResultDetails betResults={betResults} />}
                    </div>

                    {/* Footer */}
                    <div className="sticky bottom-0 px-6 py-5 bg-gray-50 border-t border-gray-200">
                        <button
                            onClick={onClose}
                            className="w-full py-3 bg-gradient-to-r from-gray-900 to-black text-white rounded-xl hover:shadow-lg transition-all duration-300 font-medium"
                        >
                            {loading ? 'Close & Continue' : 'Done'}
                        </button>
                    </div>
                </div>
            </div>

            <style jsx>{`
                @keyframes scale-in {
                    from {
                        opacity: 0;
                        transform: scale(0.95);
                    }
                    to {
                        opacity: 1;
                        transform: scale(1);
                    }
                }

                @keyframes fade-in {
                    from {
                        opacity: 0;
                        transform: translateY(10px);
                    }
                    to {
                        opacity: 1;
                        transform: translateY(0);
                    }
                }

                .animate-scale-in {
                    animation: scale-in 0.3s ease-out;
                }

                .animate-fade-in {
                    animation: fade-in 0.5s ease-out forwards;
                    opacity: 0;
                }
            `}</style>
        </div>
    );
};

export default BetTrackingModal;