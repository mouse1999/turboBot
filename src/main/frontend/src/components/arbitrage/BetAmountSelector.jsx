import React from 'react';
import NairaSymbol from "../common/NairaSymbol.jsx";

// Predefined bet amounts in thousands (k)
const BET_AMOUNTS = [100, 70, 50, 20, 10, 5, 1];

const BetAmountSelector = ({ selectedAmount, onAmountChange, className = '', compact = false }) => {
    // Convert from actual amount to k value for display
    const selectedK = selectedAmount / 1000;

    const handleAmountClick = (amountK) => {
        onAmountChange(amountK * 1000);
    };

    const handleCustomAmount = (e) => {
        const value = parseFloat(e.target.value);
        if (!isNaN(value) && value > 0) {
            onAmountChange(value * 1000);
        }
    };

    // Compact mode - horizontal layout spanning full width
    if (compact) {
        return (
            <div className={`flex flex-col gap-1 w-full ${className}`}>
                <div className="flex items-center gap-1">
                    <NairaSymbol size={12} className="text-gray-500" />
                    <span className="text-xs text-gray-600">Amount:</span>
                </div>
                <div className="flex gap-1 w-full">
                    {BET_AMOUNTS.map((amount) => (
                        <button
                            key={amount}
                            type="button"
                            onClick={() => handleAmountClick(amount)}
                            className={`flex-1 px-2 py-1 text-xs font-medium rounded transition-colors ${
                                selectedK === amount
                                    ? 'bg-blue-600 text-white'
                                    : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
                            }`}
                        >
                            {amount}k
                        </button>
                    ))}
                </div>
            </div>
        );
    }

    // Full mode - original vertical layout
    return (
        <div className={`space-y-2 ${className}`}>
            <div className="flex items-center gap-2">
                <NairaSymbol size={16} className="text-gray-500" />
                <label className="text-sm font-medium text-gray-700">Bet Amount (k)</label>
            </div>
            <div className="flex flex-wrap gap-2">
                {BET_AMOUNTS.map((amount) => (
                    <button
                        key={amount}
                        type="button"
                        onClick={() => handleAmountClick(amount)}
                        className={`px-3 py-1.5 text-sm font-medium rounded-lg transition-colors ${
                            selectedK === amount
                                ? 'bg-blue-600 text-white'
                                : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
                        }`}
                    >
                        ₦{amount}k
                    </button>
                ))}
                <div className="relative">
                    <input
                        type="number"
                        min="0.1"
                        step="0.1"
                        placeholder="Custom"
                        value={selectedK % 1 === 0 ? selectedK : selectedK.toFixed(1)}
                        onChange={handleCustomAmount}
                        className="w-24 px-3 py-1.5 text-sm border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                    />
                    <span className="absolute right-3 top-1/2 transform -translate-y-1/2 text-gray-500 text-sm">
                        k
                    </span>
                </div>
            </div>
            <div className="text-xs text-gray-500">
                Selected amount: ₦{(selectedAmount).toLocaleString()}
            </div>
        </div>
    );
};

export default BetAmountSelector;