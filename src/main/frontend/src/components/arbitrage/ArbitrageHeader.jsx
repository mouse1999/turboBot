import React, { useState } from 'react';
import { TrendingUp, RefreshCw, Menu, Settings, Play, Pause, RotateCw, Database, Activity } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import Button from '../common/Button';
import {arbitrageApi} from "../../services/api.js";


const ArbitrageHeader = ({
                             filteredCount,
                             totalCount,
                             onRefresh,
                             loading = false
                         }) => {
    const [showMenu, setShowMenu] = useState(false);
    const [actionLoading, setActionLoading] = useState(false);
    const [isPollingActive, setIsPollingActive] = useState(false);
    const navigate = useNavigate();

    const handleMenuAction = async (action) => {
        setShowMenu(false);

        try {
            setActionLoading(true);

            switch(action) {
                case 'config':
                    navigate('/config');
                    break;
                case 'start-polling':
                    console.log('Starting orchestrator...');
                    const response = await arbitrageApi.orchestrator.startOrchestrator();
                    console.log('✅ Orchestrator started:', response);
                    setIsPollingActive(true);
                    alert('Orchestrator started successfully!');
                    break;
                case 'stop-polling':
                    console.log('Stop polling');
                    setIsPollingActive(false);
                    // TODO: Implement stop polling endpoint
                    break;
                case 'restart-polling':
                    console.log('Restart polling');
                    // TODO: Implement restart polling endpoint
                    break;
                case 'cleanup':
                    console.log('Cleaning up queues...');
                    await arbitrageApi.orchestrator.forceCleanup();
                    console.log('✅ Queues cleaned up');
                    alert('Queues cleaned up successfully!');
                    break;
                case 'status':
                    console.log('Checking system status...');
                    const status = await arbitrageApi.orchestrator.getStatus();
                    console.log('System status:', status);
                    // You might want to navigate to a status page or show a modal
                    break;
                default:
                    break;
            }
        } catch (error) {
            console.error(`❌ Error executing ${action}:`, error);
            alert(`Error: ${error.message}`);
        } finally {
            setActionLoading(false);
        }
    };

    return (
        <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-2">
            <div>
                <h1 className="text-lg sm:text-xl font-bold text-gray-900 flex items-center gap-2">
                    <TrendingUp className="text-blue-600" size={20} />
                    Arbitrage Opportunities
                </h1>
                <p className="text-xs text-gray-600 mt-0.5">
                    {filteredCount} of {totalCount} opportunities showing
                </p>
            </div>

            <div className="flex gap-1.5 w-full sm:w-auto">
                <Button
                    variant="primary"
                    icon={RefreshCw}
                    onClick={onRefresh}
                    loading={loading || actionLoading}
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
                        className="px-2"
                        disabled={actionLoading}
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
                            <div className="absolute right-0 mt-1 w-52 bg-white rounded-lg shadow-xl border border-gray-200 z-50">
                                <div className="py-1">
                                    <MenuItem
                                        icon={Settings}
                                        label="Configuration"
                                        description="System settings"
                                        onClick={() => handleMenuAction('config')}
                                    />
                                    <MenuDivider />
                                    <MenuItem
                                        icon={Play}
                                        label="Start Orchestrator"
                                        description="Initialize Betting Engine"
                                        onClick={() => handleMenuAction('start-polling')}
                                        isActive={isPollingActive}
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

const MenuItem = ({ icon: Icon, label, description, onClick, danger = false, isActive = false }) => (
    <button
        onClick={onClick}
        className={`w-full px-3 py-2 flex items-start gap-2 hover:bg-gray-50 transition-colors ${
            danger ? 'hover:bg-red-50' : ''
        }`}
    >
        <div className="relative mt-0.5 flex-shrink-0">
            <Icon className={`${danger ? 'text-red-600' : 'text-gray-600'}`} size={16} />
            {isActive && (
                <span className="absolute -top-1 -right-1 flex h-2 w-2">
                    <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-green-400 opacity-75"></span>
                    <span className="relative inline-flex rounded-full h-2 w-2 bg-green-500"></span>
                </span>
            )}
        </div>
        <div className="text-left flex-1">
            <div className={`text-sm font-medium ${danger ? 'text-red-900' : 'text-gray-900'} ${isActive ? 'text-green-700' : ''}`}>
                {label}
            </div>
            <div className="text-xs text-gray-500 mt-0.5">{description}</div>
        </div>
    </button>
);

const MenuDivider = () => <div className="border-t border-gray-200 my-0.5" />;

export default ArbitrageHeader;