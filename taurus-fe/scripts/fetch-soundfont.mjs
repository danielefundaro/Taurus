// Downloads the MuseScore General SoundFont used by the score player (MIT license, about 38 MB).
// The file is not versioned: it is fetched before `npm start` and `npm run build` and verified with SHA-256.
// Set TAURUS_SKIP_SOUNDFONT=1 to skip the download, for example in jobs that do not need the player.
import { createHash } from 'node:crypto';
import { existsSync, mkdirSync, readFileSync, renameSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const SOURCE = 'https://ftp.osuosl.org/pub/musescore/soundfont/MuseScore_General/MuseScore_General.sf3';
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

console.log(`Downloading the score player SoundFont from ${SOURCE}`);
const response = await fetch(SOURCE);
if (!response.ok) {
    console.error(`SoundFont download failed: HTTP ${response.status}`);
    process.exit(1);
}
const bytes = Buffer.from(await response.arrayBuffer());
if (digest(bytes) !== SHA256) {
    console.error('SoundFont download failed: unexpected SHA-256');
    process.exit(1);
}
mkdirSync(dirname(TARGET), { recursive: true });
writeFileSync(`${TARGET}.part`, bytes);
renameSync(`${TARGET}.part`, TARGET);
console.log(`SoundFont saved to ${TARGET}`);
