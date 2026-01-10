import React, { useState } from 'react';
import { TrendingUp, RefreshCw, Menu, Settings, Play, Pause, RotateCw, Database, Activity } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import Button from '../common/Button';

const ArbitrageHeader = ({
                             filteredCount,
                             totalCount,
                             onRefresh,
                             loading = false
                         }) => {
    const [showMenu, setShowMenu] = useState(false);
    const navigate = useNavigate();

    const handleMenuAction = (action) => {
        setShowMenu(false);

        switch(action) {
            case 'config':
                navigate('/config');
                break;
            case 'start-polling':
                // Handle start polling
                console.log('Start polling');
                break;
            case 'stop-polling':
                // Handle stop polling
                console.log('Stop polling');
                break;
            case 'cleanup':
                // Handle cleanup
                console.log('Cleanup queues');
                break;
            default:
                break;
        }
    };

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

            <div className="flex gap-2 w-full sm:w-auto">
                <Button
                    variant="primary"
                    icon={RefreshCw}
                    onClick={onRefresh}
                    loading={loading}
                    className="flex-1 sm:flex-initial"
                >
                    Refresh
                </Button>

                {/* Menu Button */}
                <div className="relative">
                    <Button
                        variant="secondary"
                        icon={Menu}
                        onClick={() => setShowMenu(!showMenu)}
                        className="px-3"
                    />

                    {/* Dropdown Menu */}
                    {showMenu && (
                        <>
                            {/* Backdrop */}
                            <div
                                className="fixed inset-0 z-40"
                                onClick={() => setShowMenu(false)}
                            />

                            {/* Menu */}
                            <div className="absolute right-0 mt-2 w-56 bg-white rounded-lg shadow-xl border border-gray-200 z-50">
                                <div className="py-2">
                                    <MenuItem
                                        icon={Settings}
                                        label="Configuration"
                                        description="System settings"
                                        onClick={() => handleMenuAction('config')}
                                    />
                                    <MenuDivider />
                                    <MenuItem
                                        icon={Play}
                                        label="Start Polling"
                                        description="Begin auto-polling"
                                        onClick={() => handleMenuAction('start-polling')}
                                    />
                                    <MenuItem
                                        icon={Pause}
                                        label="Stop Polling"
                                        description="Pause auto-polling"
                                        onClick={() => handleMenuAction('stop-polling')}
                                    />
                                    <MenuItem
                                        icon={RotateCw}
                                        label="Restart Polling"
                                        description="Restart service"
                                        onClick={() => handleMenuAction('restart-polling')}
                                    />
                                    <MenuDivider />
                                    <MenuItem
                                        icon={Database}
                                        label="Cleanup Queues"
                                        description="Clear all queues"
                                        onClick={() => handleMenuAction('cleanup')}
                                        danger
                                    />
                                    <MenuItem
                                        icon={Activity}
                                        label="System Status"
                                        description="View health"
                                        onClick={() => handleMenuAction('status')}
                                    />
                                </div>
                            </div>
                        </>
                    )}
                </div>
            </div>
        </div>
    );
};

const MenuItem = ({ icon: Icon, label, description, onClick, danger = false }) => (
    <button
        onClick={onClick}
        className={`w-full px-4 py-3 flex items-start gap-3 hover:bg-gray-50 transition-colors ${
            danger ? 'hover:bg-red-50' : ''
        }`}
    >
        <Icon className={`mt-0.5 flex-shrink-0 ${danger ? 'text-red-600' : 'text-gray-600'}`} size={20} />
        <div className="text-left flex-1">
            <div className={`font-medium ${danger ? 'text-red-900' : 'text-gray-900'}`}>
                {label}
            </div>
            <div className="text-xs text-gray-500 mt-0.5">{description}</div>
        </div>
    </button>
);

const MenuDivider = () => <div className="border-t border-gray-200 my-1" />;

export default ArbitrageHeader;