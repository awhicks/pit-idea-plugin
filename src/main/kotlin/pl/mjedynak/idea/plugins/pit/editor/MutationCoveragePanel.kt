package pl.mjedynak.idea.plugins.pit.editor

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import org.cef.handler.CefLoadHandlerAdapter
import java.awt.BorderLayout
import java.io.File
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants

/**
 * Project service that renders PIT's own HTML report in a JCEF browser pane. Shown as a
 * "Coverage" tab in the "Mutation List" tool window. Loads `index.html` directly from the
 * report directory PIT wrote, so the user sees the full report (line coverage, mutation
 * coverage, test strength) exactly as PIT generated it — no custom HTML.
 *
 * A small toolbar at the top provides back/forward navigation buttons so the user can follow
 * links within the report and return. Button enabled state mirrors `CefBrowser.canGoBack()`
 * / `canGoForward()` via a [CefLoadHandlerAdapter].
 *
 * Refreshed from [PitCoverageAnnotator.updateFromReport] after a PIT run and cleared before
 * each run. Falls back to a simple label if JCEF is not available in the host IDE.
 */
class MutationCoveragePanel(
    private val project: Project,
) : JPanel(BorderLayout()) {
    private val logger = Logger.getInstance(MutationCoveragePanel::class.java)
    private val browser: JBCefBrowser? = createBrowserIfSupported()
    private var canGoBack = false
    private var canGoForward = false

    init {
        if (browser != null) {
            add(createToolbar(), BorderLayout.NORTH)
            add(browser.component, BorderLayout.CENTER)
            browser.getJBCefClient().addLoadHandler(
                object : CefLoadHandlerAdapter() {
                    override fun onLoadingStateChange(
                        b: org.cef.browser.CefBrowser?,
                        isLoading: Boolean,
                        canBack: Boolean,
                        canForward: Boolean,
                    ) {
                        canGoBack = canBack
                        canGoForward = canForward
                    }
                },
                browser.cefBrowser,
            )
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

    private fun createToolbar(): JComponent {
        val backAction =
            object : AnAction("Back", "Navigate back in the report", AllIcons.Actions.Back) {
                override fun actionPerformed(e: AnActionEvent) {
                    browser?.cefBrowser?.goBack()
                }

                override fun update(e: AnActionEvent) {
                    e.presentation.isEnabled = canGoBack
                }

                override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
            }
        val forwardAction =
            object : AnAction("Forward", "Navigate forward in the report", AllIcons.Actions.Forward) {
                override fun actionPerformed(e: AnActionEvent) {
                    browser?.cefBrowser?.goForward()
                }

                override fun update(e: AnActionEvent) {
                    e.presentation.isEnabled = canGoForward
                }

                override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
            }
        val group = DefaultActionGroup(backAction, forwardAction)
        val toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.TOOLWINDOW_CONTENT, group, true)
        toolbar.targetComponent = this
        return toolbar.component
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
