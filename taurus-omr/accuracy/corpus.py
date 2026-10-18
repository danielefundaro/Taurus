"""Reference parts of the accuracy checks, written for the test in a compact notation and built as MusicXML.

The parts imitate band parts: one staff, treble or bass clef, ledger lines, accidentals, ties, triplets, divisi chords,
repeats with endings, multi-measure rests, tempo marks and D.C. al Fine.

Notation of a measure, tokens separated by spaces:

- a note is ``pitch:duration``: pitch as ``Bb4`` or ``F#5``, ``r`` for a rest, ``C5+E5`` for a chord; duration ``w``,
  ``h``, ``q``, ``e`` or ``s``, then ``.`` for a dot and ``t`` for a triplet; then ``'`` for staccato, ``>`` for an
  accent and ``~`` to tie the note to the next one;
- ``R`` is a whole-measure rest, ``rest*N`` a rest of N measures written as a single measure;
- ``|:`` and ``:|`` are repeat barlines, ``[N`` starts ending N, ``N]`` closes it;
- ``pp`` to ``ff`` are dynamics, ``tempo=96`` a metronome mark in quarters (``tempo=72.`` in dotted quarters);
- ``key=-2``, ``time=3/4`` and ``clef=F4`` change the attributes, ``pickup`` marks an incomplete first measure;
- ``fine`` and ``dc`` write "Fine" and "D.C. al Fine".

Accidentals are written only where the key signature and the previous notes of the measure do not imply them, as an
engraver would; eighths and shorter notes are beamed by beat.
"""

from __future__ import annotations

import re
from dataclasses import dataclass, field, replace
from xml.etree import ElementTree

DIVISIONS = 12
_TYPES = {"w": ("whole", 48), "h": ("half", 24), "q": ("quarter", 12), "e": ("eighth", 6), "s": ("16th", 3)}
_ACCIDENTALS = {-2: "flat-flat", -1: "flat", 0: "natural", 1: "sharp", 2: "double-sharp"}
_ALTERS = {"": 0, "b": -1, "bb": -2, "#": 1, "##": 2}
_DYNAMICS = {"pp", "p", "mp", "mf", "f", "ff"}
_NOTE = re.compile(r"^(?P<pitches>r|[A-G](?:bb|b|##|#)?\d(?:\+[A-G](?:bb|b|##|#)?\d)*):(?P<type>[whqes])(?P<dot>\.?)(?P<triplet>t?)(?P<marks>['>]*)(?P<tie>~?)$")
_PITCH = re.compile(r"^(?P<step>[A-G])(?P<alter>bb|b|##|#)?(?P<octave>\d)$")


@dataclass(frozen=True)
class Piece:
    """A reference part: ``name`` is the file name in the corpus, ``scan`` renders it as a worn scan."""

    name: str
    title: str
    part: str
    clef: str
    key: int
    time: str
    measures: tuple[str, ...]
    scan: bool = False


@dataclass
class _Note:
    pitches: list[tuple[str, int, int]]
    type: str
    dots: int
    triplet: bool
    duration: int
    marks: str
    tie_start: bool
    tie_stop: bool = False
    measure_rest: bool = False
    start: int = 0
    accidentals: list[str | None] = field(default_factory=list)
    beams: dict[int, str] = field(default_factory=dict)
    tuplet: str | None = None


MARCIA = Piece(
    name="marcia",
    title="Marcia",
    part="Tromba in Sib",
    clef="G2",
    key=-1,
    time="2/4",
    measures=(
        "tempo=116 f |: F4:e A4:e C5:e A4:e",
        "F5:q. E5:e",
        "D5:e. C5:s Bb4:e A4:e",
        "G4:q r:q",
        "E4:e G4:e Bb4:e G4:e",
        "E5:q. D5:e",
        "C5:e. Bb4:s A4:e G4:e",
        "[1 F4:q C5:q 1] :|",
        "[2 F4:q r:q 2]",
        "mf A4:e Bb4:e C5:e D5:e",
        "Eb5:q D5:q",
        "C5:e B4:e C5:e D5:e",
        "C5:h",
        "F#4:e G4:e A4:e Bb4:e",
        "C5:q. Bb4:e",
        "A4:e. G4:s F4:e E4:e",
        "F4:q r:q",
        "key=-2 p |: D4:q F4:q",
        "Bb4:h",
        "A4:q. Bb4:e",
        "C5:h~",
        "C5:q D5:q",
        "Eb5:e D5:e C5:e Bb4:e",
        "A4:q G4:q",
        "F4:h",
        "G4:e A4:e Bb4:e C5:e",
        "D5:q. Eb5:e",
        "F5:q D5:q",
        "Bb4:h",
        "C5:e. D5:s C5:e A4:e",
        "Bb4:q F4:q",
        "ff Bb4:q> r:q :|",
    ),
)

VALZER = Piece(
    name="valzer",
    title="Valzer",
    part="Corno in Mib",
    clef="G2",
    key=-1,
    time="3/4",
    measures=(
        "tempo=144 p rest*8",
        "mf r:q A4+C5:q A4+C5:q",
        "r:q A4+C5:q A4+C5:q",
        "r:q Bb4+D5:q Bb4+D5:q",
        "r:q Bb4+D5:q Bb4+D5:q",
        "r:q G4+C5:q G4+C5:q",
        "r:q G4+Bb4:q G4+Bb4:q",
        "r:q A4+C5:q A4+C5:q",
        "r:q A4+C5:q r:q",
        "f C5:h.~",
        "C5:q D5:q E5:q",
        "F5:h C5:q",
        "A4:h.",
        "Bb4:q. A4:e G4:q",
        "C#5:q D5:q E5:q",
        "F5:h E5:q",
        "D5:h r:q",
        "rest*4",
        "p r:q F4+A4:q F4+A4:q",
        "r:q E4+G4:q E4+G4:q",
        "r:q F4+A4:q F4+A4:q",
        "F4+A4:q r:q r:q",
    ),
)

TUBA = Piece(
    name="tuba",
    title="Corale e marcia",
    part="Tuba",
    clef="F4",
    key=-2,
    time="4/4",
    measures=(
        "pickup tempo=92 mf F2:q",
        "Bb1:h F2:h",
        "Bb1:q D2:q F2:q D2:q",
        "Eb2:h G2:h",
        "F2:q r:q F2:q r:q",
        "Bb2:h. A2:q",
        "G2:h~ G2:q Eb2:q",
        "F2:q F2:q F#2:q G2:q",
        "C2:h F2:h fine",
        "p rest*4",
        "mf D2:e D2:e D2:q D2:h",
        "Eb2:q G2:q Bb2:q G2:q",
        "A2:q. G2:e F2:q Eb2:q",
        "D2:h B1:h",
        "C2:q E2:q G2:q C3:q",
        "F2:h.~ F2:q",
        "Bb1:w~",
        "Bb1:h r:h dc",
    ),
)

CLARINETTO = Piece(
    name="clarinetto",
    title="Tarantella",
    part="Clarinetto in Sib",
    clef="G2",
    key=0,
    time="6/8",
    measures=(
        "tempo=72. mp E5:e F5:e G5:e C6:q.",
        "B5:q A5:e G5:q.",
        "F5:e E5:e D5:e E5:e F#5:e G5:e",
        "A5:q. r:q.",
        "G5:e A5:e B5:e C6:e D6:e E6:e",
        "D6:q C6:e B5:q A5:e",
        "G#5:e A5:e B5:e C6:q.",
        "C6:q. r:q.",
        "|: p C5:e. D5:s C5:e B4:q A4:e",
        "G4:q. G4:e A4:e B4:e",
        "C5:e Eb5:e D5:e C5:e Bb4:e A4:e",
        "G4:q. r:q.",
        "[1 G4:e A4:e B4:e C5:q D5:e 1] :|",
        "[2 G4:e F4:e E4:e D4:q. 2]",
        "f C4:e E4:e G4:e C5:e E5:e G5:e",
        "C6:q. r:q.",
        "pp E5:e' E5:e' E5:e' F5:e' F5:e' F5:e'",
        "G5:q. G4:q.",
        "C5:q.~ C5:q.",
        "C5:q r:e r:q.",
    ),
)

FLAUTO = Piece(
    name="flauto",
    title="Scherzo",
    part="Flauto",
    clef="G2",
    key=-1,
    time="4/4",
    measures=(
        "tempo=132 f F5:s G5:s A5:s Bb5:s C6:s D6:s E6:s F6:s G6:q r:q",
        "F6:e. E6:s D6:e. C6:s Bb5:q A5:q",
        "G5:et A5:et Bb5:et C6:et D6:et E6:et F6:h",
        "E6:q C6:q r:h",
        "A6:s G6:s F6:s E6:s D6:s C6:s Bb5:s A5:s G5:e A5:e Bb5:q",
        "C6:h. r:q",
        "mp D6:et C#6:et D6:et E6:et D6:et C#6:et D6:q r:q",
        "Bb5:e A5:e G5:e F5:e E5:q r:q",
        "|: F5:s A5:s C6:s F6:s A6:s F6:s C6:s A5:s F5:q r:q",
        "E5:s G5:s Bb5:s E6:s G6:s E6:s Bb5:s G5:s E5:q r:q",
        "F5:e A5:e C6:e F6:e E6:e D6:e C6:e Bb5:e",
        "A5:h r:h :|",
        "ff A6:h F6:h",
        "F6:q r:q r:h",
    ),
)

MARCIA_SCAN = replace(MARCIA, name="marcia-scansione", scan=True)

PIECES: tuple[Piece, ...] = (MARCIA, VALZER, TUBA, CLARINETTO, FLAUTO, MARCIA_SCAN)


def by_name(name: str) -> Piece:
    for piece in PIECES:
        if piece.name == name:
            return piece
    raise KeyError(name)


def musicxml(piece: Piece) -> str:
    """The reference MusicXML of a piece; multi-measure rests are written out measure by measure, as engravers do."""
    root = ElementTree.Element("score-partwise", version="4.0")
    work = ElementTree.SubElement(root, "work")
    ElementTree.SubElement(work, "work-title").text = piece.title
    identification = ElementTree.SubElement(root, "identification")
    ElementTree.SubElement(identification, "creator", type="composer").text = "Taurus"
    part_list = ElementTree.SubElement(root, "part-list")
    score_part = ElementTree.SubElement(part_list, "score-part", id="P1")
    ElementTree.SubElement(score_part, "part-name").text = piece.part
    part = ElementTree.SubElement(root, "part", id="P1")

    state = _State(key=piece.key, time=piece.time)
    measures = [_Measure(tokens.split(), state, first=index == 0, piece=piece) for index, tokens in enumerate(piece.measures)]
    _link_ties(measures)
    number = 0 if measures[0].pickup else 1
    for index, measure in enumerate(measures):
        last = index == len(measures) - 1
        for element in measure.elements(last):
            element.set("number", str(number))
            part.append(element)
            number += 1
    ElementTree.indent(root, space="  ")
    return (
        '<?xml version="1.0" encoding="UTF-8"?>\n'
        '<!DOCTYPE score-partwise PUBLIC "-//Recordare//DTD MusicXML 4.0 Partwise//EN" "http://www.musicxml.org/dtds/partwise.dtd">\n'
        + ElementTree.tostring(root, encoding="unicode")
        + "\n"
    )


@dataclass
class _State:
    key: int
    time: str

    @property
    def length(self) -> int:
        beats, beat_type = (int(value) for value in self.time.split("/"))
        return beats * 48 // beat_type

    @property
    def beat(self) -> int:
        beats, beat_type = (int(value) for value in self.time.split("/"))
        return 18 if beat_type == 8 and beats % 3 == 0 else 48 // beat_type


class _Measure:
    def __init__(self, tokens: list[str], state: _State, first: bool, piece: Piece) -> None:
        self.notes: list[_Note] = []
        self.directions: list[ElementTree.Element] = []
        self.pending: list[ElementTree.Element] = []
        self.closing: list[ElementTree.Element] = []
        self.attributes: dict[str, str] = {}
        self.forward = self.backward = self.pickup = False
        self.ending_start: str | None = None
        self.ending_stop: str | None = None
        self.rest_count = 0
        if first:
            self.attributes = {"divisions": str(DIVISIONS), "key": str(piece.key), "time": piece.time, "clef": piece.clef}
        for token in tokens:
            self._read(token, state)
        self.key = state.key
        self.length = state.length
        self._check(state)
        self._spell(state.key)
        _beam(self.notes, state.beat)

    def _read(self, token: str, state: _State) -> None:
        if token == "|:":
            self.forward = True
        elif token == ":|":
            self.backward = True
        elif token == "pickup":
            self.pickup = True
        elif token.startswith("[") and token[1:].isdigit():
            self.ending_start = token[1:]
        elif token.endswith("]") and token[:-1].isdigit():
            self.ending_stop = token[:-1]
        elif token in _DYNAMICS:
            self.pending.append(_direction("below", _dynamics(token)))
        elif token.startswith("tempo="):
            self.pending.append(_tempo(token[6:]))
        elif token.startswith(("key=", "time=", "clef=")):
            name, value = token.split("=")
            self.attributes[name] = value
            if name == "key":
                state.key = int(value)
            elif name == "time":
                state.time = value
        elif token.startswith("rest*"):
            self.rest_count = int(token[5:])
        elif token == "R":
            self._add(_Note([], "whole", 0, False, state.length, "", False, measure_rest=True))
        elif token == "fine":
            self.closing.append(_direction("above", _words("Fine")))
        elif token == "dc":
            direction = _direction("above", _words("D.C. al Fine"))
            ElementTree.SubElement(direction, "sound", dacapo="yes")
            self.closing.append(direction)
        else:
            self._add(_note(token))

    def _add(self, note: _Note) -> None:
        note.start = sum(previous.duration for previous in self.notes)
        self.notes.append(note)
        self.directions.append(self.pending)
        self.pending = []

    def _check(self, state: _State) -> None:
        if self.rest_count:
            if self.notes:
                raise ValueError("a multi-measure rest has no notes")
            return
        if self.pending:
            raise ValueError("directions after the last note")
        total = sum(note.duration for note in self.notes)
        if self.pickup and not 0 < total < state.length or not self.pickup and total != state.length:
            raise ValueError(f"measure of {total} divisions instead of {state.length}: {[note.pitches for note in self.notes]}")

    def _spell(self, key: int) -> None:
        """Writes the accidentals not implied by the key signature or by a previous note of the measure."""
        implied = _key_alters(key)
        current: dict[tuple[str, int], int] = {}
        for note in self.notes:
            note.accidentals = []
            for step, alter, octave in note.pitches:
                expected = current.get((step, octave), implied[step])
                note.accidentals.append(None if alter == expected or note.tie_stop else _ACCIDENTALS[alter])
                current[(step, octave)] = alter

    def elements(self, last: bool) -> list[ElementTree.Element]:
        count = max(self.rest_count, 1)
        result = []
        for index in range(count):
            measure = ElementTree.Element("measure")
            if self.pickup:
                measure.set("implicit", "yes")
            if index == 0:
                self._opening(measure)
            if self.rest_count:
                if index == 0:
                    for direction in self.pending:
                        measure.append(direction)
                measure.extend(_note_elements(_Note([], "whole", 0, False, self.length, "", False, measure_rest=True)))
            else:
                for note, directions in zip(self.notes, self.directions):
                    measure.extend(directions)
                    measure.extend(_note_elements(note))
            if index == count - 1:
                measure.extend(self.closing)
                self._closing(measure, last)
            result.append(measure)
        return result

    def _opening(self, measure: ElementTree.Element) -> None:
        if self.forward or self.ending_start:
            barline = ElementTree.SubElement(measure, "barline", location="left")
            if self.forward:
                ElementTree.SubElement(barline, "bar-style").text = "heavy-light"
            if self.ending_start:
                ElementTree.SubElement(barline, "ending", number=self.ending_start, type="start").text = f"{self.ending_start}."
            if self.forward:
                ElementTree.SubElement(barline, "repeat", direction="forward")
        if self.attributes or self.rest_count:
            attributes = ElementTree.SubElement(measure, "attributes")
            if "divisions" in self.attributes:
                ElementTree.SubElement(attributes, "divisions").text = self.attributes["divisions"]
            if "key" in self.attributes:
                ElementTree.SubElement(ElementTree.SubElement(attributes, "key"), "fifths").text = self.attributes["key"]
            if "time" in self.attributes:
                time = ElementTree.SubElement(attributes, "time")
                beats, beat_type = self.attributes["time"].split("/")
                ElementTree.SubElement(time, "beats").text = beats
                ElementTree.SubElement(time, "beat-type").text = beat_type
            if "clef" in self.attributes:
                clef = ElementTree.SubElement(attributes, "clef")
                ElementTree.SubElement(clef, "sign").text = self.attributes["clef"][0]
                ElementTree.SubElement(clef, "line").text = self.attributes["clef"][1]
            if self.rest_count:
                style = ElementTree.SubElement(attributes, "measure-style")
                ElementTree.SubElement(style, "multiple-rest").text = str(self.rest_count)

    def _closing(self, measure: ElementTree.Element, last: bool) -> None:
        if not (self.backward or self.ending_stop or last):
            return
        barline = ElementTree.SubElement(measure, "barline", location="right")
        if self.backward or last:
            ElementTree.SubElement(barline, "bar-style").text = "light-heavy"
        if self.ending_stop:
            ElementTree.SubElement(barline, "ending", number=self.ending_stop, type="stop")
        if self.backward:
            ElementTree.SubElement(barline, "repeat", direction="backward")


def _note(token: str) -> _Note:
    match = _NOTE.match(token)
    if match is None:
        raise ValueError(f"unknown token {token!r}")
    note_type, duration = _TYPES[match["type"]]
    dots = 1 if match["dot"] else 0
    duration = duration * 3 // 2 if dots else duration
    triplet = bool(match["triplet"])
    if triplet:
        duration = duration * 2 // 3
    pitches = [] if match["pitches"] == "r" else [_pitch(value) for value in match["pitches"].split("+")]
    return _Note(pitches, note_type, dots, triplet, duration, match["marks"], bool(match["tie"]))


def _pitch(value: str) -> tuple[str, int, int]:
    match = _PITCH.match(value)
    if match is None:
        raise ValueError(f"unknown pitch {value!r}")
    return match["step"], _ALTERS[match["alter"] or ""], int(match["octave"])


def _key_alters(fifths: int) -> dict[str, int]:
    alters = dict.fromkeys("CDEFGAB", 0)
    for step in ("FCGDAEB"[:fifths] if fifths > 0 else "BEADGCF"[: -fifths]):
        alters[step] = 1 if fifths > 0 else -1
    return alters


def _link_ties(measures: list[_Measure]) -> None:
    previous: _Note | None = None
    for measure in measures:
        for note in measure.notes:
            if previous is not None and previous.tie_start:
                if previous.pitches != note.pitches or not note.pitches:
                    raise ValueError(f"tie between different notes {previous.pitches} and {note.pitches}")
                note.tie_stop = True
            previous = note
        if measure.rest_count:
            previous = None
    for measure in measures:
        measure._spell(measure.key)


def _beam(notes: list[_Note], beat: int) -> None:
    """Beams eighths and shorter notes of the same beat; triplets are grouped by three."""
    groups: list[list[_Note]] = []
    current: list[_Note] = []
    for note in notes:
        beamable = note.pitches and note.type in ("eighth", "16th")
        if beamable and current and current[-1].start // beat == note.start // beat:
            current.append(note)
            continue
        if len(current) > 1:
            groups.append(current)
        current = [note] if beamable else []
    if len(current) > 1:
        groups.append(current)
    for group in groups:
        for index, note in enumerate(group):
            note.beams[1] = "begin" if index == 0 else "end" if index == len(group) - 1 else "continue"
            if note.type == "16th":
                before = index > 0 and group[index - 1].type == "16th"
                after = index < len(group) - 1 and group[index + 1].type == "16th"
                note.beams[2] = (
                    "continue" if before and after else "begin" if after else "end" if before else "forward hook" if index == 0 else "backward hook"
                )
    run = 0
    for note in notes:
        if not note.triplet:
            run = 0
            continue
        run += 1
        note.tuplet = "start" if run % 3 == 1 else "stop" if run % 3 == 0 else None


def _note_elements(note: _Note) -> list[ElementTree.Element]:
    """One ``note`` element per pitch, with the children in the order of the MusicXML schema."""
    pitches = note.pitches or [None]
    elements = []
    for index, pitch in enumerate(pitches):
        element = ElementTree.Element("note")
        if index > 0:
            ElementTree.SubElement(element, "chord")
        if pitch is None:
            rest = ElementTree.SubElement(element, "rest")
            if note.measure_rest:
                rest.set("measure", "yes")
        else:
            step, alter, octave = pitch
            pitch_element = ElementTree.SubElement(element, "pitch")
            ElementTree.SubElement(pitch_element, "step").text = step
            if alter:
                ElementTree.SubElement(pitch_element, "alter").text = str(alter)
            ElementTree.SubElement(pitch_element, "octave").text = str(octave)
        ElementTree.SubElement(element, "duration").text = str(note.duration)
        if note.tie_stop:
            ElementTree.SubElement(element, "tie", type="stop")
        if note.tie_start:
            ElementTree.SubElement(element, "tie", type="start")
        ElementTree.SubElement(element, "voice").text = "1"
        if not note.measure_rest:
            ElementTree.SubElement(element, "type").text = note.type
        for _ in range(note.dots):
            ElementTree.SubElement(element, "dot")
        if pitch is not None and note.accidentals[index]:
            ElementTree.SubElement(element, "accidental").text = note.accidentals[index]
        if note.triplet:
            modification = ElementTree.SubElement(element, "time-modification")
            ElementTree.SubElement(modification, "actual-notes").text = "3"
            ElementTree.SubElement(modification, "normal-notes").text = "2"
        if index == 0:
            for level, value in sorted(note.beams.items()):
                ElementTree.SubElement(element, "beam", number=str(level)).text = value
        _notations(element, note, first=index == 0)
        elements.append(element)
    return elements


def _notations(element: ElementTree.Element, note: _Note, first: bool) -> None:
    tied = [kind for kind, present in (("stop", note.tie_stop), ("start", note.tie_start)) if present]
    tuplet = note.tuplet if first else None
    marks = note.marks if first else ""
    if not (tied or tuplet or marks):
        return
    notations = ElementTree.SubElement(element, "notations")
    for kind in tied:
        ElementTree.SubElement(notations, "tied", type=kind)
    if tuplet:
        ElementTree.SubElement(notations, "tuplet", type=tuplet, bracket="no")
    if marks:
        articulations = ElementTree.SubElement(notations, "articulations")
        for mark in marks:
            ElementTree.SubElement(articulations, "staccato" if mark == "'" else "accent")


def _direction(placement: str, content: ElementTree.Element) -> ElementTree.Element:
    direction = ElementTree.Element("direction", placement=placement)
    ElementTree.SubElement(direction, "direction-type").append(content)
    return direction


def _dynamics(mark: str) -> ElementTree.Element:
    dynamics = ElementTree.Element("dynamics")
    ElementTree.SubElement(dynamics, mark)
    return dynamics


def _words(text: str) -> ElementTree.Element:
    words = ElementTree.Element("words")
    words.text = text
    return words


def _tempo(value: str) -> ElementTree.Element:
    dotted = value.endswith(".")
    per_minute = int(value.rstrip("."))
    metronome = ElementTree.Element("metronome")
    ElementTree.SubElement(metronome, "beat-unit").text = "quarter"
    if dotted:
        ElementTree.SubElement(metronome, "beat-unit-dot")
    ElementTree.SubElement(metronome, "per-minute").text = str(per_minute)
    direction = _direction("above", metronome)
    ElementTree.SubElement(direction, "sound", tempo=f"{per_minute * (1.5 if dotted else 1):g}")
    return direction
