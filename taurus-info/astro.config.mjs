import sitemap from '@astrojs/sitemap';
import tailwindcss from '@tailwindcss/vite';
import { defineConfig } from 'astro/config';

const site = process.env.SITE_URL ?? 'http://localhost:4321';

export default defineConfig({
  site,
  output: 'static',
  // The pages that only redirect to the application stay out of the sitemap.
  integrations: [sitemap({ filter: (page) => !/\/(termini|privacy)-applicativo\/?$/.test(new URL(page).pathname) })],
  vite: {
    plugins: [tailwindcss()]
  }
});
