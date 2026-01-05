import React from 'react';
import ArbitrageCard from './ArbitrageCard.jsx';

const ArbitrageList = ({ arbitrages, expandedCards, onToggleCard, onPlaceBet }) => {
    if (arbitrages.length === 0) {
        return (
            <div className="bg-white rounded-xl shadow-md p-8 sm:p-12 text-center">
                <div className="text-gray-400 text-4xl sm:text-6xl mb-4">📊</div>
                <h3 className="text-lg sm:text-xl font-semibold text-gray-700 mb-2">
                    No Arbitrage Opportunities
                </h3>
                <p className="text-gray-500 text-sm sm:text-base">
                    There are no arbitrage opportunities at the moment.
                </p>
            </div>
        );
    }

    return (
        <div className="space-y-4 sm:space-y-6">
            {arbitrages.map(arbitrage => (
                <ArbitrageCard
                    key={arbitrage.id}
                    arbitrage={arbitrage}
                    expanded={expandedCards[arbitrage.id]}
                    onToggle={() => onToggleCard(arbitrage.id)}
                    onPlaceBet={onPlaceBet}
                />
            ))}
        </div>
    );
};

export default ArbitrageList;