import '@fontsource-variable/fraunces';
import '@fontsource-variable/inter';
import '@fontsource-variable/jetbrains-mono';
import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';

import { App } from './App';
import './index.css';

const container = document.getElementById('root');

if (!container) {
  throw new Error('Elemento #root nao encontrado em index.html');
}

createRoot(container).render(
  <StrictMode>
    <App />
  </StrictMode>,
);
