# Componenti di terze parti

Il backend include nel JAR, e quindi nell'immagine pubblicata, le librerie Java scaricate da Maven Central durante la build, senza modifiche. Quasi tutte hanno licenze permissive (Apache-2.0, MIT, BSD, EPL-2.0). Qui sono annotate quelle con condizioni diverse.

| Componente | Versione | Licenza | Sorgenti | Uso |
| --- | --- | --- | --- | --- |
| Liquibase Community (`liquibase-core`) | 5.0.4, gestita da Spring Boot | FSL-1.1-ALv2 (Functional Source License 1.1, con licenza futura Apache-2.0); Copyright © 2025 Liquibase Inc. | https://github.com/liquibase/liquibase/tree/v5.0.4, termini in https://github.com/liquibase/liquibase/blob/v5.0.4/LICENSE.txt | migrazioni dello schema all'avvio e alla creazione di un tenant |

## Liquibase e la Functional Source License

La FSL permette ogni uso tranne quello concorrente: offrire ad altri, in un prodotto o servizio commerciale, qualcosa che sostituisce Liquibase o ne ripete le funzioni. Taurus usa Liquibase solo per aggiornare il proprio database, quindi l'uso è ammesso.

Chi ridistribuisce il software deve includere i termini, o un collegamento ai termini, e non togliere le note di copyright. Per questo il `Dockerfile` copia questo file nell'immagine, accanto al JAR. Due anni dopo la pubblicazione di ciascuna versione, la stessa versione si può usare anche con la licenza Apache-2.0.

Il plugin Maven di Liquibase, con la stessa licenza, serve solo in sviluppo e non entra nell'immagine. Lo stesso vale per `liquibase-hibernate7`, con licenza Apache-2.0, che il plugin usa per confrontare lo schema con le entità.

La scelta è del 2026-11-07 ed è registrata nella sezione Licenze di `docs/platform-upgrade-spec.md`. A ogni versione maggiore di una dipendenza, la licenza si ricontrolla qui.
