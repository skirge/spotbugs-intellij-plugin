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
package org.jetbrains.plugins.spotbugs.gui.tree.model;

import com.intellij.openapi.diagnostic.Logger;
import edu.umd.cs.findbugs.I18N;
import org.jetbrains.plugins.spotbugs.core.Bug;
import org.jetbrains.plugins.spotbugs.gui.tree.BugInstanceComparator;
import org.jetbrains.plugins.spotbugs.gui.tree.GroupBy;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

@SuppressWarnings({"UseOfSystemOutOrSystemErr"})
final class Grouper<T> {

	interface GrouperCallback<E> {

		void startGroup(E firstMember, int depth);

		/**
		 * Start a new sub group.
		 *
		 * @param depth  insert the sub group at index (depth)
		 * @param member the member to compare at index (depth)
		 * @param parent
		 */
		void startSubGroup(int depth, E member, E parent);

		void addToGroup(int depth, E member, E parent);

		List<E> availableGroups(int depth, E member);

		Comparator<E> currentGroupComparatorChain(int depth);
	}

	private static final Logger LOGGER = Logger.getInstance(Grouper.class.getName());

	private final GrouperCallback<T> _callback;

	/**
	 * Creates a new instance of Grouper.
	 *
	 * @param callback the callback which receives the groups and elements
	 */
	Grouper(final GrouperCallback<T> callback) {
		_callback = callback;
	}

	/**
	 * Group elements of given collection obtained by calling {@link GrouperCallback#availableGroups(int, Object)}
	 * according to given compartor's and depth test for equality.  The groups are specified by
	 * calls to the Grouper's callback object.
	 *
	 * @param comparable  the comparable to compare with the obtained group(s) comparables
	 * @param comparators the comparator's according to the comparing depth
	 * @see GroupBy
	 * @see BugInstanceComparator
	 * @see Grouper.GrouperCallback
	 */
	public void group(final T comparable, final List<Comparator<T>> comparators) {

		int depth = -1;
		int index = -1;
		int j = 0;

		for (int i = 0; i < comparators.size(); i++) {
			depth = -1;
			j = i;

			final List<T> groups = _callback.availableGroups(i, comparable);

			final Comparator<T> tComparator = comparators.get(i);
			Collections.sort(groups, tComparator);
			index = Collections.binarySearch(groups, comparable, tComparator);

			// FIXME make dynamic
			if (i == 0 && index < 0) { // if top 1st level group does not exists break iteration
				depth = i;
				break;
			} else if (i == 1 && index < 0) { // if 2nd level group does not exists break iteration
				depth = i;
				break;
			} else if (i == 2 && index < 0) { // if 3rd level group does not exists break iteration
				depth = i;
				break;
			} else if (i == 3 && index < 0) { // if 4th level group does not exists break iteration
				depth = i;
				break;
			} else if (i == 4 && index < 0) { // if 5th level group does not exists break iteration
				depth = i;
				break;
			} else { // addToGroup
				//parentIndex = index;
				depth = i;
			}
		}


		/*System.err.println("\n===OOOPS \nindex: " + index + " level: " + level + " i: " + j);
					System.err.println("Classname: " + ((Bug) comparable).getInstance().getPrimaryClass().getClassName().split("\\$")[0]);
					System.err.println("Category: " + I18N.instance().getBugCategoryDescription(((Bug) comparable).getInstance().getBugPattern().getCategory()));
					System.err.println("Type: " + I18N.instance().getBugTypeDescription(((Bug) comparable).getInstance().getBugPattern().getAbbrev()));
					System.err.println("Short: " + ((Bug) comparable).getInstance().getBugPattern().getShortDescription());*/

		int parentIndex = -1;
		if (index < 0 && depth == 0) { // top level group ##  && level == -1
			//System.err.println("\n= StartGroup =\nindex: " + index + " depth: " + depth + " i: " + j);
			//System.err.println("Classname: " + ((Bug) comparable).getInstance().getPrimaryClass().getClassName().split("\\$")[0]);
			/*System.err.println("Category: " + I18N.instance().getBugCategoryDescription(((Bug) comparable).getInstance().getBugPattern().getCategory()));
			System.err.println("Type: " + I18N.instance().getBugTypeDescription(((Bug) comparable).getInstance().getBugPattern().getAbbrev()));
			System.err.println("Short: " + ((Bug) comparable).getInstance().getBugPattern().getShortDescription());*/
			//final T parent = groups.get(index);
			_callback.startGroup(comparable, depth);

		} else if (index < 0 && depth > 0) { // x level group

/*
			final Comparator<T> c = comparators.get(0);// depth-1
			Collections.sort(groups, c);
			parentIndex = Collections.binarySearch(groups, comparable, c);

			// FIXME: multilevel comparator:  by depth-comparators.size(); for (int y = depth; y < comparators.size(); y++) {
			final T parent = groups.get(parentIndex);
			System.err.println("Parnet: " + parent);
			_callback.startSubGroup(depth, comparable, parent);*/
			final Comparator<T> c = _callback.currentGroupComparatorChain(depth - 1);

			final List<T> groups = _callback.availableGroups(depth - 1, comparable);
			Collections.sort(groups, c); // FIXME: comparators.get(depth-1) ???
			parentIndex = Collections.binarySearch(groups, comparable, comparators.get(depth - 1)); // FIXME: comparators.get(depth-1) ???

			if (LOGGER.isDebugEnabled()) {
				System.err.println("\n== StartSubGroup ==\nindex: " + index + " depth: " + depth + " i: " + j);
				System.err.println("Classname: " + ((Bug) comparable).getInstance().getPrimaryClass().getClassName().split("\\$")[0]);
				System.err.println("PAckage: " + ((Bug) comparable).getInstance().getPrimaryClass().getPackageName());
				System.err.println("Message: " + ((Bug) comparable).getInstance().getMessage());
				System.err.println("Category: " + I18N.instance().getBugCategoryDescription(((Bug) comparable).getInstance().getBugPattern().getCategory()));
				System.err.println("Type: " + I18N.instance().getBugTypeDescription(((Bug) comparable).getInstance().getBugPattern().getAbbrev()));
				System.err.println("Short: " + ((Bug) comparable).getInstance().getBugPattern().getShortDescription());
			}

			final T parent = groups.get(parentIndex);


			if (LOGGER.isDebugEnabled()) {
				System.err.println("ParentIndex: " + parentIndex);
				System.err.println("ParentClassname: " + ((Bug) parent).getInstance().getPrimaryClass().getClassName().split("\\$")[0]);
				System.err.println("ParentCategory: " + I18N.instance().getBugCategoryDescription(((Bug) parent).getInstance().getBugPattern().getCategory()));
				System.err.println("ParentType: " + I18N.instance().getBugTypeDescription(((Bug) parent).getInstance().getBugPattern().getAbbrev()));
				System.err.println("PArentShort: " + ((Bug) parent).getInstance().getBugPattern().getShortDescription());
			}


			_callback.startSubGroup(depth, comparable, parent);
		} else if (index >= 0) {
			if (LOGGER.isDebugEnabled()) {
				System.err.println("\n#### AddToGroup");
				System.err.println("=== index: " + index + " depth: " + depth + " i: " + j);
				System.err.println("Classname: " + ((Bug) comparable).getInstance().getPrimaryClass().getClassName().split("\\$")[0]);
				System.err.println("Classname: " + ((Bug) comparable).getInstance().getPrimaryClass().getClassName());
				System.err.println("Category: " + I18N.instance().getBugCategoryDescription(((Bug) comparable).getInstance().getBugPattern().getCategory()));
				System.err.println("Type: " + I18N.instance().getBugTypeDescription(((Bug) comparable).getInstance().getBugPattern().getAbbrev()));
				System.err.println("Short: " + ((Bug) comparable).getInstance().getBugPattern().getShortDescription());
				System.err.println("PriorityAbb: " + ((Bug) comparable).getInstance().getPriorityAbbreviation());
				System.err.println("PriorityString: " + ((Bug) comparable).getInstance().getPriorityString());
				System.err.println("PriorityTypeString: " + ((Bug) comparable).getInstance().getPriorityTypeString());
			}

			final Comparator<T> c = _callback.currentGroupComparatorChain(depth); // FIXME: -1 ???

			final List<T> groups = _callback.availableGroups(depth, comparable); // FIXME: -1 ???
			Collections.sort(groups, c); // FIXME: -1 ???
			parentIndex = Collections.binarySearch(groups, comparable, c); // FIXME: -1 ???
			final T parent = groups.get(parentIndex);

			if (LOGGER.isDebugEnabled()) {
				System.err.println("ParentIndex: " + parentIndex);
				System.err.println("ParentClassname: " + ((Bug) parent).getInstance().getPrimaryClass().getClassName().split("\\$")[0]);
				System.err.println("ParentCategory: " + I18N.instance().getBugCategoryDescription(((Bug) parent).getInstance().getBugPattern().getCategory()));
				System.err.println("ParentType: " + I18N.instance().getBugTypeDescription(((Bug) parent).getInstance().getBugPattern().getAbbrev()));
				System.err.println("PArentShort: " + ((Bug) parent).getInstance().getBugPattern().getShortDescription());
			}

			_callback.addToGroup(depth, comparable, parent);
		}


	}
}
