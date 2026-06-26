/** @type {import('tailwindcss').Config} */
module.exports = {
  content: ["./src/**/*.{js,jsx}"],
  theme: {
    extend: {
      colors: {
        bg: {
          primary: '#0b0e14',
          card: '#141923',
          hover: '#1c2333',
        },
        accent: '#10b981',
        danger: '#ff4444',
        warning: '#ffaa00',
        success: '#34d399',
      },
    },
  },
  plugins: [],
};

