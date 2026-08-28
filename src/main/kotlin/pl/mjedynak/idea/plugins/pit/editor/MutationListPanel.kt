package pl.mjedynak.idea.plugins.pit.editor

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.ui.JBColor
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeCellRenderer
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath
import javax.swing.tree.TreeSelectionModel

/**
 * Project service that shows surviving (actionable) mutations in a tree grouped by class.
 * Each leaf is a single mutation with its line number and description; clicking it navigates
 * the editor to that source line. Refreshed from [PitCoverageAnnotator.updateFromReport] after
 * a PIT run completes and cleared by [PitCoverageAnnotator.clearAnnotations] before a run starts.
 *
 * Tree structure:
 * ```
 * Mutations
 * └── com.example.Calculator (2)
 *     ├── 14: Replaced integer multiplication with division [LINES_NOT_TESTED]
 *     └── 14: Replaced integer multiplication with subtraction [LINES_NOT_TESTED]
 * ```
 */
class MutationListPanel(
    private val project: Project,
) : JPanel(BorderLayout()) {
    private val logger = Logger.getInstance(MutationListPanel::class.java)
    private val tree =
        Tree().apply {
            isRootVisible = false
            showsRootHandles = true
            selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
            cellRenderer = MutationTreeCellRenderer()
        }
    private var sourceFilesByClass: Map<String, String> = emptyMap()

    init {
        border = JBUI.Borders.empty()
        add(JScrollPane(tree), BorderLayout.CENTER)
        tree.addMouseListener(
            object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    if (e.clickCount < 1 || e.isConsumed) {
                        return
                    }
                    val path = tree.getPathForLocation(e.x, e.y) ?: return
                    val node = path.lastPathComponent as? DefaultMutableTreeNode ?: return
                    val record = node.userObject as? MutationRecord ?: return
                    navigateToMutation(record)
                }
            },
        )
    }

    /**
     * Replaces the tree contents with the actionable subset of [records] (mutations whose
     * status is [MutationStatus.LINES_NEEDING_BETTER_TESTING] or [MutationStatus.LINES_NOT_TESTED]),
     * grouped by mutated class. Non-actionable mutations (KILLED/NON_VIABLE/...) are dropped.
     */
    fun update(records: List<MutationRecord>) {
        sourceFilesByClass =
            records.groupBy { it.mutatedClass }.mapValues { (_, classRecords) -> classRecords.first().sourceFile }
        val actionable =
            records.filter {
                it.status == MutationStatus.LINES_NEEDING_BETTER_TESTING ||
                    it.status == MutationStatus.LINES_NOT_TESTED
            }
        val root = DefaultMutableTreeNode("Mutations")
        actionable
            .groupBy { it.mutatedClass }
            .toSortedMap()
            .forEach { (mutatedClass, classRecords) ->
                val classNode = DefaultMutableTreeNode(ClassNode(mutatedClass, classRecords.size))
                classRecords.sortedWith(compareBy({ it.lineNumber }, { it.description })).forEach { record ->
                    classNode.add(DefaultMutableTreeNode(record, false))
                }
                root.add(classNode)
            }
        tree.model = DefaultTreeModel(root)
        expandAllClassNodes()
    }

    /** Clears the tree back to an empty root, e.g. before a new PIT run starts. */
    fun clear() {
        sourceFilesByClass = emptyMap()
        tree.model = DefaultTreeModel(DefaultMutableTreeNode("Mutations"))
    }

    private fun expandAllClassNodes() {
        val root = tree.model.root as? DefaultMutableTreeNode ?: return
        for (i in 0 until root.childCount) {
            val child = root.getChildAt(i) as? DefaultMutableTreeNode ?: continue
            tree.expandPath(TreePath(arrayOf(root, child)))
        }
    }

    private fun navigateToMutation(record: MutationRecord) {
        if (project.isDisposed) {
            return
        }
        val file =
            ApplicationManager.getApplication().runReadAction<VirtualFile?> {
                resolveFileForClass(record.mutatedClass)
            }
        if (file == null) {
            logger.warn("Mutation List: could not resolve source file for ${record.mutatedClass}")
            return
        }
        OpenFileDescriptor(project, file, (record.lineNumber - 1).coerceAtLeast(0), 0).navigate(true)
    }

    /**
     * Resolves the source file for a mutated class the same way [PitCoverageAnnotator] does:
     * `findClass` first, accepting only a real source file (`.java`/`.kt`), then a
     * `FilenameIndex` fallback by the source file name recorded in mutations.xml.
     */
    private fun resolveFileForClass(mutatedClass: String): VirtualFile? {
        val psiClass = JavaPsiFacade.getInstance(project).findClass(mutatedClass, GlobalSearchScope.projectScope(project))
        val containingFile = psiClass?.containingFile?.virtualFile
        if (containingFile != null && containingFile.extension in SOURCE_EXTENSIONS) {
            return containingFile
        }
        val sourceFileName = sourceFilesByClass[mutatedClass] ?: return null
        val files = FilenameIndex.getVirtualFilesByName(sourceFileName, GlobalSearchScope.projectScope(project))
        return files.firstOrNull()
    }

    private companion object {
        val SOURCE_EXTENSIONS = setOf("java", "kt")
    }

    private data class ClassNode(
        val className: String,
        val count: Int,
    ) {
        override fun toString(): String = "$className ($count)"
    }

    /**
     * Renders class nodes as "className (count)" and leaf nodes as "lineNumber: description [STATUS]".
     * Leaf text is colored red to match the editor coverage band for uncovered lines.
     */
    private class MutationTreeCellRenderer : DefaultTreeCellRenderer() {
        override fun getTreeCellRendererComponent(
            tree: JTree,
            value: Any?,
            sel: Boolean,
            expanded: Boolean,
            leaf: Boolean,
            row: Int,
            hasFocus: Boolean,
        ): Component {
            super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus)
            when (val userObject = (value as? DefaultMutableTreeNode)?.userObject) {
                is ClassNode -> {
                    text = userObject.toString()
                    foreground = JBColor.foreground()
                }

                is MutationRecord -> {
                    text = "${userObject.lineNumber}: ${userObject.description} [${userObject.status}]"
                    foreground = JBColor(Color(0x8B3333), Color(0xFF8888))
                }

                else -> {
                    text = value?.toString() ?: ""
                }
            }
            return this
        }
    }
}
