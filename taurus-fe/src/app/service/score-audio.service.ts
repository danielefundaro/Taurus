import { Injectable, OnDestroy } from '@angular/core';
import type { Sequencer, WorkletSynthesizer } from 'spessasynth_lib';
import { PlaybackChannel } from '../score-player/playback-builder';

const WORKLET_URL = 'assets/spessasynth/spessasynth_processor.min.js';
const SOUNDFONT_URL = 'assets/soundfonts/MuseScore_General.sf3';

interface AudioEngine {
    context: AudioContext;
    synth: WorkletSynthesizer;
    sequencer: Sequencer;
}

/**
 * Motore audio del lettore: sintetizzatore SpessaSynth con il SoundFont MuseScore General e sequencer MIDI.
 * Libreria e SoundFont sono caricati al primo uso; il service worker mette in cache il SoundFont.
 */
@Injectable({ providedIn: 'root' })
export class ScoreAudioService implements OnDestroy {
    private engine?: Promise<AudioEngine>;
    private current?: AudioEngine;
    private portOffsets: number[] = [];

    /** Prepara il motore; la prima chiamata scarica libreria e SoundFont. */
    ready(): Promise<AudioEngine> {
        this.engine ??= this.create().catch((error) => {
            this.engine = undefined;
            throw error;
        });
        return this.engine;
    }

    async load(midi: Uint8Array): Promise<void> {
        const { sequencer } = await this.ready();
        const loaded = new Promise<void>((resolve) => sequencer.eventHandler.addEvent('songChange', 'taurus-load', () => resolve()));
        sequencer.loadNewSongList([{ binary: midi.slice().buffer as ArrayBuffer, fileName: 'taurus.mid' }]);
        // L'evento di cambio brano conferma il caricamento; il timeout tiene utilizzabile il lettore se non arriva.
        await Promise.race([loaded, new Promise<void>((resolve) => window.setTimeout(resolve, 3000))]);
        sequencer.pause();
        sequencer.currentTime = 0;
        this.portOffsets = (await sequencer.getMIDI()).portChannelOffsetMap ?? [];
    }

    async play(): Promise<void> {
        const { context, sequencer } = await this.ready();
        await context.resume();
        sequencer.play();
    }

    pause(): void {
        this.current?.sequencer.pause();
    }

    get currentTime(): number {
        return this.current?.sequencer.currentTime ?? 0;
    }

    set currentTime(seconds: number) {
        if (this.current) this.current.sequencer.currentTime = Math.max(0, seconds);
    }

    get paused(): boolean {
        return this.current?.sequencer.paused ?? true;
    }

    get duration(): number {
        return this.current?.sequencer.duration ?? 0;
    }

    set playbackRate(rate: number) {
        if (this.current) this.current.sequencer.playbackRate = rate;
    }

    /** Volume (0-1) e silenziamento di una parte, sul suo canale. */
    setChannel(channel: PlaybackChannel, gain: number, muted: boolean): void {
        const midiChannel = this.current?.synth.midiChannels[(this.portOffsets[channel.port] ?? channel.port * 16) + channel.channel];
        if (!midiChannel) return;
        midiChannel.setSystemParameter('gain', gain);
        midiChannel.setSystemParameter('isMuted', muted);
    }

    /** Suona una scala o un colpo di batteria, per provare il suono di uno strumento. */
    async preview(notes: { key: number; at: number; duration: number }[], program: number | null, bank: number, drum: boolean): Promise<void> {
        const { context, synth, sequencer } = await this.ready();
        await context.resume();
        sequencer.pause();
        const channel = drum ? 9 : 0;
        if (!drum && program !== null) {
            synth.controllerChange(channel, 0, (bank >> 7) & 0x7f);
            synth.controllerChange(channel, 32, bank & 0x7f);
            synth.programChange(channel, program);
        }
        for (const note of notes) {
            window.setTimeout(() => synth.noteOn(channel, note.key, 100), note.at * 1000);
            window.setTimeout(() => synth.noteOff(channel, note.key), (note.at + note.duration) * 1000);
        }
    }

    ngOnDestroy(): void {
        void this.engine?.then(({ context, synth }) => {
            synth.destroy();
            void context.close();
        });
    }

    private async create(): Promise<AudioEngine> {
        const [{ WorkletSynthesizer, Sequencer }, soundFont] = await Promise.all([
            import('spessasynth_lib'),
            fetch(SOUNDFONT_URL).then((response) => {
                if (!response.ok) throw new Error(`SoundFont non disponibile (${response.status})`);
                return response.arrayBuffer();
            })
        ]);
        const context = new AudioContext();
        await context.audioWorklet.addModule(WORKLET_URL);
        const synth = new WorkletSynthesizer(context);
        synth.connect(context.destination);
        await synth.soundBankManager.addSoundBank(soundFont, 'main');
        await synth.isReady;
        const sequencer = new Sequencer(synth, { skipToFirstNoteOn: false });
        this.current = { context, synth, sequencer };
        return this.current;
    }
}
