/** @type {import('tailwindcss').Config} */
module.exports = {
  content: [
    './app/**/*.{js,ts,jsx,tsx,mdx}',
    './components/**/*.{js,ts,jsx,tsx,mdx}',
  ],
  theme: {
    extend: {
      fontFamily: {
        sans: ['Inter', 'system-ui', 'sans-serif'],
      },
      colors: {
        bg: '#0d1117',
        surface: '#161b22',
        border: '#30363d',
        primary: '#58a6ff',
        accent: '#3fb950',
        warn: '#d29922',
        danger: '#f85149',
        muted: '#8b949e',
      },
    },
  },
  plugins: [],
};
