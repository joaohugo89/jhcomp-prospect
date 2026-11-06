import '@fontsource-variable/fraunces';
import '@fontsource-variable/inter';
import '@fontsource-variable/jetbrains-mono';
import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';

import { App } from './App';
import { initTheme } from './app/theme';
import './index.css';

// Antes do primeiro render: sem isto a tela pisca no modo claro ate o React montar.
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
