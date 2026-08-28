package pl.mjedynak.idea.plugins.pit.editor

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import java.awt.BorderLayout
import java.io.File
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants

/**
 * Project service that renders PIT's own HTML report in a JCEF browser pane. Shown as a
 * "Coverage" tab in the "Mutation List" tool window. Loads `index.html` directly from the
 * report directory PIT wrote, so the user sees the full report (line coverage, mutation
 * coverage, test strength) exactly as PIT generated it — no custom HTML.
 *
 * Refreshed from [PitCoverageAnnotator.updateFromReport] after a PIT run and cleared before
 * each run. Falls back to a simple label if JCEF is not available in the host IDE.
 */
class MutationCoveragePanel(
    private val project: Project,
) : JPanel(BorderLayout()) {
    private val logger = Logger.getInstance(MutationCoveragePanel::class.java)
    private val browser: JBCefBrowser? = createBrowserIfSupported()

    init {
        if (browser != null) {
            add(browser.component, BorderLayout.CENTER)
        } else {
            add(
                JLabel(
                    "<html>JCEF browser not available in this IDE.<br>Open the HTML report directly to view coverage.</html>",
                    SwingConstants.CENTER,
                ),
                BorderLayout.CENTER,
            )
        }
    }

    /**
     * Loads PIT's `index.html` from [reportDirForLinks] (top-level first, then the latest
     * timestamped subdirectory for older PIT layouts). The [records] parameter is ignored —
     * kept for API symmetry with [MutationListPanel.update] and to avoid changing the
     * annotator's call site.
     */
    fun update(
        records: List<MutationRecord>,
        reportDirForLinks: File? = null,
    ) {
        if (browser == null) {
            return
        }
        val indexFile = resolveReportIndex(reportDirForLinks)
        if (indexFile == null) {
            logger.warn("Mutation coverage panel: no index.html found under ${reportDirForLinks?.absolutePath}")
            browser.loadHTML(EMPTY_PAGE)
            return
        }
        browser.loadURL(indexFile.toURI().toString())
    }

    /** Clears the browser to an empty page. */
    fun clear() {
        if (browser == null) {
            return
        }
        browser.loadHTML(EMPTY_PAGE)
    }

    private fun resolveReportIndex(reportDir: File?): File? {
        if (reportDir == null) {
            return null
        }
        val topLevel = File(reportDir, "index.html")
        if (topLevel.exists()) {
            return topLevel
        }
        // Older PIT layouts write to a timestamped subdirectory.
        val subdirs = reportDir.listFiles()?.filter { it.isDirectory }?.sortedBy { it.lastModified() } ?: return null
        for (subdir in subdirs.asReversed()) {
            val index = File(subdir, "index.html")
            if (index.exists()) {
                return index
            }
        }
        return null
    }

    private fun createBrowserIfSupported(): JBCefBrowser? =
        if (JBCefApp.isSupported()) {
            try {
                JBCefBrowser()
            } catch (e: Exception) {
                logger.warn("Failed to create JCEF browser for coverage panel", e)
                null
            }
        } else {
            logger.info("JCEF not supported; coverage panel will show a fallback label")
            null
        }

    private companion object {
        const val EMPTY_PAGE =
            "<html><body style=\"font-family:sans-serif;color:#888;padding:24px;\">" +
                "No mutation report loaded. Run a mutation test to see coverage.</body></html>"
    }
}
