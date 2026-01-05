import React, { useState, useMemo } from 'react';
import { Clock, MapPin, Trophy, Activity, ChevronDown, ChevronUp, Banknote } from 'lucide-react';
import Badge from '../common/Badge';
import Button from '../common/Button';
import BetAmountSelector from './BetAmountSelector.jsx';
import {
    calculateArbitrageStakes,
    calculateArbitrageProfit,
    formatCurrency
} from '../../utils/stakeCalculator';

const ArbitrageCard = ({ arbitrage, expanded, onToggle, onPlaceBet }) => {
    const [selectedAmount, setSelectedAmount] = useState(100000); // Default 100k

    // Calculate stakes dynamically based on selected amount
    const outcomesWithStakes = useMemo(() => {
        return calculateArbitrageStakes(arbitrage.outcomes, selectedAmount);
    }, [arbitrage.outcomes, selectedAmount]);

    // Calculate profit using the utility function
    const profitData = useMemo(() => {
        return calculateArbitrageProfit(arbitrage.outcomes, selectedAmount);
    }, [arbitrage.outcomes, selectedAmount]);

    // Determine profit percentage color
    const profitColor = profitData.profitPercentage >= 5 ? 'text-green-600' :
        profitData.profitPercentage >= 3 ? 'text-blue-600' :
            profitData.profitPercentage >= 0 ? 'text-gray-600' : 'text-red-600';

    // Determine profit amount color
    const profitAmountColor = profitData.profit >= 0 ? 'text-green-600' : 'text-red-600';

    // Determine ROI color
    const roiColor = arbitrage.roiPercentage >= 0 ? 'text-green-600' : 'text-red-600';

    const formatDate = (dateString) => {
        if (!dateString) return 'N/A';
        const date = new Date(dateString);
        return date.toLocaleString('en-US', {
            month: 'short',
            day: 'numeric',
            hour: '2-digit',
            minute: '2-digit'
        });
    };

    const handlePlaceBet = () => {
        onPlaceBet(arbitrage, selectedAmount, outcomesWithStakes, profitData);
    };

    return (
        <div className="bg-white rounded-lg shadow hover:shadow-lg transition-all duration-200 border border-gray-200 overflow-hidden w-full max-w-6xl">
            {/* Compact Header */}
            <div className="bg-gradient-to-r from-blue-50 to-indigo-50 px-3 py-2 border-b border-gray-200 flex flex-col sm:flex-row items-start justify-between gap-2">
                <div className="flex-1 min-w-0 w-full sm:w-auto">
                    <div className="flex items-center gap-2 mb-1">
                        <Trophy className="text-blue-600 flex-shrink-0" size={16} />
                        <h3 className="text-sm font-bold text-gray-900 truncate">
                            {arbitrage.homeTeam} vs {arbitrage.awayTeam}
                        </h3>
                        {arbitrage.isLive && <Badge variant="live">LIVE</Badge>}
                    </div>
                    <div className="flex flex-wrap items-center gap-2 text-xs text-gray-600">
                        <div className="flex items-center gap-1">
                            <MapPin size={11} className="flex-shrink-0" />
                            <span className="truncate">{arbitrage.leagueName || 'Unknown League'}</span>
                        </div>
                        <div className="flex items-center gap-1">
                            <Activity size={11} className="flex-shrink-0" />
                            <span>{arbitrage.sport}</span>
                        </div>
                        {arbitrage.matchStartTime && (
                            <div className="flex items-center gap-1">
                                <Clock size={11} className="flex-shrink-0" />
                                <span>{formatDate(arbitrage.matchStartTime)}</span>
                            </div>
                        )}
                    </div>
                </div>

                {/* Profit and Place Bet Section */}
                <div className="flex flex-col sm:flex-row items-stretch sm:items-center gap-2 w-full sm:w-auto">
                    {/* Profit - Desktop only inline */}
                    <div className="hidden sm:block text-center">
                        <div className={`text-xl font-bold ${profitColor}`}>
                            {profitData.profitPercentage >= 0 ? '+' : ''}{profitData.profitPercentage.toFixed(2)}%
                        </div>
                        <div className="text-xs text-gray-500">Profit</div>
                        {arbitrage.confidenceScore && (
                            <div className="mt-0.5">
                                <Badge variant="info" className="text-xs px-1.5 py-0">
                                    {arbitrage.confidenceScore.toFixed(0)}%
                                </Badge>
                            </div>
                        )}
                    </div>

                    {/* Mobile: Profit above Bet Selector */}
                    <div className="sm:hidden flex items-center justify-between w-full mb-1">
                        <div className="text-center">
                            <div className={`text-xl font-bold ${profitColor}`}>
                                {profitData.profitPercentage >= 0 ? '+' : ''}{profitData.profitPercentage.toFixed(2)}%
                            </div>
                            <div className="text-xs text-gray-500">Profit</div>
                        </div>
                        {arbitrage.confidenceScore && (
                            <Badge variant="info" className="text-xs px-1.5 py-0">
                                {arbitrage.confidenceScore.toFixed(0)}%
                            </Badge>
                        )}
                    </div>

                    {/* Bet Amount Selector and Button */}
                    <div className="flex flex-col sm:flex-row items-stretch sm:items-center gap-2 w-full sm:w-auto">
                        <div className="w-full sm:w-auto">
                            <BetAmountSelector
                                selectedAmount={selectedAmount}
                                onAmountChange={setSelectedAmount}
                                compact={true}
                            />
                        </div>
                        <Button
                            variant="success"
                            size="sm"
                            onClick={handlePlaceBet}
                            className="whitespace-nowrap text-xs px-3 py-1.5 w-full sm:w-auto"
                            icon={Banknote}
                        >
                            Place Bet ₦{(selectedAmount / 1000).toFixed(0)}k
                        </Button>
                    </div>
                </div>
            </div>

            {/* Compact Outcomes Summary */}
            <div className="px-3 py-2">
                <div className="grid grid-cols-2 gap-2">
                    {outcomesWithStakes.map((outcome) => (
                        <div key={outcome.id} className="bg-gray-50 rounded p-2 border border-gray-200">
                            <div className="flex items-start justify-between mb-1">
                                <div className="flex-1 min-w-0">
                                    <div className="text-xs text-gray-500 mb-0.5">{outcome.outcomeName}</div>
                                    <div className="font-bold text-sm text-gray-900 truncate">{outcome.bookmakerName}</div>
                                </div>
                                <div className="text-right ml-2">
                                    <div className="text-lg font-bold text-blue-600">{outcome.odds.toFixed(2)}</div>
                                    {outcome.previousOdds && outcome.previousOdds !== outcome.odds && (
                                        <div className="text-xs text-gray-400 line-through">
                                            {outcome.previousOdds.toFixed(2)}
                                        </div>
                                    )}
                                </div>
                            </div>
                            <div className="text-xs text-gray-600">
                                Stake: <span className="font-semibold text-blue-600">{formatCurrency(outcome.stake)}</span>
                            </div>
                            <div className="text-xs text-gray-500 mt-0.5">
                                Return: <span className="font-medium">{formatCurrency(outcome.stake * outcome.odds)}</span>
                            </div>
                        </div>
                    ))}
                </div>

                {/* Total Summary - Using profitData from calculateArbitrageProfit */}
                <div className="mt-2 p-2 bg-blue-50 rounded border border-blue-200">
                    <div className="flex justify-between text-xs">
                        <span className="text-gray-600">Total Stake:</span>
                        <span className="font-bold text-gray-900">{formatCurrency(profitData.totalStake)}</span>
                    </div>
                    <div className="flex justify-between text-xs mt-1">
                        <span className="text-gray-600">Expected Return:</span>
                        <span className="font-bold text-blue-600">
                            {formatCurrency(profitData.potentialReturn)}
                        </span>
                    </div>
                    <div className="flex justify-between text-xs mt-1">
                        <span className="text-gray-600">Profit:</span>
                        <span className={`font-bold ${profitAmountColor}`}>
                            {profitData.profit >= 0 ? '+' : ''}{formatCurrency(profitData.profit)}
                        </span>
                    </div>
                    <div className="flex justify-between text-xs mt-1">
                        <span className="text-gray-600">Profit %:</span>
                        <span className={`font-bold ${profitColor}`}>
                            {profitData.profitPercentage >= 0 ? '+' : ''}{profitData.profitPercentage.toFixed(2)}%
                        </span>
                    </div>
                </div>

                {/* Compact Expand Button */}
                <button
                    onClick={onToggle}
                    className="w-full mt-2 flex items-center justify-center gap-1 text-blue-600 hover:text-blue-800 py-1 rounded hover:bg-blue-50 transition-colors"
                >
                    {expanded ? (
                        <>
                            <span className="text-xs font-medium">Show Less</span>
                            <ChevronUp size={14} />
                        </>
                    ) : (
                        <>
                            <span className="text-xs font-medium">Show More Details</span>
                            <ChevronDown size={14} />
                        </>
                    )}
                </button>

                {/* Expanded Details */}
                {expanded && (
                    <div className="mt-2 pt-2 border-t border-gray-200 space-y-2">
                        <div className="grid grid-cols-2 gap-2 text-xs">
                            <div>
                                <span className="text-gray-500">External ID:</span>
                                <div className="font-mono text-xs text-gray-700 mt-0.5 break-all">
                                    {arbitrage.externalId}
                                </div>
                            </div>
                            <div>
                                <span className="text-gray-500">Event ID:</span>
                                <div className="font-mono text-xs text-gray-700 mt-0.5">
                                    {arbitrage.eventId}
                                </div>
                            </div>
                            <div>
                                <span className="text-gray-500">Status:</span>
                                <div className="mt-0.5">
                                    <Badge variant={arbitrage.status === 'ACTIVE' ? 'success' : 'default'}>
                                        {arbitrage.status}
                                    </Badge>
                                </div>
                            </div>
                            <div>
                                <span className="text-gray-500">Market Type:</span>
                                <div className="font-medium text-gray-700 mt-0.5">{arbitrage.marketType}</div>
                            </div>
                            {arbitrage.roiPercentage && (
                                <div>
                                    <span className="text-gray-500">ROI:</span>
                                    <div className={`font-medium mt-0.5 ${roiColor}`}>
                                        {arbitrage.roiPercentage >= 0 ? '+' : ''}{arbitrage.roiPercentage.toFixed(2)}%
                                    </div>
                                </div>
                            )}
                            {arbitrage.matchProgress && (
                                <div>
                                    <span className="text-gray-500">Progress:</span>
                                    <div className="font-medium text-gray-700 mt-0.5">{arbitrage.matchProgress}</div>
                                </div>
                            )}
                        </div>

                        <div className="text-xs text-gray-500 pt-1 border-t border-gray-100">
                            Last updated: {formatDate(arbitrage.lastCheckedAt)}
                        </div>
                    </div>
                )}
            </div>
        </div>
    );
};

export default ArbitrageCard;