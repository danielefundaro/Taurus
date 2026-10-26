/**
 * Carico ripetibile sulle API di Taurus, con k6.
 *
 * I profili sono quelli del Runbook di test (smoke, load, stress, spike, soak) e si scelgono con
 * `-e PROFILE=`. Le soglie sono quelle approvate: finché un valore resta `TBD` nel runbook, la
 * soglia qui è volutamente larga e serve a rilevare un peggioramento, non a certificare uno SLO.
 *
 *   k6 run -e PROFILE=smoke -e BASE_URL=http://localhost:8080 -e TOKEN=... api-load.js
 *
 * Il token è un access token valido del tenant di prova: lo stesso che usa il frontend. Senza
 * token lo script si fermasubito, perché misurare i 401 non dice nulla.
 */
import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { Trend } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const TOKEN = __ENV.TOKEN;
const PROFILE = __ENV.PROFILE || 'smoke';

/** Profili del runbook: durata e utenti simultanei di ciascuno. */
const profiles = {
    smoke: { vus: 1, duration: '30s' },
    load: { vus: 20, duration: '5m' },
    stress: { stages: [{ duration: '2m', target: 20 }, { duration: '3m', target: 80 }, { duration: '2m', target: 160 }] },
    spike: { stages: [{ duration: '30s', target: 5 }, { duration: '30s', target: 120 }, { duration: '2m', target: 5 }] },
    soak: { vus: 15, duration: '2h' }
};

const latency = {
    dashboard: new Trend('taurus_dashboard_ms', true),
    lists: new Trend('taurus_lists_ms', true),
    features: new Trend('taurus_features_ms', true)
};

export const options = {
    ...(profiles[PROFILE] || profiles.smoke),
    thresholds: {
        // Errori: nessuna richiesta fallita è accettabile in smoke e load.
        http_req_failed: ['rate<0.01'],
        // Riferimenti da rivedere quando gli SLO vengono approvati (runbook 17.1).
        'taurus_features_ms': ['p(95)<400'],
        'taurus_lists_ms': ['p(95)<800'],
        'taurus_dashboard_ms': ['p(95)<1500']
    },
    summaryTrendStats: ['avg', 'p(50)', 'p(95)', 'p(99)', 'max']
};

export function setup() {
    if (!TOKEN) throw new Error('Serve -e TOKEN=<access token del tenant di prova>');
}

const authenticated = { headers: { Authorization: `Bearer ${TOKEN}`, Accept: 'application/json' } };

const measure = (name, path, trend) => {
    const response = http.get(`${BASE_URL}${path}`, authenticated);
    trend.add(response.timings.duration);
    check(response, { [`${name} risponde 200`]: (value) => value.status === 200 });

    return response;
};

export default function () {
    group('funzionalità del tenant', () => {
        measure('tenant-features', '/api/tenant-features/current', latency.features);
    });

    group('liste paginate', () => {
        measure('tracce', '/api/tracks?page=0&size=20', latency.lists);
        measure('album', '/api/albums?page=0&size=20', latency.lists);
        measure('eventi', '/api/calendar-events?page=0&size=20', latency.lists);
    });

    group('dashboard operativa', () => {
        measure('dashboard', '/api/dashboard/operations', latency.dashboard);
    });

    sleep(1);
}
