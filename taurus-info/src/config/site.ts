export const siteConfig = {
  name: 'Taurus',
  tagline: 'Il patrimonio musicale, finalmente in armonia.',
  description:
    'Taurus organizza cataloghi musicali, tracce, strumenti, documenti ed eventi in un unico spazio di lavoro sicuro e multi-tenant.',
  appUrl: import.meta.env.PUBLIC_APP_URL ?? 'http://localhost:4200',
  contactEmail: import.meta.env.PUBLIC_CONTACT_EMAIL ?? 'admin@taurus.it',
  navigation: [
    { label: 'Prodotto', href: '/#prodotto' },
    { label: 'Sicurezza', href: '/#sicurezza' },
    { label: 'Funzionalità', href: '/funzionalita/' },
    { label: 'Contatti', href: '/contatti/' }
  ]
} as const;
