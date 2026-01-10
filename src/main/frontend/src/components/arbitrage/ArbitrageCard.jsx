import React, { useState, useMemo, memo } from 'react';
import {
    Clock, MapPin, Trophy, Activity, ChevronDown, ChevronUp,
    Banknote, Zap, Shield, TrendingUp, Star, Target,
    Calendar, Globe, Award, PieChart, DollarSign
} from 'lucide-react';
import Badge from '../common/Badge';
import Button from '../common/Button';
import BetAmountSelector from './BetAmountSelector.jsx';
import {
    calculateArbitrageStakes,
    calculateArbitrageProfit,
    formatCurrency
} from '../../utils/stakeCalculator';

// Memoized Outcome Card for better performance
const OutcomeCard = memo(({ outcome }) => (
    <div className="group relative bg-gradient-to-br from-white to-gray-50 rounded-xl p-3 border border-gray-200 hover:border-blue-300 hover:shadow-sm transition-all duration-200">
        {/* Glow effect on hover */}
        <div className="absolute inset-0 rounded-xl bg-gradient-to-r from-blue-500/0 via-blue-500/5 to-blue-500/0 opacity-0 group-hover:opacity-100 transition-opacity duration-300" />

        <div className="relative">
            <div className="flex items-start justify-between mb-2">
                <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2 mb-1">
                        <div className="w-6 h-6 rounded-lg bg-gradient-to-br from-blue-100 to-blue-200 flex items-center justify-center flex-shrink-0">
                            <Target className="w-3 h-3 text-blue-600" />
                        </div>
                        <div className="min-w-0">
                            <div className="text-xs font-medium text-gray-900 truncate">
                                {outcome.bookmakerName}
                            </div>
                            <div className="text-xs text-gray-500 truncate">
                                {outcome.outcomeName}
                            </div>
                        </div>
                    </div>
                </div>
                <div className="text-right ml-3">
                    <div className="flex items-baseline gap-1">
                        <span className="text-lg font-bold bg-gradient-to-r from-blue-600 to-indigo-600 bg-clip-text text-transparent">
                            {outcome.odds.toFixed(2)}
                        </span>
                        <span className="text-xs text-gray-400">odds</span>
                    </div>
                    {outcome.previousOdds && outcome.previousOdds !== outcome.odds && (
                        <div className="flex items-center gap-1 text-xs">
                            <span className="text-gray-400 line-through">
                                {outcome.previousOdds.toFixed(2)}
                            </span>
                            {outcome.previousOdds < outcome.odds ? (
                                <span className="text-green-600">↑</span>
                            ) : (
                                <span className="text-red-600">↓</span>
                            )}
                        </div>
                    )}
                </div>
            </div>

            <div className="grid grid-cols-2 gap-2 text-xs">
                <div className="bg-gradient-to-r from-gray-50 to-white rounded-lg p-2 border border-gray-100">
                    <div className="text-gray-500 mb-0.5">Stake</div>
                    <div className="font-semibold text-gray-900">
                        {formatCurrency(outcome.stake)}
                    </div>
                </div>
                <div className="bg-gradient-to-r from-blue-50 to-indigo-50 rounded-lg p-2 border border-blue-100">
                    <div className="text-blue-600 mb-0.5">Return</div>
                    <div className="font-semibold text-blue-700">
                        {formatCurrency(outcome.stake * outcome.odds)}
                    </div>
                </div>
            </div>
        </div>
    </div>
));

OutcomeCard.displayName = 'OutcomeCard';

// Main ArbitrageCard Component
const ArbitrageCard = memo(({ arbitrage, expanded, onToggle, onPlaceBet }) => {
    const [selectedAmount, setSelectedAmount] = useState(100000); // Default 100k

    // Memoized calculations for performance
    const outcomesWithStakes = useMemo(() =>
            calculateArbitrageStakes(arbitrage.outcomes, selectedAmount),
        [arbitrage.outcomes, selectedAmount]
    );

    const profitData = useMemo(() =>
            calculateArbitrageProfit(arbitrage.outcomes, selectedAmount),
        [arbitrage.outcomes, selectedAmount]
    );

    // Determine colors based on profit
    const getProfitColor = (profit) => {
        if (profit >= 5) return {
            text: 'text-emerald-600',
            bg: 'bg-emerald-50',
            border: 'border-emerald-200',
            gradient: 'from-emerald-500 to-green-500'
        };
        if (profit >= 3) return {
            text: 'text-blue-600',
            bg: 'bg-blue-50',
            border: 'border-blue-200',
            gradient: 'from-blue-500 to-indigo-500'
        };
        if (profit >= 0) return {
            text: 'text-gray-600',
            bg: 'bg-gray-50',
            border: 'border-gray-200',
            gradient: 'from-gray-500 to-gray-600'
        };
        return {
            text: 'text-rose-600',
            bg: 'bg-rose-50',
            border: 'border-rose-200',
            gradient: 'from-rose-500 to-red-500'
        };
    };

    const profitColor = getProfitColor(arbitrage.profitPercentage);
    const arbAgeDisplay = useMemo(() => getArbAgeDisplay(arbitrage.arbAge), [arbitrage.arbAge]);

    const handlePlaceBet = () => {
        onPlaceBet(arbitrage, selectedAmount, outcomesWithStakes, profitData);
    };

    return (
        <div className="group relative bg-white rounded-2xl shadow-lg hover:shadow-2xl transition-all duration-300 border border-gray-200 hover:border-blue-300 overflow-hidden">
            {/* Decorative background elements */}
            <div className="absolute top-0 left-0 w-32 h-32 bg-gradient-to-br from-blue-50 to-transparent rounded-full -translate-x-16 -translate-y-16 opacity-50" />
            <div className="absolute bottom-0 right-0 w-32 h-32 bg-gradient-to-tl from-emerald-50 to-transparent rounded-full translate-x-16 translate-y-16 opacity-50" />

            {/* Main Card Content */}
            <div className="relative">
                {/* Compact Header */}
                <div className="bg-gradient-to-r from-white to-gray-50 px-6 py-4 border-b border-gray-200">
                    <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-4">
                        {/* Match Info */}
                        <div className="flex-1 min-w-0">
                            <div className="flex items-center gap-3 mb-2">
                                <div className="p-2 bg-gradient-to-r from-blue-500 to-indigo-500 rounded-xl">
                                    <Trophy className="w-5 h-5 text-white" />
                                </div>
                                <div className="min-w-0">
                                    <h3 className="text-lg font-bold text-gray-900 truncate">
                                        {arbitrage.homeTeam} vs {arbitrage.awayTeam}
                                    </h3>
                                    <div className="flex flex-wrap items-center gap-3 mt-1">
                                        <Badge
                                            variant={arbitrage.isLive ? "live" : "default"}
                                            className="gap-1 px-2 py-1"
                                        >
                                            {arbitrage.isLive ? (
                                                <>
                                                    <Zap className="w-3 h-3 animate-pulse" />
                                                    LIVE
                                                </>
                                            ) : (
                                                <>
                                                    <Calendar className="w-3 h-3" />
                                                    PREMATCH
                                                </>
                                            )}
                                        </Badge>

                                        <div className="flex items-center gap-1 text-sm text-gray-600">
                                            <MapPin className="w-4 h-4" />
                                            <span className="truncate max-w-[200px]">
                                                {arbitrage.leagueName || 'Unknown League'}
                                            </span>
                                        </div>

                                        <div className="flex items-center gap-1 text-sm text-gray-600">
                                            <Globe className="w-4 h-4" />
                                            <span>{arbitrage.sport}</span>
                                        </div>
                                    </div>
                                </div>
                            </div>

                            {/* Progress and Age */}
                            <div className="flex flex-wrap items-center gap-3 mt-2">
                                {arbitrage.matchProgress && (
                                    <div className="flex items-center gap-2 bg-gradient-to-r from-amber-50 to-orange-50 px-3 py-1 rounded-lg">
                                        <Activity className="w-3 h-3 text-orange-600 animate-pulse" />
                                        <span className="text-sm font-medium text-orange-700">
                                            {arbitrage.matchProgress}
                                        </span>
                                    </div>
                                )}

                                <div className="flex items-center gap-2">
                                    <Clock className="w-4 h-4 text-gray-500" />
                                    <div className="text-sm">
                                        <span className="text-gray-600">Age: </span>
                                        <span className={`font-semibold ${arbAgeDisplay.color}`}>
                                            {arbAgeDisplay.text}
                                        </span>
                                    </div>
                                </div>
                            </div>
                        </div>

                        {/* Profit and Action Section */}
                        <div className="flex flex-col items-end gap-4">
                            {/* Profit Display */}
                            <div className="text-center">
                                <div className={`text-3xl font-bold ${profitColor.text} mb-1`}>
                                    {arbitrage.profitPercentage >= 0 ? '+' : ''}{arbitrage.profitPercentage.toFixed(2)}%
                                </div>
                                <div className="flex items-center justify-center gap-2">
                                    <TrendingUp className="w-4 h-4 text-gray-500" />
                                    <span className="text-sm text-gray-600">Profit Margin</span>
                                    {arbitrage.confidenceScore && (
                                        <Badge variant="info" className="ml-2">
                                            {arbitrage.confidenceScore.toFixed(0)}% Conf
                                        </Badge>
                                    )}
                                </div>
                            </div>

                            {/* Quick Actions */}
                            <div className="flex items-center gap-2">
                                <Button
                                    variant="ghost"
                                    size="sm"
                                    onClick={onToggle}
                                    className="text-gray-600 hover:text-blue-600"
                                    icon={expanded ? ChevronUp : ChevronDown}
                                >
                                    {expanded ? 'Less' : 'More'}
                                </Button>
                            </div>
                        </div>
                    </div>
                </div>

                {/* Outcomes and Betting Section */}
                <div className="px-6 py-4">
                    {/* Outcomes Grid */}
                    <div className="grid grid-cols-1 md:grid-cols-2 gap-3 mb-4">
                        {outcomesWithStakes.map((outcome) => (
                            <OutcomeCard key={outcome.id} outcome={outcome} />
                        ))}
                    </div>

                    {/* Betting Interface */}
                    <div className="bg-gradient-to-r from-gray-50 to-white rounded-xl p-4 border border-gray-200 mb-4">
                        <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
                            {/* Bet Amount Selector */}
                            <div className="lg:col-span-2">
                                <div className="flex items-center gap-2 mb-2">
                                    <Banknote className="w-5 h-5 text-blue-600" />
                                    <h4 className="font-semibold text-gray-900">Select Bet Amount</h4>
                                </div>
                                <BetAmountSelector
                                    selectedAmount={selectedAmount}
                                    onAmountChange={setSelectedAmount}
                                    compact={false}
                                />
                            </div>

                            {/* Quick Stats */}
                            <div className="bg-gradient-to-br from-blue-50 to-indigo-50 rounded-xl p-4 border border-blue-100">
                                <div className="space-y-3">
                                    <div className="flex items-center justify-between">
                                        <span className="text-sm text-gray-600">Total Stake:</span>
                                        <span className="font-bold text-gray-900">
                                            {formatCurrency(profitData.totalStake)}
                                        </span>
                                    </div>
                                    <div className="h-px bg-gradient-to-r from-transparent via-blue-200 to-transparent" />
                                    <div className="flex items-center justify-between">
                                        <span className="text-sm text-gray-600">Expected Return:</span>
                                        <span className="font-bold text-blue-600">
                                            {formatCurrency(profitData.potentialReturn)}
                                        </span>
                                    </div>
                                    <div className="h-px bg-gradient-to-r from-transparent via-blue-200 to-transparent" />
                                    <div className="flex items-center justify-between">
                                        <span className="text-sm text-gray-600">Expected Profit:</span>
                                        <span className={`font-bold ${profitColor.text}`}>
                                            {profitData.profit >= 0 ? '+' : ''}{formatCurrency(profitData.profit)}
                                        </span>
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div>

                    {/* Action Button */}
                    <div className="flex flex-col sm:flex-row items-center justify-between gap-4">
                        <div className="flex-1 text-sm text-gray-600">
                            <div className="flex items-center gap-2">
                                <Shield className="w-4 h-4 text-green-600" />
                                <span>Risk-free arbitrage • Guaranteed profit • Instant placement</span>
                            </div>
                        </div>
                        <Button
                            variant="success"
                            size="lg"
                            onClick={handlePlaceBet}
                            className="px-8 py-3 rounded-xl shadow-lg hover:shadow-xl transition-all duration-300 group"
                            icon={Banknote}
                        >
                            <span className="font-bold">Place Bet • ₦{(selectedAmount / 1000).toFixed(0)}k</span>
                            <div className="ml-2 px-2 py-1 bg-white/20 rounded-lg text-xs">
                                {profitData.profitPercentage.toFixed(1)}% ROI
                            </div>
                        </Button>
                    </div>
                </div>

                {/* Expanded Details */}
                {expanded && (
                    <div className="border-t border-gray-200 bg-gradient-to-b from-gray-50 to-white">
                        <div className="px-6 py-4">
                            <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mb-4">
                                {/* Arbitrage Details */}
                                <div className="bg-white rounded-xl p-4 border border-gray-200">
                                    <h4 className="font-semibold text-gray-900 mb-3 flex items-center gap-2">
                                        <Award className="w-4 h-4" />
                                        Arbitrage Details
                                    </h4>
                                    <div className="space-y-2 text-sm">
                                        <div className="flex justify-between">
                                            <span className="text-gray-500">External ID:</span>
                                            <code className="font-mono text-gray-900 bg-gray-100 px-2 py-1 rounded">
                                                {arbitrage.externalId?.slice(0, 12)}...
                                            </code>
                                        </div>
                                        <div className="flex justify-between">
                                            <span className="text-gray-500">Event ID:</span>
                                            <span className="font-medium text-gray-900">{arbitrage.eventId}</span>
                                        </div>
                                        <div className="flex justify-between">
                                            <span className="text-gray-500">Status:</span>
                                            <Badge variant={arbitrage.status === 'ACTIVE' ? 'success' : 'default'}>
                                                {arbitrage.status}
                                            </Badge>
                                        </div>
                                        <div className="flex justify-between">
                                            <span className="text-gray-500">Market Type:</span>
                                            <span className="font-medium text-gray-900">{arbitrage.marketType || 'N/A'}</span>
                                        </div>
                                    </div>
                                </div>

                                {/* Financial Summary */}
                                <div className="bg-white rounded-xl p-4 border border-gray-200">
                                    <h4 className="font-semibold text-gray-900 mb-3 flex items-center gap-2">
                                        <PieChart className="w-4 h-4" />
                                        Financial Summary
                                    </h4>
                                    <div className="space-y-2 text-sm">
                                        <div className="flex justify-between">
                                            <span className="text-gray-500">ROI Percentage:</span>
                                            <span className={`font-medium ${arbitrage.roiPercentage >= 0 ? 'text-green-600' : 'text-red-600'}`}>
                                                {arbitrage.roiPercentage >= 0 ? '+' : ''}{arbitrage.roiPercentage?.toFixed(2) || '0.00'}%
                                            </span>
                                        </div>
                                        <div className="flex justify-between">
                                            <span className="text-gray-500">Bookmakers:</span>
                                            <span className="font-medium text-gray-900">{outcomesWithStakes.length}</span>
                                        </div>
                                        <div className="flex justify-between">
                                            <span className="text-gray-500">Arbitrage Age:</span>
                                            <span className={`font-medium ${arbAgeDisplay.color}`}>
                                                {arbAgeDisplay.text}
                                            </span>
                                        </div>
                                        <div className="flex justify-between">
                                            <span className="text-gray-500">Last Updated:</span>
                                            <span className="font-medium text-gray-900">
                                                {formatDate(arbitrage.lastCheckedAt)}
                                            </span>
                                        </div>
                                    </div>
                                </div>

                                {/* Quick Stats */}
                                <div className="bg-gradient-to-br from-blue-50 to-indigo-50 rounded-xl p-4 border border-blue-200">
                                    <h4 className="font-semibold text-gray-900 mb-3 flex items-center gap-2">
                                        <Star className="w-4 h-4" />
                                        Performance
                                    </h4>
                                    <div className="space-y-3">
                                        <div className="text-center">
                                            <div className="text-2xl font-bold text-gray-900 mb-1">
                                                {arbitrage.profitPercentage.toFixed(1)}%
                                            </div>
                                            <div className="text-sm text-gray-600">Profit Margin</div>
                                        </div>
                                        <div className="h-px bg-gradient-to-r from-transparent via-blue-200 to-transparent" />
                                        <div className="text-center">
                                            <div className={`text-xl font-bold ${profitColor.text} mb-1`}>
                                                {profitData.profit >= 0 ? '+' : ''}{formatCurrency(profitData.profit)}
                                            </div>
                                            <div className="text-sm text-gray-600">Expected Profit</div>
                                        </div>
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div>
                )}
            </div>
        </div>
    );
});

ArbitrageCard.displayName = 'ArbitrageCard';

// Helper functions
const getArbAgeDisplay = (age) => {
    if (age === null || age === undefined) return { text: 'N/A', color: 'text-gray-500' };

    if (age <= 5) return { text: `${age}s`, color: 'text-emerald-600' };
    if (age <= 15) return { text: `${age}s`, color: 'text-amber-600' };
    if (age <= 30) return { text: `${age}s`, color: 'text-orange-600' };
    return { text: `${age}s`, color: 'text-rose-600' };
};

const formatDate = (dateString) => {
    if (!dateString) return 'N/A';
    const date = new Date(dateString);
    return date.toLocaleString('en-US', {
        month: 'short',
        day: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit'
    });
};

export default ArbitrageCard;