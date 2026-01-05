/**
 * Calculate stakes for arbitrage betting
 * Formula: stake = (total_amount / total_inverse_odds) * (1 / odds)
 * where total_inverse_odds = sum of (1/odds) for all outcomes
 */

export const calculateArbitrageStakes = (outcomes, totalAmount) => {
    if (!outcomes || outcomes.length === 0) {
        return [];
    }

    // Calculate total inverse odds (sum of 1/odds for all outcomes)
    const totalInverseOdds = outcomes.reduce((sum, outcome) => {
        return sum + (1 / outcome.odds);
    }, 0);

    // Calculate stake for each outcome
    return outcomes.map(outcome => {
        const stake = (totalAmount / totalInverseOdds) * (1 / outcome.odds);
        return {
            ...outcome,
            stake: stake
        };
    });
};

/**
 * Calculate profit for arbitrage
 */
export const calculateArbitrageProfit = (outcomes, totalAmount) => {
    const stakesWithAmounts = calculateArbitrageStakes(outcomes, totalAmount);

    // Calculate potential return for first outcome (all outcomes should return same amount in perfect arb)
    if (stakesWithAmounts.length > 0) {
        const firstOutcome = stakesWithAmounts[0];
        const potentialReturn = firstOutcome.stake * firstOutcome.odds;
        const profit = potentialReturn - totalAmount;
        const profitPercentage = (profit / totalAmount) * 100;

        return {
            profit,
            profitPercentage,
            potentialReturn,
            totalStake: totalAmount
        };
    }

    return {
        profit: 0,
        profitPercentage: 0,
        potentialReturn: 0,
        totalStake: totalAmount
    };
};

/**
 * Verify if stakes are correctly calculated (all outcomes should return same amount)
 */
export const verifyArbitrageStakes = (outcomes) => {
    if (!outcomes || outcomes.length < 2) {
        return false;
    }

    const returns = outcomes.map(outcome => outcome.stake * outcome.odds);
    const firstReturn = returns[0];

    // Check if all returns are approximately equal (within 0.01 tolerance)
    return returns.every(ret => Math.abs(ret - firstReturn) < 0.01);
};

/**
 * Format currency for display
 */
export const formatCurrency = (amount, currency = '₦') => {
    return `${currency}${amount.toFixed(2)}`;
};

/**
 * Format large numbers with K suffix
 */
export const formatAmountWithK = (amount) => {
    if (amount >= 1000) {
        return `${(amount / 1000).toFixed(0)}k`;
    }
    return amount.toFixed(0);
};