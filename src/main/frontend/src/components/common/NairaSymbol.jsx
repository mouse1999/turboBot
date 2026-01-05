// src/components/common/NairaSymbol.jsx
import React from 'react';

const NairaSymbol = ({ size = 1, className = '' }) => {
    return (
        <span className={`font-bold ${size} ${className}`}>
      ₦
    </span>
    );
};

export default NairaSymbol;