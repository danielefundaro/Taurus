import Aura from '@primeng/themes/aura';
import Lara from '@primeng/themes/lara';
import { ACCESSIBLE_BUTTON, ACCESSIBLE_MESSAGE, ACCESSIBLE_TOAST, accessibleComponents, MessageSeverity, SEVERITY_PALETTES } from './accessible-theme';

type Rgb = [number, number, number];
type Scheme = 'light' | 'dark';

/** Il colore di un token del preset: primitivo (`{yellow.700}`), di superficie (`{surface.900}`) o esadecimale. */
function resolver(preset: any, scheme: Scheme): (token: string) => Rgb {
    const resolve = (token: string): Rgb => {
        const reference = /^\{(\w+)\.(\w+)\}$/.exec(token.trim());
        if (!reference) return [1, 3, 5].map((index) => parseInt(token.trim().slice(index, index + 2), 16)) as Rgb;
        const [, palette, shade] = reference;
        const value = palette === 'surface' ? preset.semantic.colorScheme[scheme].surface[shade] : preset.primitive[palette][shade];
        return resolve(value);
    };
    return resolve;
}

/** Il fondo che si vede: la tinta di `color-mix(in srgb, {tinta}, transparent N%)` sopra la superficie. */
function layer(background: string, ground: Rgb, resolve: (token: string) => Rgb): Rgb {
    const mix = /color-mix\(in srgb,\s*(\{[\w.]+\}),\s*transparent (\d+)%\)/.exec(background);
    if (!mix) return resolve(background);
    const weight = 1 - Number(mix[2]) / 100;
    const tint = resolve(mix[1]);
    return tint.map((value, index) => Math.round(value * weight + ground[index] * (1 - weight))) as Rgb;
}

/** Il rapporto di contrasto WCAG 2.1 fra due colori. */
function contrast(first: Rgb, second: Rgb): number {
    const luminance = (rgb: Rgb) => {
        const [r, g, b] = rgb.map((value) => {
            const channel = value / 255;
            return channel <= 0.03928 ? channel / 12.92 : ((channel + 0.055) / 1.055) ** 2.4;
        });
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    };
    const [lighter, darker] = [luminance(first), luminance(second)].sort((a, b) => b - a);
    return (lighter + 0.05) / (darker + 0.05);
}

describe('accessible theme', () => {
    const severities = Object.keys(SEVERITY_PALETTES) as MessageSeverity[];
    // Le superfici su cui stanno messaggi e toast: card e fondo della pagina.
    const surfaces: Record<Scheme, string[]> = { light: ['{surface.0}', '{surface.100}'], dark: ['{surface.900}', '{surface.950}'] };

    for (const [name, preset] of Object.entries({ Aura, Lara } as Record<string, any>)) {
        for (const scheme of ['light', 'dark'] as Scheme[]) {
            it(`keeps the ${scheme} message and toast text of ${name} at 4.5:1 or more`, () => {
                const resolve = resolver(preset, scheme);
                const overrides = accessibleComponents(name);
                for (const component of ['message', 'toast'] as const) {
                    for (const severity of severities) {
                        const text = resolve(overrides[component]!.colorScheme[scheme][severity].color);
                        const background = preset.components[component].colorScheme[scheme][severity].background;
                        for (const surface of surfaces[scheme]) {
                            const ground = resolve(surface);
                            expect(contrast(text, layer(background, ground, resolve)))
                                .withContext(`${component} ${severity} on ${surface}`)
                                .toBeGreaterThanOrEqual(4.5);
                        }
                    }
                }
            });

            it(`keeps the ${scheme} outlined and simple messages of ${name} at 4.5:1 or more`, () => {
                const resolve = resolver(preset, scheme);
                for (const severity of severities) {
                    const tokens = ACCESSIBLE_MESSAGE.colorScheme[scheme][severity];
                    for (const surface of surfaces[scheme]) {
                        for (const [variant, color] of [
                            ['outlined', tokens.outlined.color],
                            ['simple', tokens.simple.color]
                        ]) {
                            expect(contrast(resolve(color), resolve(surface)))
                                .withContext(`${variant} ${severity} on ${surface}`)
                                .toBeGreaterThanOrEqual(4.5);
                        }
                    }
                }
            });
        }
    }

    it('leaves Nora the filled messages and toasts it draws with light text', () => {
        expect(accessibleComponents('Nora')).toEqual({ button: ACCESSIBLE_BUTTON });
    });

    it('applies the message and toast overrides to Aura, Lara and the default preset', () => {
        for (const preset of ['Aura', 'Lara', undefined]) {
            expect(accessibleComponents(preset)).withContext(String(preset)).toEqual({ button: ACCESSIBLE_BUTTON, message: ACCESSIBLE_MESSAGE, toast: ACCESSIBLE_TOAST });
        }
    });
});
