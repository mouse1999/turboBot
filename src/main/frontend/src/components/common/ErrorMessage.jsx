import React from 'react';
import Button from './Button';
import { AlertCircle } from 'lucide-react';

const ErrorMessage = ({ message, onRetry, retryText = 'Try Again' }) => {
    return (
        <div className="bg-white rounded-xl shadow-lg p-8 max-w-md w-full">
            <div className="text-red-600 text-center">
                <div className="flex justify-center mb-4">
                    <AlertCircle size={48} />
                </div>
                <h2 className="text-xl font-bold mb-2">Error</h2>
                <p className="text-gray-600 mb-6">{message}</p>
                {onRetry && (
                    <Button variant="primary" onClick={onRetry}>
                        {retryText}
                    </Button>
                )}
            </div>
        </div>
    );
};

export default ErrorMessage;