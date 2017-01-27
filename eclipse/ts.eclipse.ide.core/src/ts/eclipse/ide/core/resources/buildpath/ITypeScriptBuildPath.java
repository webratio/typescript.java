/**
 *  Copyright (c)s 2015-2016 Angelo ZERR.
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  which accompanies this distribution, and is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 *
 *  Contributors:
 *  Angelo Zerr <angelo.zerr@gmail.com> - initial API and implementation
 */
package ts.eclipse.ide.core.resources.buildpath;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;

/**
 * TypeScript build path API.
 *
 */
public interface ITypeScriptBuildPath {

	/**
	 * Returns list of folders root of the project which hosts "tsconfig.json".
	 * 
	 * @return list of folders root of the project which hosts "tsconfig.json".
	 */
	ITsconfigBuildPath[] getTsconfigBuildPaths();

	/**
	 * Determines whether the given resource leads to the start (or IS the
	 * start) of the scope of files included in the build path.
	 * 
	 * @param resource
	 * @return {@code true} if the resource leads to the scope.
	 */
	boolean isScopeEntrance(IResource resource);

	/**
	 * Determines whether the given resource is in the scope of files included
	 * in the build path.
	 * 
	 * @param resource
	 * @return {@code true} if the resource is in the scope.
	 */
	boolean isInScope(IResource resource);

	ITsconfigBuildPath findTsconfigBuildPath(IResource resource);
	
	void addEntry(IFile tsconfigFile);

	void addEntry(ITypeScriptBuildPathEntry entry);

	void removeEntry(IFile tsconfigFile);

	void removeEntry(ITypeScriptBuildPathEntry entry);

	boolean isInBuildPath(IFile tsconfigFile);

	ITsconfigBuildPath getTsconfigBuildPath(IFile tsconfigFile);

	ITypeScriptBuildPath copy();

	void clear();

	void save();

	boolean hasRootContainers();

}
