import React from 'react';
import { TrendingUp, RefreshCw } from 'lucide-react';
import Button from '../common/Button';

const ArbitrageHeader = ({
                             filteredCount,
                             totalCount,
                             onRefresh,
                             loading = false
                         }) => {
    return (
        <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
            <div>
                <h1 className="text-2xl sm:text-3xl font-bold text-gray-900 flex items-center gap-3">
                    <TrendingUp className="text-blue-600" size={28} />
                    Arbitrage Opportunities
                </h1>
                <p className="text-gray-600 mt-1">
                    {filteredCount} of {totalCount} opportunities showing
                </p>
            </div>
            <Button
                variant="primary"
                icon={RefreshCw}
                onClick={onRefresh}
                loading={loading}
                className="w-full sm:w-auto"
            >
                Refresh
            </Button>
        </div>
    );
};

export default ArbitrageHeader;