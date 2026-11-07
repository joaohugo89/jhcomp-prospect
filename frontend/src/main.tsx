import '@fontsource-variable/fraunces';
import '@fontsource-variable/inter';
import '@fontsource-variable/jetbrains-mono';
import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';

import { App } from './App';
import { initTheme } from './app/theme';
import './index.css';

// Before the first render: without this the screen flashes in light mode until React mounts.
initTheme();

const container = document.getElementById('root');

if (!container) {
  throw new Error('Elemento #root nao encontrado em index.html');
}

createRoot(container).render(
  <StrictMode>
    <App />
  </StrictMode>,
);
