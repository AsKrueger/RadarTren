package com.askrueger.radartren.domain

import java.text.Normalizer

data class Classification(val severity: Severity, val category: IncidentCategory)

/**
 * Assigns urgency to Renfe notices. The feed carries no `severity`, `cause` or `effect`, so the
 * only input is free text. Rules run top to bottom and the first match wins; the table and its
 * rationale are documented in docs/clasificacion-de-urgencia.md.
 */
object AlertClassifier {
    private data class Rule(val classification: Classification, val patterns: List<Regex>)

    private fun rule(severity: Severity, category: IncidentCategory, vararg patterns: String) =
        Rule(Classification(severity, category), patterns.map(::Regex))

    private val TEST_NOTICE = Regex("""^pruebas?\b""")

    /** Scheduled works: serious wording in them describes a plan, not an emergency. */
    private val PLANNED = Regex("""\b(por|debido a( las)?) (las )?obras\b|plan alternativo|por obras de|azpiegitur""")

    private val rules = listOf(
        rule(
            Severity.CRITICAL, IncidentCategory.SAFETY,
            """descarril""", """\barrollamiento\b""", """\barrollad[oa]\b""", """\bcolision\b""",
            """\bchoque\b""", """\baccidente\b""", """\bdesalojo\b""", """\bevacuacion\b""", """\bincendio\b""",
        ),
        rule(
            Severity.INFO, IncidentCategory.INFORMATION,
            """recuperan? sus frecuencias""", """circulacion ferroviaria en todo su recorrido""",
            """se restablece""", """restablecid[oa]""",
        ),
        rule(
            Severity.HIGH, IncidentCategory.SERVICE_INTERRUPTED,
            """sin servicio ferroviario""", """no se puede garantizar""", """servicio interrumpido""",
            """circulacion interrumpida""", """interrumpida la circulacion""", """circulacion cortada""",
            """falta de suministro electrico""", """servicio suspendido""", """suspendido el servicio""",
            """en todo su recorrido""",
        ),
        rule(
            Severity.MEDIUM, IncidentCategory.DISRUPTION,
            """via unica""", """incidencia en la infraestructura""", """incidencia operativa""", """con afectaciones""",
            """fuera de su horario habitual""", """no presta servicio""", """transbordo""", """reajuste de servicio""",
            """servicio alternativo""", """demoras?\b""", """\bretrasos?\b""",
        ),
        rule(
            Severity.LOW, IncidentCategory.ACCESSIBILITY,
            """ascensor""", """escaleras? mecanica""", """igogailu""", """accesible""", """accesibilidad""", """\bpmr\b""",
        ),
    )

    /** Returns null for notices that should be dropped, such as Renfe's own test entries. */
    fun classify(rawText: String): Classification? {
        val text = normalize(rawText)
        if (text.isBlank() || TEST_NOTICE.containsMatchIn(text)) return null

        val matched = rules.firstOrNull { rule -> rule.patterns.any { it.containsMatchIn(text) } }?.classification
        val planned = PLANNED.containsMatchIn(text)
        return when {
            matched?.category == IncidentCategory.SAFETY -> matched
            matched?.category == IncidentCategory.ACCESSIBILITY -> matched
            planned -> Classification(Severity.LOW, IncidentCategory.PLANNED_WORKS)
            matched != null -> matched
            else -> Classification(Severity.INFO, IncidentCategory.INFORMATION)
        }
    }

    /**
     * Northern networks publish "Basque // Spanish". Keeps the Spanish half so that the rules and
     * the reader get a single language.
     */
    fun readableText(rawText: String): String =
        rawText.substringAfterLast(" // ").trim().replace(Regex("""\s+"""), " ")

    private fun normalize(text: String): String =
        Normalizer.normalize(readableText(text), Normalizer.Form.NFD)
            .replace(Regex("""\p{M}+"""), "")
            .lowercase()
            .trim()
}
