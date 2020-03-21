/*******************************************************************************
 * Copyright (c) 2019 IBM Corporation
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.codewind.filewatchers.eclipse;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.codewind.filewatchers.eclipse.CodewindFilewatcherdConnection.FileChangeEntryEclipse;
import org.eclipse.codewind.filewatchers.eclipse.CodewindFilewatcherdConnection.FileChangeEntryEclipse.ChangeEntryEventType;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IResourceDeltaVisitor;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;

/**
 * An instance of this class is created by CodewindFilewatcherdConnection, and
 * is also where this class is registered as a workbench listener. Only one
 * instance of this class should exist per Codewind server.
 *
 * This class converts a list of changes from the IDE into a List of
 * FileChangeEntryEclipse, which are then processed by 'parent' and passed to
 * the Codewind core filewatcher plugin.
 */
public class CodewindResourceChangeListener implements IResourceChangeListener {

	private final CodewindFilewatcherdConnection parent;

	private final ResourceChangeProjectTracker projectTracker;

	public CodewindResourceChangeListener(CodewindFilewatcherdConnection parent) {
		this.parent = parent;
		this.projectTracker = new ResourceChangeProjectTracker();
	}

	@Override
	public void resourceChanged(IResourceChangeEvent event) {
		IResourceDelta delta = event.getDelta();

		CodewindResourceDeltaVisitor visitor = new CodewindResourceDeltaVisitor();

		try {

			// If the delta is null (as happens with some events), just pass the empty array
			// list below.
			if (delta != null) {
				delta.accept(visitor);
			}

			parent.handleResourceChanges(visitor.getResult());

		} catch (CoreException e1) {
			// TODO: Log me.
			e1.printStackTrace();
		}

	}

	/**
	 * A standard Eclipse resource delta visitor, which converts a list of workbench
	 * resource changes into FileChangeEntryEclipse, for processing by 'parent'.
	 */
	private class CodewindResourceDeltaVisitor implements IResourceDeltaVisitor {

		private final List<FileChangeEntryEclipse> result = new ArrayList<>();

		public CodewindResourceDeltaVisitor() {
		}

		@Override
		public boolean visit(IResourceDelta delta) throws CoreException {

			IResource resource = delta.getResource();

			System.out.println("-------------");
			System.out.println("1) " + delta.getFullPath());
			System.out.println("2) " + delta.getMovedFromPath());
			System.out.println("3) " + delta.getMovedToPath());
			System.out.println("4) " + delta.getProjectRelativePath());
			System.out.println();

			if (resource == null) {
				return false;
			}

			if (resource instanceof IProject) {
				System.out.println("hi: " + resource);

				IProject proj = (IProject) resource;

				projectTracker.updateProjectPath(proj);

//				System.out.println("h1) " + proj.getFile("."));
				System.out.println("h3) " + proj.getProjectRelativePath());
				System.out.println("h4) " + proj.getWorkingLocation("."));
//				System.out.println("h2) " + proj.getFolder("."));

			}

			System.out.println("A) " + resource.getClass().getName());
			System.out.println("5) " + resource.getFullPath()); // 1
			System.out.println("6) " + resource.getLocation());
			System.out.println("7) " + resource.getLocationURI());
			System.out.println("8) " + resource.getParent());
			System.out.println("9) " + resource.getProject()); // 2
			System.out.println("10) " + resource.getProjectRelativePath());
			System.out.println("11) " + resource.getRawLocation());
			System.out.println("12) " + resource.getRawLocationURI());
			System.out.println("13) " + resource.getWorkspace());
			System.out.println("14) " + resource.getAdapter(String.class));
			System.out.println("15) " + resource.getAdapter(File.class));
			System.out.println("16) " + resource.getAdapter(java.nio.file.Path.class));

			// Exclude parent folder or project
			if (delta.getKind() == IResourceDelta.CHANGED && delta.getFlags() == 0) {
				return true;
			}

			ChangeEntryEventType ceet = null;

			switch (delta.getKind()) {
			case IResourceDelta.ADDED:
				ceet = ChangeEntryEventType.CREATE;
				break;
			case IResourceDelta.REMOVED:
				ceet = ChangeEntryEventType.DELETE;
				break;
			case IResourceDelta.CHANGED:
				ceet = ChangeEntryEventType.MODIFY;
				break;
			default:
				break;
			}

			if (ceet == null) {
				// Ignore and return for any unrecognized types.
				return true;
			}

			if (ceet == ChangeEntryEventType.MODIFY && ((delta.getFlags() & IResourceDelta.CONTENT) == 0
					&& (delta.getFlags() & IResourceDelta.REPLACED) == 0)) {
				// Some workbench operations, such as adding/removing a debug breakpoint, will
				// trigger a resource delta, even though the actual file contents is the same.
				// We ignore those, and return here.

				// However, these non-file-changed resources will always have the
				// IResourceDelta.CHANGED kind, so we only filter out events of this kind.
				return true;
			}

			// We have already checked that the resource is not null above, but some of the
			// underlying file system resources may still not (or no longer) have a backing
			// object, for example on project deletion events, so we check them here.

			IProject project = resource.getProject();
			if (project == null) {
				return true;
			}

//			System.out.println("path:" + project.getParent().getFolder(project.getFullPath()));

			File resourceFile = null;

			IPath path = resource.getLocation();
			if (path != null) {

				resourceFile = path.toFile();

			} else if (ceet == ChangeEntryEventType.DELETE && project.getLocation() != null) {
				resourceFile = project.getLocation().toFile();

			} else if (ceet == ChangeEntryEventType.DELETE && resource instanceof IProject
					&& ((IProject) resource).getLocation() == null) {

				// In the event of a deleted project, check the project tracker to see if we
				// have previously seen this project's path, and if so, use that.
				IProject deletedProject = ((IProject) resource);
				String delProjPath = projectTracker.getProjectPath(deletedProject).orElse(null);
				resourceFile = delProjPath != null ? new File(delProjPath) : null;
				
				Only communicate the deletion if the file no longer exists, otherwise it's just an Eclipse project removal
			}

			if (resourceFile == null) {
				return true;
			}

			FileChangeEntryEclipse fcee = new FileChangeEntryEclipse(resourceFile, ceet,
					resource.getType() == IResource.FOLDER, project);

			result.add(fcee);

			return true;
		}

		public List<FileChangeEntryEclipse> getResult() {
			return result;
		}
	}
}
