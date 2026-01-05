
export const formatCurrency = (amount, currency = 'NGN') => {
    // Convert from Kobo to Naira
    const nairaAmount = amount / 100;
    return new Intl.NumberFormat('en-NG', {
        style: 'currency',
        currency: currency,
        minimumFractionDigits: 0,
        maximumFractionDigits: 0,
    }).format(nairaAmount);
};

export const formatPercent = (value, decimals = 2) => {
    return `${value.toFixed(decimals)}%`;
};

export const formatDate = (dateString) => {
    if (!dateString) return 'N/A';

    const date = new Date(dateString);
    const now = new Date();
    const diffMs = date - now;
    const diffHours = Math.floor(diffMs / (1000 * 60 * 60));
    const diffMinutes = Math.floor((diffMs % (1000 * 60 * 60)) / (1000 * 60));

    if (date < now) {
        return 'Started';
    }

    if (diffHours > 24) {
        return date.toLocaleDateString('en-US', {
            month: 'short',
            day: 'numeric',
            hour: '2-digit',
            minute: '2-digit'
        });
    } else if (diffHours > 0) {
        return `In ${diffHours}h ${diffMinutes}m`;
    } else {
        return `In ${diffMinutes}m`;
    }
};

export const formatLargeNumber = (num) => {
    if (num >= 1000000) {
        return `${(num / 1000000).toFixed(1)}M`;
    } else if (num >= 1000) {
        return `${(num / 1000).toFixed(1)}k`;
    }
    return num.toString();
};