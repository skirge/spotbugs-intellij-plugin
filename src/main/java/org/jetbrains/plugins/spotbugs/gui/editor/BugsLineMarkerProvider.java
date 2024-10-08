/*
 * Copyright 2020 SpotBugs plugin contributors
 *
 * This file is part of IntelliJ SpotBugs plugin.
 *
 * IntelliJ SpotBugs plugin is free software: you can redistribute it 
 * and/or modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation, either version 3 of 
 * the License, or (at your option) any later version.
 *
 * IntelliJ SpotBugs plugin is distributed in the hope that it will
 * be useful, but WITHOUT ANY WARRANTY; without even the implied 
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with IntelliJ SpotBugs plugin.
 * If not, see <http://www.gnu.org/licenses/>.
 */
package org.jetbrains.plugins.spotbugs.gui.editor;

import com.intellij.codeInsight.daemon.GutterIconNavigationHandler;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.spotbugs.common.ExtendedProblemDescriptor;
import org.jetbrains.plugins.spotbugs.common.util.BugInstanceUtil;
import org.jetbrains.plugins.spotbugs.common.util.GuiUtil;
import org.jetbrains.plugins.spotbugs.common.util.IdeaUtilImpl;
import org.jetbrains.plugins.spotbugs.core.FindBugsState;
import org.jetbrains.plugins.spotbugs.core.ProblemCacheService;
import org.jetbrains.plugins.spotbugs.core.WorkspaceSettings;
import org.jetbrains.plugins.spotbugs.gui.intentions.GroupBugIntentionListPopupStep;
import org.jetbrains.plugins.spotbugs.gui.intentions.RootGroupBugIntentionListPopupStep;
import org.jetbrains.plugins.spotbugs.gui.toolwindow.view.ToolWindowPanel;
import org.jetbrains.plugins.spotbugs.intentions.ClearAndSuppressBugIntentionAction;
import org.jetbrains.plugins.spotbugs.intentions.ClearBugIntentionAction;
import org.jetbrains.plugins.spotbugs.intentions.SuppressReportBugForClassIntentionAction;
import org.jetbrains.plugins.spotbugs.intentions.SuppressReportBugIntentionAction;

import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public final class BugsLineMarkerProvider implements LineMarkerProvider {

	public BugsLineMarkerProvider() {
	}

	/**
	 * TODO: Note that this method could be invoked outside EDT!
	 */
	@Override
	@Nullable
	public LineMarkerInfo<?> getLineMarkerInfo(@NotNull final PsiElement psiElement) {
		if (psiElement.getFirstChild() != null) {
			return null;
		}
		final Project project = psiElement.getProject();
		final WorkspaceSettings workspaceSettings = WorkspaceSettings.getInstance(project);
		if (!workspaceSettings.annotationGutterIcon) {
			return null;
		}
		if (!FindBugsState.get(project).isIdle()) {
			return null;
		}
		final ProblemCacheService cacheService = psiElement.getProject().getService(ProblemCacheService.class);
		if (cacheService == null) {
			return null;
		}
		final PsiFile psiFile = IdeaUtilImpl.getPsiFile(psiElement);
		final Map<PsiFile, List<ExtendedProblemDescriptor>> problemCache = cacheService.getProblems();

		if (problemCache.containsKey(psiFile)) {
			final List<ExtendedProblemDescriptor> matchingDescriptors = new ArrayList<ExtendedProblemDescriptor>();
			final List<ExtendedProblemDescriptor> problemDescriptors = problemCache.get(psiFile);
			if (problemDescriptors == null) {
				return null;
			}

			final Iterable<ExtendedProblemDescriptor> descriptors = new ArrayList<ExtendedProblemDescriptor>(problemDescriptors);
			for (final ExtendedProblemDescriptor problemDescriptor : descriptors) {

				final PsiElement problemPsiElement = problemDescriptor.getPsiElement();
				if (psiElement == firstLeafOrNull(problemPsiElement)) {
					matchingDescriptors.add(problemDescriptor);
					//if(psiElement instanceof PsiAnonymousClass) {
					//	final Editor[] editors = com.intellij.openapi.editor.EditorFactory.getInstance().getEditors(IdeaUtilImpl.getDocument(psiFile.getProject(), problemDescriptor));
					//	editors[0].getMarkupModel().addRangeHighlighter()
					//}
				}
			}
			if (!matchingDescriptors.isEmpty()) {
				final GutterIconNavigationHandler<PsiElement> navHandler = new BugGutterIconNavigationHandler(psiElement, matchingDescriptors);
				return new LineMarkerInfo<>(psiElement, psiElement.getTextRange(), GuiUtil.getTinyIcon(matchingDescriptors.get(0)), new TooltipProvider(matchingDescriptors), navHandler, GutterIconRenderer.Alignment.LEFT);
			}
		}

		return null;
	}

	@Nullable
	private static PsiElement firstLeafOrNull(@NotNull PsiElement element) {
		LeafElement firstLeaf = TreeUtil.findFirstLeaf(element.getNode());
		return firstLeaf != null ? firstLeaf.getPsi() : null;
	}

	public void collectSlowLineMarkers(final @NotNull List<? extends PsiElement> elements, final @NotNull Collection<? super LineMarkerInfo<?>> result) {
	}

	private static class BugGutterIconNavigationHandler implements GutterIconNavigationHandler<PsiElement> {

		private final List<ExtendedProblemDescriptor> _descriptors;
		private final PsiElement _psiElement;


		private BugGutterIconNavigationHandler(final PsiElement psiElement, final List<ExtendedProblemDescriptor> descriptors) {
			_descriptors = descriptors;
			_psiElement = psiElement;
			//buildPopupMenu();
		}


		@SuppressWarnings({"AnonymousInnerClass", "AnonymousInnerClassMayBeStatic"})
		private JBPopup buildPopupMenu() {
			final List<GroupBugIntentionListPopupStep> intentionGroups = new ArrayList<GroupBugIntentionListPopupStep>();

			for (final ExtendedProblemDescriptor problemDescriptor : _descriptors) {
				final List<SuppressReportBugIntentionAction> intentionActions = new ArrayList<SuppressReportBugIntentionAction>(_descriptors.size());

				intentionActions.add(new SuppressReportBugIntentionAction(problemDescriptor));
				intentionActions.add(new SuppressReportBugForClassIntentionAction(problemDescriptor));
				intentionActions.add(new ClearBugIntentionAction(problemDescriptor));
				intentionActions.add(new ClearAndSuppressBugIntentionAction(problemDescriptor));

				final GroupBugIntentionListPopupStep intentionActionGroup = new GroupBugIntentionListPopupStep(_psiElement, intentionActions);
				intentionGroups.add(intentionActionGroup);
			}

			final JBPopupFactory factory = JBPopupFactory.getInstance();
			/*return factory.createListPopup(new BaseListPopupStep<SuppressIntentionAction>(FindBugsPluginConstants.PLUGIN_NAME, intentionActions) {
				@Override
				public PopupStep<?> onChosen(final SuppressIntentionAction selectedValue, final boolean finalChoice) {
					final Project project = _psiElement.getProject();
					final Editor editor = FileEditorManager.getInstance(project).getSelectedTextEditor();
					ApplicationManager.getApplication().runWriteAction(new Runnable() {
						public void run() {
							selectedValue.invoke(project, editor, _psiElement);		
						}
					});
					return super.onChosen(selectedValue, finalChoice);
				}
			});*/
			return factory.createListPopup(new RootGroupBugIntentionListPopupStep(intentionGroups));
		}


		@Override
		public void navigate(final MouseEvent e, final PsiElement psiElement) {
			final ToolWindowPanel toolWindowPanel = ToolWindowPanel.getInstance(psiElement.getProject());
			for (final ExtendedProblemDescriptor descriptor : _descriptors) {
				if (descriptor.getPsiElement() == psiElement) {
					toolWindowPanel.getBugTreePanel().getBugTree().gotoNode(descriptor.getBug());
					break;
				}
			}
			buildPopupMenu().show(new RelativePoint(e));
		}
	}

	private static class TooltipProvider implements Function<PsiElement, String> {

		private final List<ExtendedProblemDescriptor> _problemDescriptors;
		@SuppressWarnings("HardcodedLineSeparator")
		private static final Pattern PATTERN = Pattern.compile("\n");


		private TooltipProvider(final List<ExtendedProblemDescriptor> problemDescriptors) {
			_problemDescriptors = problemDescriptors;
		}


		public String fun(final PsiElement psiElement) {
			return getTooltipText(_problemDescriptors);
		}


		@SuppressWarnings({"HardcodedFileSeparator"})
		private static String getTooltipText(final List<ExtendedProblemDescriptor> problemDescriptors) {
			final StringBuilder buffer = new StringBuilder();
			buffer.append("<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01 Transitional//EN\">");
			buffer.append("<HTML><HEAD><TITLE>");

			final int problemDescriptorsSize = problemDescriptors.size();
			for (int i = 0; i < problemDescriptorsSize; i++) {
				final ExtendedProblemDescriptor problemDescriptor = problemDescriptors.get(i);
				buffer.append("");
				buffer.append("</TITLE></HEAD><BODY><H3>");
				buffer.append(BugInstanceUtil.getBugPatternShortDescription(problemDescriptor.getBug().getInstance()));
				buffer.append("</H3>");
				buffer.append(PATTERN.matcher(BugInstanceUtil.getDetailText(problemDescriptor.getBug().getInstance())).replaceAll(""));
				if (i < problemDescriptors.size() - 1) {
					buffer.append("<HR>");
				}

			}

			buffer.append("</BODY></HTML>");
			return buffer.toString();
		}


		@Override
		public String toString() {
			return "TooltipProvider" + "{_problemDescriptor=" + _problemDescriptors + '}';
		}
	}
}
