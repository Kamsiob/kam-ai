package com.kamsiob.kamai.ui.theme

import androidx.compose.ui.graphics.Color

// PART 8. The accent the user can choose, sixteen in all: eight brighter and
// eight earthier. Green is the default and the one DESIGN.md fixes.
//
// Every colour here was designed and verified by a contrast script before it
// was allowed into this file (tools/... , recorded in DECISIONS.md). Each has a
// separately tuned light-theme and dark-theme shade, and an on-accent text
// colour, and every one clears the same bar in BOTH themes:
//   on-accent text on the filled accent  >= 4.5:1
//   the accent against the theme background >= 3:1
// A colour that only looked right in one theme was retuned until it worked in
// both, or it did not ship. None had to be dropped.
//
// The accent drives exactly what the green drove. It never touches the reserved
// amber, which stays fixed for follow-up bookmarks, locked tiers, and the
// support button regardless of the accent chosen.

enum class AccentGroup { BRIGHT, EARTHY }

data class Accent(
    val id: String,
    val name: String,
    val group: AccentGroup,
    val lightAccent: Color,
    val lightOnAccent: Color,
    val lightTonalFill: Color,
    val lightTonalText: Color,
    val darkAccent: Color,
    val darkOnAccent: Color,
    val darkTonalFill: Color,
    val darkTonalText: Color,
)

object Accents {
    val all: List<Accent> = listOf(
    // The default. Its tonal shades are the exact DESIGN.md section 3 values,
    // not derived, so the out-of-box look is unchanged.
    Accent(
        id = "green",
        name = "Green",
        group = AccentGroup.BRIGHT,
        lightAccent = Color(0xFF2E7A52),
        lightOnAccent = Color(0xFFF2FBF4),
        lightTonalFill = Color(0xFFE2EDE0),
        lightTonalText = Color(0xFF2A5C42),
        darkAccent = Color(0xFF6FD19E),
        darkOnAccent = Color(0xFF0A1B11),
        darkTonalFill = Color(0xFF1D2E23),
        darkTonalText = Color(0xFF9FDDBA),
    ),
    Accent(
        id = "teal",
        name = "Teal",
        group = AccentGroup.BRIGHT,
        lightAccent = Color(0xFF1F777A),
        lightOnAccent = Color(0xFFF2FBF4),
        lightTonalFill = Color(0xFFD8E2DC),
        lightTonalText = Color(0xFF1D6D70),
        darkAccent = Color(0xFFADE9EB),
        darkOnAccent = Color(0xFF0A1B11),
        darkTonalFill = Color(0xFF2F3F3D),
        darkTonalText = Color(0xFFADE9EB),
    ),
    Accent(
        id = "sky",
        name = "Sky",
        group = AccentGroup.BRIGHT,
        lightAccent = Color(0xFF1F547A),
        lightOnAccent = Color(0xFFF2FBF4),
        lightTonalFill = Color(0xFFD8DEDC),
        lightTonalText = Color(0xFF1D4D70),
        darkAccent = Color(0xFFADD1EB),
        darkOnAccent = Color(0xFF0A1B11),
        darkTonalFill = Color(0xFF2F3B3D),
        darkTonalText = Color(0xFFADD1EB),
    ),
    Accent(
        id = "indigo",
        name = "Indigo",
        group = AccentGroup.BRIGHT,
        lightAccent = Color(0xFF261F7A),
        lightOnAccent = Color(0xFFF2FBF4),
        lightTonalFill = Color(0xFFD9D6DC),
        lightTonalText = Color(0xFF231D70),
        darkAccent = Color(0xFFB3ADEB),
        darkOnAccent = Color(0xFF0A1B11),
        darkTonalFill = Color(0xFF30333D),
        darkTonalText = Color(0xFFB3ADEB),
    ),
    Accent(
        id = "violet",
        name = "Violet",
        group = AccentGroup.BRIGHT,
        lightAccent = Color(0xFF4C1F7A),
        lightOnAccent = Color(0xFFF2FBF4),
        lightTonalFill = Color(0xFFDED6DC),
        lightTonalText = Color(0xFF461D70),
        darkAccent = Color(0xFFCCADEB),
        darkOnAccent = Color(0xFF0A1B11),
        darkTonalFill = Color(0xFF35333D),
        darkTonalText = Color(0xFFCCADEB),
    ),
    Accent(
        id = "magenta",
        name = "Magenta",
        group = AccentGroup.BRIGHT,
        lightAccent = Color(0xFF7A1F59),
        lightOnAccent = Color(0xFFF2FBF4),
        lightTonalFill = Color(0xFFE5D6D7),
        lightTonalText = Color(0xFF701D52),
        darkAccent = Color(0xFFEBADD4),
        darkOnAccent = Color(0xFF0A1B11),
        darkTonalFill = Color(0xFF3B3339),
        darkTonalText = Color(0xFFEBADD4),
    ),
    Accent(
        id = "rose",
        name = "Rose",
        group = AccentGroup.BRIGHT,
        lightAccent = Color(0xFF7A1F2E),
        lightOnAccent = Color(0xFFF2FBF4),
        lightTonalFill = Color(0xFFE5D6D1),
        lightTonalText = Color(0xFF701D2A),
        darkAccent = Color(0xFFEBADB8),
        darkOnAccent = Color(0xFF0A1B11),
        darkTonalFill = Color(0xFF3B3333),
        darkTonalText = Color(0xFFEBADB8),
    ),
    Accent(
        id = "gold",
        name = "Gold",
        group = AccentGroup.BRIGHT,
        lightAccent = Color(0xFF7A621F),
        lightOnAccent = Color(0xFFF2FBF4),
        lightTonalFill = Color(0xFFE5E0CF),
        lightTonalText = Color(0xFF705A1D),
        darkAccent = Color(0xFFEBDAAD),
        darkOnAccent = Color(0xFF0A1B11),
        darkTonalFill = Color(0xFF3B3C31),
        darkTonalText = Color(0xFFEBDAAD),
    ),
    Accent(
        id = "sage",
        name = "Sage",
        group = AccentGroup.EARTHY,
        lightAccent = Color(0xFF31683F),
        lightOnAccent = Color(0xFFF2FBF4),
        lightTonalFill = Color(0xFFDAE0D4),
        lightTonalText = Color(0xFF2D603A),
        darkAccent = Color(0xFFBADEC3),
        darkOnAccent = Color(0xFF0A1B11),
        darkTonalFill = Color(0xFF313D35),
        darkTonalText = Color(0xFFBADEC3),
    ),
    Accent(
        id = "slate",
        name = "Slate",
        group = AccentGroup.EARTHY,
        lightAccent = Color(0xFF315668),
        lightOnAccent = Color(0xFFF2FBF4),
        lightTonalFill = Color(0xFFDADEDA),
        lightTonalText = Color(0xFF2D4F60),
        darkAccent = Color(0xFFBAD2DE),
        darkOnAccent = Color(0xFF0A1B11),
        darkTonalFill = Color(0xFF313B3B),
        darkTonalText = Color(0xFFBAD2DE),
    ),
    Accent(
        id = "denim",
        name = "Denim",
        group = AccentGroup.EARTHY,
        lightAccent = Color(0xFF314368),
        lightOnAccent = Color(0xFFF2FBF4),
        lightTonalFill = Color(0xFFDADBDA),
        lightTonalText = Color(0xFF2D3E60),
        darkAccent = Color(0xFFBAC6DE),
        darkOnAccent = Color(0xFF0A1B11),
        darkTonalFill = Color(0xFF31383B),
        darkTonalText = Color(0xFFBAC6DE),
    ),
    Accent(
        id = "plum",
        name = "Plum",
        group = AccentGroup.EARTHY,
        lightAccent = Color(0xFF5F3168),
        lightOnAccent = Color(0xFFF2FBF4),
        lightTonalFill = Color(0xFFE1D9DA),
        lightTonalText = Color(0xFF572D60),
        darkAccent = Color(0xFFD8BADE),
        darkOnAccent = Color(0xFF0A1B11),
        darkTonalFill = Color(0xFF37363B),
        darkTonalText = Color(0xFFD8BADE),
    ),
    Accent(
        id = "clay",
        name = "Clay",
        group = AccentGroup.EARTHY,
        lightAccent = Color(0xFF684131),
        lightOnAccent = Color(0xFFF2FBF4),
        lightTonalFill = Color(0xFFE2DBD2),
        lightTonalText = Color(0xFF603C2D),
        darkAccent = Color(0xFFDEC5BA),
        darkOnAccent = Color(0xFF0A1B11),
        darkTonalFill = Color(0xFF383834),
        darkTonalText = Color(0xFFDEC5BA),
    ),
    Accent(
        id = "rust",
        name = "Rust",
        group = AccentGroup.EARTHY,
        lightAccent = Color(0xFF684B31),
        lightOnAccent = Color(0xFFF2FBF4),
        lightTonalFill = Color(0xFFE2DCD2),
        lightTonalText = Color(0xFF60452D),
        darkAccent = Color(0xFFDECBBA),
        darkOnAccent = Color(0xFF0A1B11),
        darkTonalFill = Color(0xFF383934),
        darkTonalText = Color(0xFFDECBBA),
    ),
    Accent(
        id = "olive",
        name = "Olive",
        group = AccentGroup.EARTHY,
        lightAccent = Color(0xFF5B6831),
        lightOnAccent = Color(0xFFF2FBF4),
        lightTonalFill = Color(0xFFE0E0D2),
        lightTonalText = Color(0xFF54602D),
        darkAccent = Color(0xFFD6DEBA),
        darkOnAccent = Color(0xFF0A1B11),
        darkTonalFill = Color(0xFF373D34),
        darkTonalText = Color(0xFFD6DEBA),
    ),
    Accent(
        id = "mocha",
        name = "Mocha",
        group = AccentGroup.EARTHY,
        lightAccent = Color(0xFF684E31),
        lightOnAccent = Color(0xFFF2FBF4),
        lightTonalFill = Color(0xFFE2DDD2),
        lightTonalText = Color(0xFF60482D),
        darkAccent = Color(0xFFDECDBA),
        darkOnAccent = Color(0xFF0A1B11),
        darkTonalFill = Color(0xFF383A34),
        darkTonalText = Color(0xFFDECDBA),
    ),
    )

    val default: Accent = all.first { it.id == "green" }

    fun byId(id: String?): Accent = all.firstOrNull { it.id == id } ?: default

    val bright: List<Accent> get() = all.filter { it.group == AccentGroup.BRIGHT }
    val earthy: List<Accent> get() = all.filter { it.group == AccentGroup.EARTHY }
}
