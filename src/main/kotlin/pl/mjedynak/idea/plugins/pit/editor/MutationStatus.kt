package pl.mjedynak.idea.plugins.pit.editor

/**
 * PIT mutation status, mirroring the `status` attribute of `<mutation>` in mutations.xml.
 *
 * SURVIVED and NO_COVERAGE are renamed to student-facing labels
 * (LINES_NEEDING_BETTER_TESTING / LINES_NOT_TESTED) to match the pitclipse fork.
 * The XML strings from PIT are mapped to these renamed values in [fromXml].
 */
enum class MutationStatus {
    KILLED,
    LINES_NEEDING_BETTER_TESTING,
    LINES_NOT_TESTED,
    NON_VIABLE,
    TIMED_OUT,
    MEMORY_ERROR,
    RUN_ERROR,
    NOT_STARTED,
    UNKNOWN,
    ;

    companion object {
        private val XML_TO_ENUM: Map<String, MutationStatus> =
            buildMap {
                put("KILLED", KILLED)
                put("SURVIVED", LINES_NEEDING_BETTER_TESTING)
                put("NO_COVERAGE", LINES_NOT_TESTED)
                put("NON_VIABLE", NON_VIABLE)
                put("TIMED_OUT", TIMED_OUT)
                put("MEMORY_ERROR", MEMORY_ERROR)
                put("RUN_ERROR", RUN_ERROR)
                put("NOT_STARTED", NOT_STARTED)
            }

        fun fromXml(status: String?): MutationStatus = status?.trim()?.uppercase()?.let { raw -> XML_TO_ENUM[raw] } ?: UNKNOWN
    }
}
