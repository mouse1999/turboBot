// Betting amounts in cents (100k = 10000000 cents)
export const BET_AMOUNTS = [
    { value: 10000000, label: '100k' },
    { value: 7000000, label: '70k' },
    { value: 5000000, label: '50k' },
    { value: 2000000, label: '20k' },
    { value: 1000000, label: '10k' },
    { value: 500000, label: '5k' },
    { value: 100000, label: '1k' },
];

// API polling interval in milliseconds
export const POLLING_INTERVAL = 2000; // 2 seconds

// Sport types
export const SPORT_TYPES = {
    FOOTBALL: 'Football',
    BASKETBALL: 'Basketball',
    TENNIS: 'Tennis',
    BASEBALL: 'Baseball',
    HOCKEY: 'Hockey',
    CRICKET: 'Cricket',
};

// Status types
export const STATUS_TYPES = {
    ACTIVE: 'ACTIVE',
    INACTIVE: 'INACTIVE',
    EXPIRED: 'EXPIRED',
    CLOSED: 'CLOSED',
};