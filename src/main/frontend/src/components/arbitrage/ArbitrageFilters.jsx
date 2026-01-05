import React from 'react';
import { Filter } from 'lucide-react';
import Button from '../common/Button';

const ArbitrageFilters = ({ filter, setFilter, arbitrages }) => {
    const liveCount = arbitrages.filter(a => a.isLive).length;
    const prematchCount = arbitrages.filter(a => !a.isLive).length;
    const allCount = arbitrages.length;

    return (
        <div className="flex flex-wrap items-center gap-3">
            <div className="flex items-center gap-2 text-gray-700">
                <Filter size={18} />
                <span className="text-sm font-medium">Filter:</span>
            </div>
            <Button
                variant={filter === 'all' ? 'primary' : 'outline'}
                size="sm"
                onClick={() => setFilter('all')}
            >
                All ({allCount})
            </Button>
            <Button
                variant={filter === 'live' ? 'primary' : 'outline'}
                size="sm"
                onClick={() => setFilter('live')}
            >
                Live ({liveCount})
            </Button>
            <Button
                variant={filter === 'prematch' ? 'primary' : 'outline'}
                size="sm"
                onClick={() => setFilter('prematch')}
            >
                Prematch ({prematchCount})
            </Button>
        </div>
    );
};

export default ArbitrageFilters;