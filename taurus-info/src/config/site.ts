export const siteConfig = {
  name: 'Taurus',
  tagline: 'La gestione della tua organizzazione, in armonia.',
  description:
    'Taurus riunisce catalogo musicale, calendario, preparazione degli eventi, inventario, supporto alla gestione economica, persone e documenti in uno spazio di lavoro sicuro e configurabile per organizzazioni musicali.',
  appUrl: import.meta.env.PUBLIC_APP_URL ?? 'http://localhost:4200',
  contactEmail: import.meta.env.PUBLIC_CONTACT_EMAIL ?? 'ing.daniele.fundaro@gmail.com',
  navigation: [
    { label: 'Panoramica', href: '/#prodotto' },
    { label: 'Sicurezza', href: '/#sicurezza' },
    { label: 'Funzionalità', href: '/funzionalita/' },
    { label: 'Contatti', href: '/contatti/' }
  ]
} as const;
