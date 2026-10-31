// Downloads the MuseScore General SoundFont used by the score player (MIT license, about 38 MB).
// The file is not versioned: it is fetched before `npm start` and `npm run build` and verified with SHA-256.
// Set TAURUS_SKIP_SOUNDFONT=1 to skip the download, for example in jobs that do not need the player.
//
// Where the bytes come from, in order:
//   1. TAURUS_SOUNDFONT_PATH, a copy already on the build machine (a mirror on disk, an air-gapped build);
//   2. TAURUS_SOUNDFONT_URL, the controlled copy of the installation — for example a release asset of
//      this repository, which costs nothing and does not depend on a third party staying up;
//   3. the upstream mirror, which is where the file comes from in the first place.
// Whatever the source, the SHA-256 below is the gate: a copy that does not match is refused, so a
// mirror cannot quietly serve something else.
import { createHash } from 'node:crypto';
import { existsSync, mkdirSync, readFileSync, renameSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const UPSTREAM = 'https://ftp.osuosl.org/pub/musescore/soundfont/MuseScore_General/MuseScore_General.sf3';
const SHA256 = '5b85b6c2c61d10b2b91cddd41efcce7b25cd31c8271d511c73afafbef20b6fa3';
const TARGET = join(dirname(fileURLToPath(import.meta.url)), '..', 'src', 'assets', 'soundfonts', 'MuseScore_General.sf3');

const digest = (bytes) => createHash('sha256').update(bytes).digest('hex');

if (process.env.TAURUS_SKIP_SOUNDFONT === '1') {
    console.log('SoundFont download skipped (TAURUS_SKIP_SOUNDFONT=1)');
    process.exit(0);
}
if (existsSync(TARGET) && digest(readFileSync(TARGET)) === SHA256) {
    process.exit(0);
}

const fromFile = async (path) => readFileSync(path);

const fromUrl = async (url) => {
    const response = await fetch(url);
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    return Buffer.from(await response.arrayBuffer());
};

const sources = [
    process.env.TAURUS_SOUNDFONT_PATH ? { what: `the local copy ${process.env.TAURUS_SOUNDFONT_PATH}`, read: () => fromFile(process.env.TAURUS_SOUNDFONT_PATH) } : null,
    process.env.TAURUS_SOUNDFONT_URL ? { what: `the controlled copy ${process.env.TAURUS_SOUNDFONT_URL}`, read: () => fromUrl(process.env.TAURUS_SOUNDFONT_URL) } : null,
    { what: `the upstream mirror ${UPSTREAM}`, read: () => fromUrl(UPSTREAM) }
].filter(Boolean);

let bytes = null;
for (const source of sources) {
    console.log(`Reading the score player SoundFont from ${source.what}`);
    let candidate;
    try {
        candidate = await source.read();
    } catch (error) {
        console.warn(`SoundFont not available there: ${error.message}`);
        continue;
    }

    if (digest(candidate) !== SHA256) {
        // A source that serves different bytes is not a copy of this file: refuse it and say so,
        // instead of falling through silently to the next one.
        console.error(`SoundFont refused: unexpected SHA-256 from ${source.what}`);
        process.exit(1);
    }

    bytes = candidate;
    break;
}

if (!bytes) {
    console.error('SoundFont download failed: no source available. Set TAURUS_SOUNDFONT_PATH or TAURUS_SOUNDFONT_URL, or TAURUS_SKIP_SOUNDFONT=1 for a build that does not need the player.');
    process.exit(1);
}

mkdirSync(dirname(TARGET), { recursive: true });
writeFileSync(`${TARGET}.part`, bytes);
renameSync(`${TARGET}.part`, TARGET);
console.log(`SoundFont saved to ${TARGET}`);
