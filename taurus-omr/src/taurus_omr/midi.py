"""Conversion of a MIDI file into MusicXML with music21, one part per MIDI track."""

from __future__ import annotations

import logging

from . import musicxml
from .errors import UnreadableMidi

log = logging.getLogger(__name__)
_HEADER = b"MThd"


def to_musicxml(data: bytes) -> bytes:
    """Converts a standard MIDI file, quantizing durations to sixteenths and triplet eighths.

    Every track with notes becomes a MusicXML part, so the backend can offer the same part choice as for MusicXML files.
    """
    if not data.startswith(_HEADER):
        raise UnreadableMidi("not a standard MIDI file")
    # music21 is slow to import: it is loaded only when a conversion is requested.
    from music21 import midi as m21midi  # noqa: PLC0415
    from music21.musicxml.m21ToXml import GeneralObjectExporter  # noqa: PLC0415

    try:
        midi_file = m21midi.MidiFile()
        midi_file.readstr(data)
        score = m21midi.translate.midiFileToStream(midi_file, quantizePost=True, quarterLengthDivisors=(4, 3))
        if not score.parts:
            raise UnreadableMidi("MIDI file without notes")
        converted = musicxml.parse(GeneralObjectExporter(score).parse())
        musicxml.remove_titles(converted)
        return musicxml.to_bytes(converted)
    except UnreadableMidi:
        raise
    except Exception as exception:  # music21 raises many unrelated exception types on malformed input
        log.info("MIDI conversion failed: %s", type(exception).__name__)
        raise UnreadableMidi("unreadable MIDI file") from exception
