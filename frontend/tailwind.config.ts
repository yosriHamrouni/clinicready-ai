import type { Config } from 'tailwindcss';

const token = (name: string) => `hsl(var(--${name}) / <alpha-value>)`;

export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        background: token('background'),
        foreground: token('foreground'),
        card: { DEFAULT: token('card'), foreground: token('card-foreground') },
        muted: { DEFAULT: token('muted'), foreground: token('muted-foreground') },
        primary: {
          DEFAULT: token('primary'),
          foreground: token('primary-foreground'),
          soft: token('primary-soft'),
        },
        accent: { DEFAULT: token('accent'), foreground: token('accent-foreground') },
        destructive: {
          DEFAULT: token('destructive'),
          foreground: token('destructive-foreground'),
        },
        border: token('border'),
        input: token('input'),
        ring: token('ring'),
      },
      borderRadius: {
        lg: 'var(--radius)', // 8px cards
        md: '0.375rem', // 6px inputs
        sm: '0.25rem',
      },
      fontFamily: { sans: ['Inter', 'system-ui', 'sans-serif'] },
      keyframes: {
        'fade-in': { from: { opacity: '0' }, to: { opacity: '1' } },
        'fade-out': { from: { opacity: '1' }, to: { opacity: '0' } },
        'sheet-in': {
          from: { transform: 'translateX(-100%)' },
          to: { transform: 'translateX(0)' },
        },
        'sheet-out': {
          from: { transform: 'translateX(0)' },
          to: { transform: 'translateX(-100%)' },
        },
        'sheet-in-rtl': {
          from: { transform: 'translateX(100%)' },
          to: { transform: 'translateX(0)' },
        },
        'sheet-out-rtl': {
          from: { transform: 'translateX(0)' },
          to: { transform: 'translateX(100%)' },
        },
      },
      animation: {
        'fade-in': 'fade-in 150ms ease-out',
        'fade-out': 'fade-out 150ms ease-in',
        'sheet-in': 'sheet-in 200ms ease-out',
        'sheet-out': 'sheet-out 150ms ease-in',
        'sheet-in-rtl': 'sheet-in-rtl 200ms ease-out',
        'sheet-out-rtl': 'sheet-out-rtl 150ms ease-in',
      },
    },
  },
  plugins: [],
} satisfies Config;
