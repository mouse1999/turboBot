import React from 'react';
import { Routes, Route, Navigate } from 'react-router-dom';
import ArbitragePage from './pages/ArbitragePage';
import ConfigPage from './pages/ConfigPage';

const App = () => {
    return (
        <Routes>
            {/* Main routes */}
            <Route path="/" element={<Navigate to="/arbitrage" replace />} />
            <Route path="/arbitrage" element={<ArbitragePage />} />
            <Route path="/config" element={<ConfigPage />} />

            {/* 404 Not Found */}
            <Route path="*" element={<Navigate to="/arbitrage" replace />} />
        </Routes>
    );
};

export default App;