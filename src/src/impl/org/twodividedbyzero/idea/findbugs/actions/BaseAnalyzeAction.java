/*
 * Copyright 2008-2013 Andre Pfeiler
 *
 * This file is part of FindBugs-IDEA.
 *
 * FindBugs-IDEA is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * FindBugs-IDEA is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with FindBugs-IDEA.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.twodividedbyzero.idea.findbugs.actions;

import com.intellij.analysis.AnalysisScope;
import com.intellij.analysis.AnalysisScopeBundle;
import com.intellij.analysis.AnalysisUIOptions;
import com.intellij.analysis.BaseAnalysisActionDialog;
import com.intellij.ide.highlighter.ArchiveFileType;
import com.intellij.injected.editor.VirtualFileWindow;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiRecursiveElementVisitor;
import edu.umd.cs.findbugs.annotations.SuppressWarnings;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import javax.swing.Action;
import javax.swing.JComponent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Copy of {@link com.intellij.analysis.BaseAnalysisAction}
 * but extends {@link BaseAction}.
 *
 * @author Reto Merz<reto.merz@gmail.com>
 * @version $Revision$
 * @since 0.9.99
 */
public abstract class BaseAnalyzeAction extends BaseAction {

	private static final Logger LOGGER = Logger.getInstance(BaseAction.class.getName());

	@Override
	public void actionPerformed(final AnActionEvent e) {
		final DataContext dataContext = e.getDataContext();
		final Project project = e.getData(PlatformDataKeys.PROJECT);
		final Module module = e.getData(LangDataKeys.MODULE);
		if (project == null) {
			return;
		}
		AnalysisScope scope = getInspectionScope(dataContext);
		final boolean rememberScope = e.getPlace().equals(ActionPlaces.MAIN_MENU);
		final AnalysisUIOptions uiOptions = AnalysisUIOptions.getInstance(project);
		final PsiElement element = LangDataKeys.PSI_ELEMENT.getData(dataContext);
		final BaseAnalysisActionDialog dlg = new BaseAnalysisActionDialog(AnalysisScopeBundle.message("specify.analysis.scope", "FindBugs Analyze"),
			AnalysisScopeBundle.message("analysis.scope.title", "Analyze"),
			project,
			scope,
			module != null && scope.getScopeType() != AnalysisScope.MODULE ? getModuleNameInReadAction(module) : null,
			rememberScope, AnalysisUIOptions.getInstance(project), element) {
				@Override
				@Nullable
				protected JComponent getAdditionalActionSettings(final Project project) {
					return BaseAnalyzeAction.this.getAdditionalActionSettings(project, this);
				}

				@Override
				protected void doHelpAction() {
					HelpManager.getInstance().invokeHelp(getHelpTopic());
				}

				@SuppressWarnings({"RCN_REDUNDANT_NULLCHECK_OF_NONNULL_VALUE"})
				@NotNull
				@Override
				protected Action[] createActions() {
					return new Action[]{getOKAction(), getCancelAction(), getHelpAction()};
				}
			};
		dlg.show();
		if (!dlg.isOK()) {
			canceled();
			return;
		}
		final int oldScopeType = uiOptions.SCOPE_TYPE;
		scope = dlg.getScope(uiOptions, scope, project, module);
		if (!rememberScope) {
			uiOptions.SCOPE_TYPE = oldScopeType;
		}
		uiOptions.ANALYZE_TEST_SOURCES = dlg.isInspectTestSources();
		FileDocumentManager.getInstance().saveAllDocuments();

		analyze(project, scope);
	}


	@NonNls
	protected String getHelpTopic() {
		return "reference.dialogs.analyzeDependencies.scope";
	}


	protected void canceled() {
	}


	protected abstract void analyze(@NotNull Project project, AnalysisScope scope);


	@edu.umd.cs.findbugs.annotations.SuppressWarnings({"SIC_INNER_SHOULD_BE_STATIC_ANON"})
	protected final Iterable<String> findClasses(@NotNull final Project project, final AnalysisScope scope) {
		final Collection<String> ret = new ArrayList<String>(256);
		final PsiManager psiManager = PsiManager.getInstance(project);
		psiManager.startBatchFilesProcessingMode();
		try {
			//noinspection AnonymousInnerClass
			scope.accept(new PsiRecursiveElementVisitor() {

				FileType fileType;
				String path;
				VirtualFile virtualFile;

				@Override
				public void visitFile(final PsiFile file) {
					fileType = file.getFileType();
					if (StdFileTypes.JAVA.equals(fileType)) {
						path = AnalyzeUtil.getOutputClassFilePathForJavaFile(file, project);
						if (path != null) {
							addPath(path);
						} else {
							LOGGER.warn("No output class path found for PsiFile: " + file);
						}
					} else if (StdFileTypes.CLASS.equals(fileType)) {
						virtualFile = file.getVirtualFile();
						if (virtualFile != null) {
							path = virtualFile.getPath();
							if (path != null) {
								addPath(path);
							} else {
								LOGGER.warn("No path for VirtualFile: " + virtualFile);
							}
						} else {
							LOGGER.warn("No virtual file for PsiFile: " + file);
						}
					} // else skip non .java and .class files
				}

				private void addPath(final String path) {
					if (!ret.contains(path)) {
						ret.add(path);
					} // LATER: check why visitFile is called multiple times for the same PsiFile ?
				}
			});
		} finally {
			psiManager.finishBatchFilesProcessingMode();
		}
		return ret;
	}


	@Nullable
	private AnalysisScope getInspectionScope(@NotNull final DataContext dataContext) {
		if (PlatformDataKeys.PROJECT.getData(dataContext) == null) return null;

		final AnalysisScope scope = getInspectionScopeImpl(dataContext);

		return scope != null && scope.getScopeType() != AnalysisScope.INVALID ? scope : null;
	}


	@Nullable
	private AnalysisScope getInspectionScopeImpl(@NotNull final DataContext dataContext) {
		//Possible scopes: file, directory, package, project, module.
		final Project projectContext = PlatformDataKeys.PROJECT_CONTEXT.getData(dataContext);
		if (projectContext != null) {
			return new AnalysisScope(projectContext);
		}

		final AnalysisScope analysisScope = AnalyzeUtil.KEY.getData(dataContext);
		if (analysisScope != null) {
			return analysisScope;
		}

		final Module moduleContext = LangDataKeys.MODULE_CONTEXT.getData(dataContext);
		if (moduleContext != null) {
			return new AnalysisScope(moduleContext);
		}

		final Module [] modulesArray = LangDataKeys.MODULE_CONTEXT_ARRAY.getData(dataContext);
		if (modulesArray != null) {
			return new AnalysisScope(modulesArray);
		}
		final PsiFile psiFile = LangDataKeys.PSI_FILE.getData(dataContext);
		if (psiFile != null && psiFile.getManager().isInProject(psiFile)) {
			final VirtualFile file = psiFile.getVirtualFile();
			if (file != null && file.isValid() && file.getFileType() instanceof ArchiveFileType && acceptNonProjectDirectories()) {
				final VirtualFile jarRoot = JarFileSystem.getInstance().getJarRootForLocalFile(file);
				if (jarRoot != null) {
					final PsiDirectory psiDirectory = psiFile.getManager().findDirectory(jarRoot);
					if (psiDirectory != null) {
						return new AnalysisScope(psiDirectory);
					}
				}
			}
			return new AnalysisScope(psiFile);
		}

		final VirtualFile[] virtualFiles = PlatformDataKeys.VIRTUAL_FILE_ARRAY.getData(dataContext);
		final Project project = PlatformDataKeys.PROJECT.getData(dataContext);
		if (virtualFiles != null && project != null) { //analyze on selection
			final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
			if (virtualFiles.length == 1) {
				final PsiDirectory psiDirectory = PsiManager.getInstance(project).findDirectory(virtualFiles[0]);
				if (psiDirectory != null && (acceptNonProjectDirectories() || psiDirectory.getManager().isInProject(psiDirectory))) {
					return new AnalysisScope(psiDirectory);
				}
			}
			final Set<VirtualFile> files = new HashSet<VirtualFile>();
			for (VirtualFile vFile : virtualFiles) {
				if (fileIndex.isInContent(vFile)) {
					if (vFile instanceof VirtualFileWindow) {
						files.add(vFile);
						vFile = ((VirtualFileWindow)vFile).getDelegate();
					}
					collectFilesUnder(vFile, files);
				}
			}
			return new AnalysisScope(project, files);
		}
		return project == null ? null : new AnalysisScope(project);
	}


	protected boolean acceptNonProjectDirectories() {
		return false;
	}


	@Nullable
	protected JComponent getAdditionalActionSettings(final Project project, final BaseAnalysisActionDialog dialog) {
		return null;
	}


	private static void collectFilesUnder(@NotNull final VirtualFile vFile, @NotNull final Collection<VirtualFile> files) {
		VfsUtilCore.visitChildrenRecursively(vFile, new VirtualFileVisitor() {
			@Override
			public boolean visitFile(@NotNull final VirtualFile file) {
				if (!file.isDirectory()) {
					files.add(file);
				}
				return true;
			}
		});
	}


	private static String getModuleNameInReadAction(@NotNull final Module module) {
		return new ReadAction<String>() {
			protected void run(final Result<String> result) throws Throwable {
				result.setResult(module.getName());
			}
		}.execute().getResultObject();
	}
}
