export const siteConfig = {
  name: 'Taurus',
  tagline: 'La gestione della tua organizzazione, in armonia.',
  description:
    'Taurus riunisce catalogo musicale, calendario, inventario, gestione economica, persone e documenti in un unico spazio di lavoro sicuro per organizzazioni musicali.',
  appUrl: import.meta.env.PUBLIC_APP_URL ?? 'http://localhost:4200',
  contactEmail: import.meta.env.PUBLIC_CONTACT_EMAIL ?? 'ing.daniele.fundaro@gmail.com',
  navigation: [
    { label: 'Panoramica', href: '/#prodotto' },
    { label: 'Funzionalità', href: '/funzionalita/' },
    { label: 'Sicurezza', href: '/#sicurezza' },
    { label: 'Contatti', href: '/contatti/' }
  ]
} as const;
