package pl.mjedynak.idea.plugins.pit.editor

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

/**
 * Registers the "Mutation List" tool window at the bottom of the IDE with two tabs:
 * - **Mutations** — tree of surviving mutants grouped by class ([MutationListPanel])
 * - **Coverage** — HTML summary of mutation coverage per class ([MutationCoveragePanel])
 *
 * Both tabs are refreshed by [PitCoverageAnnotator] after a PIT run completes.
 * [DumbAware] keeps the window available during indexing.
 */
class MutationListToolWindowFactory :
    ToolWindowFactory,
    DumbAware {
    override fun createToolWindowContent(
        project: Project,
        toolWindow: ToolWindow,
    ) {
        val contentFactory = ContentFactory.getInstance()

        val mutationListPanel = project.getService(MutationListPanel::class.java)
        val mutationsContent = contentFactory.createContent(mutationListPanel, "Mutations", false)
        toolWindow.contentManager.addContent(mutationsContent)

        val coveragePanel = project.getService(MutationCoveragePanel::class.java)
        val coverageContent = contentFactory.createContent(coveragePanel, "Coverage", false)
        toolWindow.contentManager.addContent(coverageContent)
    }
}
