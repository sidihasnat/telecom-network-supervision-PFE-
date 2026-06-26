import React, { useState } from 'react';
import { Shield } from 'lucide-react';

function Login({ onLogin }) {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!username || !password) return;

    setLoading(true);
    setError('');

    try {
      const res = await fetch('/api/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username, password }),
      });

      const data = await res.json();

      if (!res.ok) {
        setError(data.error || 'Login failed');
        setLoading(false);
        return;
      }

      // Save token and user info
      localStorage.setItem('token', data.token);
      localStorage.setItem('user', JSON.stringify({
        username: data.username,
        role: data.role,
        fullName: data.fullName,
      }));

      onLogin(data);
    } catch (err) {
      setError('Cannot connect to server');
    }

    setLoading(false);
  };

  return (
    <div className="min-h-screen bg-bg-primary flex items-center justify-center">
      <div className="w-full max-w-sm">
        {/* Logo */}
        <div className="text-center mb-8">
          <div className="inline-flex items-center justify-center w-16 h-16 bg-accent/10 rounded-2xl mb-4">
            <Shield className="text-accent" size={32} />
          </div>
          <h1 className="text-2xl font-bold text-gray-100">TelecomAI</h1>
          <p className="text-sm text-gray-500 mt-1">Network Supervision & Threat Detection</p>
        </div>

        {/* Login Form */}
        <div className="bg-bg-card rounded-xl border border-gray-800 p-6">
          <h2 className="text-lg font-semibold text-gray-200 mb-6">Sign In</h2>

          {error && (
            <div className="mb-4 p-3 bg-danger/10 border border-danger/20 rounded-lg text-sm text-danger">
              {error}
            </div>
          )}

          <div className="space-y-4">
            <div>
              <label className="block text-xs text-gray-500 mb-1.5">Username</label>
              <input
                type="text"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                onKeyDown={(e) => e.key === 'Enter' && handleSubmit(e)}
                placeholder="admin"
                autoFocus
                className="w-full bg-bg-primary border border-gray-700 rounded-lg px-4 py-2.5
                         text-sm text-gray-200 placeholder-gray-600
                         focus:outline-none focus:border-accent/50 transition-colors"
              />
            </div>

            <div>
              <label className="block text-xs text-gray-500 mb-1.5">Password</label>
              <input
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                onKeyDown={(e) => e.key === 'Enter' && handleSubmit(e)}
                placeholder="••••••••"
                className="w-full bg-bg-primary border border-gray-700 rounded-lg px-4 py-2.5
                         text-sm text-gray-200 placeholder-gray-600
                         focus:outline-none focus:border-accent/50 transition-colors"
              />
            </div>

            <button
              onClick={handleSubmit}
              disabled={loading || !username || !password}
              className="w-full py-2.5 bg-accent text-white rounded-lg font-medium text-sm
                       hover:bg-accent/90 transition-colors disabled:opacity-40 disabled:cursor-not-allowed"
            >
              {loading ? 'Signing in...' : 'Sign In'}
            </button>
          </div>

          <p className="text-xs text-gray-600 text-center mt-6">
            Default: admin / admin123
          </p>
        </div>
      </div>
    </div>
  );
}

export default Login;
