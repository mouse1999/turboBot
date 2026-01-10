import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import {
    ArrowLeft, Save, Settings, Users, Clock,
    DollarSign, CheckCircle, AlertCircle,
    Sliders, Bell, Shield, Zap, Info
} from 'lucide-react';
import { arbitrageApi } from '../services/api';

const ConfigPage = () => {
    const navigate = useNavigate();
    const [loading, setLoading] = useState(true);
    const [saving, setSaving] = useState(false);
    const [message, setMessage] = useState(null);
    const [dirty, setDirty] = useState(false);

    const [config, setConfig] = useState({
        pollingEnabled: true,
        pollingInterval: 5000,
        minProfit: 5,
        allowedBookmakers: ['BET365', 'BETWAY', 'SPORTYBET'],
        validateAllStakes: true,
        notificationEnabled: true,
        autoPlaceBets: false,
        maxBetAmount: 1000,
        soundAlert: true
    });

    const availableBookmakers = [
        { id: 'BET365', name: 'Bet365', color: 'bg-green-500' },
        { id: 'BETWAY', name: 'Betway', color: 'bg-purple-500' },
        { id: 'SPORTYBET', name: 'SportyBet', color: 'bg-orange-500' },
        { id: '1XBET', name: '1xBet', color: 'bg-red-500' },
        { id: 'MELBET', name: 'MelBet', color: 'bg-blue-500' },
        { id: 'PARIMATCH', name: 'Parimatch', color: 'bg-yellow-500' },
        { id: '22BET', name: '22Bet', color: 'bg-cyan-500' }
    ];

    // Polling intervals in milliseconds
    const pollingIntervals = [
        { value: 2000, label: '2 seconds (Ultra Fast)' },
        { value: 5000, label: '5 seconds (Fast)' },
        { value: 10000, label: '10 seconds (Balanced)' },
        { value: 30000, label: '30 seconds (Conservative)' },
        { value: 60000, label: '1 minute (Slow)' }
    ];

    const profitOptions = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10];

    useEffect(() => {
        loadCurrentConfig();
    }, []);

    const loadCurrentConfig = async () => {
        setLoading(true);
        try {
            const status = await arbitrageApi.polling.getStatus();
            setConfig(prev => ({
                ...prev,
                pollingEnabled: status.running,
                pollingInterval: status.intervalMs,
                minProfit: status.minProfitPercentage,
                allowedBookmakers: Array.from(status.allowedBookmakers || [])
            }));
            setDirty(false);
        } catch (error) {
            console.error('Failed to load config:', error);
            showMessage('Failed to load configuration', 'error');
        } finally {
            setLoading(false);
        }
    };

    const handleConfigChange = (key, value) => {
        setConfig(prev => ({ ...prev, [key]: value }));
        setDirty(true);
    };

    const handleSave = async () => {
        if (!dirty) {
            showMessage('No changes to save', 'info');
            return;
        }

        setSaving(true);
        setMessage(null);

        try {
            await Promise.all([
                arbitrageApi.polling.updateBookmakers(config.allowedBookmakers),
                arbitrageApi.polling.updateMinProfit(config.minProfit),
                config.pollingEnabled
                    ? arbitrageApi.polling.start()
                    : arbitrageApi.polling.stop()
            ]);

            showMessage('Configuration saved successfully!', 'success');
            setDirty(false);

            // Reload config to ensure sync
            setTimeout(loadCurrentConfig, 1000);
        } catch (error) {
            console.error('Failed to save config:', error);
            showMessage('Failed to save configuration', 'error');
        } finally {
            setSaving(false);
        }
    };

    const showMessage = (text, type) => {
        setMessage({ text, type });
        setTimeout(() => setMessage(null), 5000);
    };

    const toggleBookmaker = (bookmakerId) => {
        handleConfigChange(
            'allowedBookmakers',
            config.allowedBookmakers.includes(bookmakerId)
                ? config.allowedBookmakers.filter(b => b !== bookmakerId)
                : [...config.allowedBookmakers, bookmakerId]
        );
    };

    const handleReset = () => {
        if (window.confirm('Reset to default settings?')) {
            setConfig({
                pollingEnabled: true,
                pollingInterval: 5000,
                minProfit: 5,
                allowedBookmakers: ['BET365', 'BETWAY', 'SPORTYBET'],
                validateAllStakes: true,
                notificationEnabled: true,
                autoPlaceBets: false,
                maxBetAmount: 1000,
                soundAlert: true
            });
            setDirty(true);
        }
    };

    const selectAllBookmakers = () => {
        handleConfigChange(
            'allowedBookmakers',
            availableBookmakers.map(b => b.id)
        );
    };

    const deselectAllBookmakers = () => {
        handleConfigChange('allowedBookmakers', []);
    };

    if (loading) {
        return (
            <div className="min-h-screen bg-gradient-to-br from-gray-50 to-gray-100 flex items-center justify-center">
                <div className="text-center space-y-4">
                    <div className="animate-spin rounded-full h-16 w-16 border-4 border-blue-600 border-t-transparent mx-auto"></div>
                    <div>
                        <p className="text-gray-700 font-medium">Loading configuration</p>
                        <p className="text-sm text-gray-500">Please wait...</p>
                    </div>
                </div>
            </div>
        );
    }

    return (
        <div className="min-h-screen bg-gradient-to-br from-gray-50 to-gray-100">
            {/* Header */}
            <header className="bg-white shadow-sm border-b border-gray-200">
                <div className="max-w-6xl mx-auto px-4 sm:px-6 lg:px-8 py-6">
                    <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
                        <div className="flex items-center gap-4">
                            <button
                                onClick={() => navigate('/arbitrage')}
                                className="p-2 hover:bg-gray-100 rounded-lg transition-colors group"
                                title="Back to arbitrage"
                            >
                                <ArrowLeft size={24} className="text-gray-600 group-hover:text-gray-900" />
                            </button>
                            <div className="flex items-center gap-3">
                                <div className="p-2 bg-blue-50 rounded-lg">
                                    <Sliders className="text-blue-600" size={24} />
                                </div>
                                <div>
                                    <h1 className="text-2xl font-bold text-gray-900">Configuration</h1>
                                    <p className="text-gray-600 text-sm">
                                        Manage your arbitrage bot settings
                                    </p>
                                </div>
                            </div>
                        </div>

                        <div className="flex items-center gap-2">
                            {dirty && (
                                <span className="px-3 py-1 bg-yellow-100 text-yellow-800 text-sm font-medium rounded-full flex items-center gap-1">
                                    <Info size={14} />
                                    Unsaved changes
                                </span>
                            )}
                        </div>
                    </div>
                </div>
            </header>

            {/* Content */}
            <main className="max-w-6xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
                {/* Message Alert */}
                {message && (
                    <div className={`mb-6 animate-fade-in p-4 rounded-lg border-l-4 flex items-start gap-3 ${
                        message.type === 'success' ? 'border-l-green-500 bg-green-50' :
                            message.type === 'error' ? 'border-l-red-500 bg-red-50' :
                                'border-l-blue-500 bg-blue-50'
                    }`}>
                        {message.type === 'success' ? (
                            <CheckCircle className="flex-shrink-0 mt-0.5" size={20} />
                        ) : message.type === 'error' ? (
                            <AlertCircle className="flex-shrink-0 mt-0.5" size={20} />
                        ) : (
                            <Info className="flex-shrink-0 mt-0.5" size={20} />
                        )}
                        <div>
                            <span className="font-medium">{message.text}</span>
                            {message.type === 'error' && (
                                <p className="text-sm opacity-90 mt-1">Please try again or contact support</p>
                            )}
                        </div>
                    </div>
                )}

                <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
                    {/* Left Column */}
                    <div className="lg:col-span-2 space-y-6">
                        {/* Polling Settings */}
                        <ConfigCard
                            icon={Clock}
                            title="Polling Settings"
                            description="Configure how often we scan for opportunities"
                            badge={config.pollingEnabled ? "Active" : "Paused"}
                        >
                            <div className="space-y-5">
                                <ToggleField
                                    label="Enable Automatic Polling"
                                    description="Continuously scan for arbitrage opportunities"
                                    checked={config.pollingEnabled}
                                    onChange={(checked) => handleConfigChange('pollingEnabled', checked)}
                                    icon={Zap}
                                />

                                <div className="space-y-2">
                                    <label className="block text-sm font-medium text-gray-700">
                                        Polling Frequency
                                    </label>
                                    <p className="text-xs text-gray-500 mb-3">
                                        Faster polling finds opportunities quicker but uses more resources
                                    </p>
                                    <div className="grid grid-cols-2 md:grid-cols-5 gap-2">
                                        {pollingIntervals.map(interval => (
                                            <button
                                                key={interval.value}
                                                onClick={() => handleConfigChange('pollingInterval', interval.value)}
                                                className={`p-3 rounded-lg border-2 text-sm transition-all ${
                                                    config.pollingInterval === interval.value
                                                        ? 'border-blue-500 bg-blue-50 text-blue-700 font-medium'
                                                        : 'border-gray-200 hover:border-gray-300 hover:bg-gray-50 text-gray-700'
                                                }`}
                                            >
                                                {interval.label.split(' ')[0]}
                                            </button>
                                        ))}
                                    </div>
                                    <p className="text-xs text-gray-500 mt-2">
                                        Current: {config.pollingInterval / 1000} seconds
                                    </p>
                                </div>
                            </div>
                        </ConfigCard>

                        {/* Bookmaker Selection */}
                        <ConfigCard
                            icon={Users}
                            title="Bookmaker Selection"
                            description="Choose which bookmakers to monitor"
                            badge={`${config.allowedBookmakers.length} selected`}
                        >
                            <div className="space-y-4">
                                <div className="flex gap-2 mb-4">
                                    <button
                                        onClick={selectAllBookmakers}
                                        className="px-4 py-2 text-sm bg-gray-100 hover:bg-gray-200 rounded-lg transition-colors"
                                    >
                                        Select All
                                    </button>
                                    <button
                                        onClick={deselectAllBookmakers}
                                        className="px-4 py-2 text-sm bg-gray-100 hover:bg-gray-200 rounded-lg transition-colors"
                                    >
                                        Deselect All
                                    </button>
                                </div>

                                <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                                    {availableBookmakers.map(bookmaker => {
                                        const isSelected = config.allowedBookmakers.includes(bookmaker.id);
                                        return (
                                            <div
                                                key={bookmaker.id}
                                                onClick={() => toggleBookmaker(bookmaker.id)}
                                                className={`p-4 rounded-xl border-2 cursor-pointer transition-all hover:shadow-md ${
                                                    isSelected
                                                        ? 'border-blue-500 bg-blue-50'
                                                        : 'border-gray-200 hover:border-gray-300'
                                                }`}
                                            >
                                                <div className="flex items-center justify-between">
                                                    <div className="flex items-center gap-3">
                                                        <div className={`w-3 h-3 rounded-full ${bookmaker.color}`}></div>
                                                        <div>
                                                            <div className="font-medium text-gray-900">{bookmaker.name}</div>
                                                            <div className="text-sm text-gray-500">
                                                                {isSelected ? 'Active monitoring' : 'Not monitoring'}
                                                            </div>
                                                        </div>
                                                    </div>
                                                    <div className={`w-5 h-5 rounded border-2 flex items-center justify-center transition-colors ${
                                                        isSelected
                                                            ? 'border-blue-500 bg-blue-500'
                                                            : 'border-gray-300'
                                                    }`}>
                                                        {isSelected && (
                                                            <CheckCircle size={12} className="text-white" />
                                                        )}
                                                    </div>
                                                </div>
                                            </div>
                                        );
                                    })}
                                </div>
                            </div>
                        </ConfigCard>

                        {/* Risk Management */}
                        <ConfigCard
                            icon={Shield}
                            title="Risk Management"
                            description="Configure bet validation and limits"
                        >
                            <div className="space-y-5">
                                <ToggleField
                                    label="Validate All Stakes"
                                    description="Require stakes for all bookmakers before placing bets"
                                    checked={config.validateAllStakes}
                                    onChange={(checked) => handleConfigChange('validateAllStakes', checked)}
                                />

                                <div>
                                    <label className="block text-sm font-medium text-gray-700 mb-2">
                                        Maximum Bet Amount ($)
                                    </label>
                                    <div className="flex items-center gap-4">
                                        <input
                                            type="range"
                                            min="10"
                                            max="5000"
                                            step="10"
                                            value={config.maxBetAmount}
                                            onChange={(e) => handleConfigChange('maxBetAmount', parseInt(e.target.value))}
                                            className="flex-1 h-2 bg-gray-200 rounded-lg appearance-none cursor-pointer"
                                        />
                                        <span className="text-lg font-semibold text-gray-900 min-w-[80px]">
                                            ${config.maxBetAmount.toLocaleString()}
                                        </span>
                                    </div>
                                    <div className="flex justify-between text-xs text-gray-500 mt-2">
                                        <span>$10</span>
                                        <span>$1,000</span>
                                        <span>$5,000</span>
                                    </div>
                                </div>
                            </div>
                        </ConfigCard>
                    </div>

                    {/* Right Column */}
                    <div className="space-y-6">
                        {/* Profit Settings */}
                        <ConfigCard
                            icon={DollarSign}
                            title="Profit Settings"
                            description="Set your minimum profit threshold"
                        >
                            <div className="space-y-4">
                                <div className="text-center mb-4">
                                    <div className="text-4xl font-bold text-gray-900">{config.minProfit}%</div>
                                    <div className="text-sm text-gray-500">Minimum Profit</div>
                                </div>

                                <div className="space-y-2">
                                    <div className="grid grid-cols-5 gap-2">
                                        {profitOptions.map(profit => (
                                            <button
                                                key={profit}
                                                onClick={() => handleConfigChange('minProfit', profit)}
                                                className={`py-3 rounded-lg border-2 transition-all ${
                                                    config.minProfit === profit
                                                        ? 'border-green-500 bg-green-50 text-green-700 font-bold'
                                                        : 'border-gray-200 hover:border-gray-300 text-gray-700'
                                                }`}
                                            >
                                                {profit}%
                                            </button>
                                        ))}
                                    </div>
                                    <div className="flex justify-between text-xs text-gray-500">
                                        <span>Low Risk</span>
                                        <span>Balanced</span>
                                        <span>High Risk</span>
                                    </div>
                                </div>

                                <div className="pt-4 border-t border-gray-200">
                                    <label className="block text-sm font-medium text-gray-700 mb-2">
                                        Custom Profit (%)
                                    </label>
                                    <div className="flex gap-2">
                                        <input
                                            type="number"
                                            min="0.1"
                                            max="50"
                                            step="0.1"
                                            value={config.minProfit}
                                            onChange={(e) => handleConfigChange('minProfit', parseFloat(e.target.value))}
                                            className="flex-1 px-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                                        />
                                        <button
                                            onClick={() => handleConfigChange('minProfit', 5)}
                                            className="px-4 py-2 text-sm border border-gray-300 rounded-lg hover:bg-gray-50"
                                        >
                                            Reset
                                        </button>
                                    </div>
                                </div>
                            </div>
                        </ConfigCard>

                        {/* Notifications */}
                        <ConfigCard
                            icon={Bell}
                            title="Notifications"
                            description="Alerts and sounds"
                        >
                            <div className="space-y-4">
                                <ToggleField
                                    label="Enable Notifications"
                                    description="Show browser notifications for new opportunities"
                                    checked={config.notificationEnabled}
                                    onChange={(checked) => handleConfigChange('notificationEnabled', checked)}
                                />

                                <ToggleField
                                    label="Sound Alerts"
                                    description="Play sound when opportunity is found"
                                    checked={config.soundAlert}
                                    onChange={(checked) => handleConfigChange('soundAlert', checked)}
                                />

                                <ToggleField
                                    label="Auto-place Bets"
                                    description="Automatically place bets when opportunity is found (Use with caution)"
                                    checked={config.autoPlaceBets}
                                    onChange={(checked) => handleConfigChange('autoPlaceBets', checked)}
                                    warning
                                />
                            </div>
                        </ConfigCard>

                        {/* Actions */}
                        <div className="bg-white rounded-xl border border-gray-200 p-6">
                            <h3 className="text-lg font-semibold text-gray-900 mb-4">Actions</h3>
                            <div className="space-y-3">
                                <button
                                    onClick={handleSave}
                                    disabled={!dirty || saving}
                                    className="w-full py-3 bg-gradient-to-r from-blue-600 to-blue-700 text-white rounded-lg hover:from-blue-700 hover:to-blue-800 transition-all font-medium shadow-sm hover:shadow-md disabled:opacity-50 disabled:cursor-not-allowed flex items-center justify-center gap-2"
                                >
                                    {saving ? (
                                        <>
                                            <div className="animate-spin rounded-full h-5 w-5 border-b-2 border-white"></div>
                                            Saving...
                                        </>
                                    ) : (
                                        <>
                                            <Save size={20} />
                                            {dirty ? 'Save Changes' : 'All Changes Saved'}
                                        </>
                                    )}
                                </button>

                                <button
                                    onClick={loadCurrentConfig}
                                    className="w-full py-3 border border-gray-300 rounded-lg hover:bg-gray-50 transition-colors font-medium text-gray-700"
                                >
                                    Reload Configuration
                                </button>

                                <button
                                    onClick={handleReset}
                                    className="w-full py-3 text-red-600 hover:bg-red-50 border border-red-200 rounded-lg transition-colors font-medium"
                                >
                                    Reset to Defaults
                                </button>
                            </div>

                            <div className="mt-6 pt-6 border-t border-gray-200">
                                <div className="flex items-center justify-between text-sm">
                                    <span className="text-gray-600">Last Updated</span>
                                    <span className="text-gray-900 font-medium">Just now</span>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            </main>
        </div>
    );
};

// Helper Components
const ConfigCard = ({ icon: Icon, title, description, badge, children }) => (
    <div className="bg-white rounded-xl border border-gray-200 hover:border-gray-300 transition-colors">
        <div className="p-6">
            <div className="flex items-start justify-between mb-4">
                <div className="flex items-center gap-3">
                    <div className="p-2 bg-blue-50 rounded-lg">
                        <Icon className="text-blue-600" size={20} />
                    </div>
                    <div>
                        <h2 className="text-lg font-semibold text-gray-900">{title}</h2>
                        {description && (
                            <p className="text-sm text-gray-600 mt-1">{description}</p>
                        )}
                    </div>
                </div>
                {badge && (
                    <span className="px-3 py-1 bg-gray-100 text-gray-700 text-sm font-medium rounded-full">
                        {badge}
                    </span>
                )}
            </div>
            <div className="mt-4">
                {children}
            </div>
        </div>
    </div>
);

const ToggleField = ({ label, description, checked, onChange, icon: Icon, warning }) => (
    <div className={`flex items-center justify-between p-3 rounded-lg ${warning ? 'bg-red-50 border border-red-100' : 'bg-gray-50'}`}>
        <div className="flex-1">
            <div className="flex items-center gap-2">
                {Icon && <Icon size={16} className="text-gray-500" />}
                <div className="font-medium text-gray-900">{label}</div>
            </div>
            {description && (
                <div className={`text-sm mt-1 ${warning ? 'text-red-600' : 'text-gray-500'}`}>
                    {description}
                </div>
            )}
        </div>
        <button
            onClick={() => onChange(!checked)}
            className={`relative inline-flex h-7 w-12 items-center rounded-full transition-colors ${
                checked ? warning ? 'bg-red-600' : 'bg-green-500' : 'bg-gray-300'
            }`}
        >
            <span
                className={`inline-block h-5 w-5 transform rounded-full bg-white transition-transform shadow-sm ${
                    checked ? 'translate-x-6' : 'translate-x-1'
                }`}
            />
        </button>
    </div>
);

export default ConfigPage;