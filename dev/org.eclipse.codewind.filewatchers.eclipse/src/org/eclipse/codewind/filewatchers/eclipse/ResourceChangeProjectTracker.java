/*******************************************************************************
 * Copyright (c) 2020 IBM Corporation
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
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IPath;

/**
 * This class should ONLY be used to determine the location of a project that
 * has been deleted; in this scenario, IProject.getLocation() returns null, so
 * we need another mechanism to retrieve this value (eg this class).
 * 
 * At present, this class should only be called from
 * CodewindResourceChangeListener.
 */
public class ResourceChangeProjectTracker {

	protected ResourceChangeProjectTracker() {
	}

	/* synchronize on me when accessing */
	private final Map<String /* project name */, String /* project root */> projects = new HashMap<>();

	public Optional<String> getProjectPath(IProject proj) {

		synchronized (projects) {
			System.out.println("project name:" + proj.getName());
			return Optional.ofNullable(projects.get(proj.getName()));
		}

	}

	public void updateProjectPath(IProject proj) {
		if (proj == null || proj.getName() == null) {
			return;
		}

		IPath path = proj.getLocation();
		if (path == null) {
			return;
		}

		File file = path.toFile();
		if (file == null) {
			return;
		}

		String pathFile = file.getPath();

		synchronized (projects) {
			projects.put(proj.getName(), pathFile);
		}

	}

	public void removeProjectPath(IProject proj) {
		if (proj == null || proj.getName() == null) {
			return;
		}

		synchronized (projects) {
			projects.remove(proj.getName());
		}

	}

}
