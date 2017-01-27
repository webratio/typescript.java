/**
 *  Copyright (c) 2015-2016 Angelo ZERR.
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  which accompanies this distribution, and is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 *
 *  Contributors:
 *  Angelo Zerr <angelo.zerr@gmail.com> - initial API and implementation
 */
package ts.eclipse.ide.internal.core.resources.jsonconfig;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;

import ts.eclipse.ide.core.TypeScriptCorePlugin;
import ts.eclipse.ide.core.resources.jsconfig.IDETsconfigJson;
import ts.eclipse.ide.core.utils.LRUCache;
import ts.eclipse.ide.core.utils.WorkbenchResourceUtil;
import ts.utils.FileUtils;

/**
 * JSON configuration (tsconfig.json, package.json) file manager.
 *
 */
public class JsonConfigResourcesManager {

	private static final JsonConfigResourcesManager INSTANCE = new JsonConfigResourcesManager();

	private static final IPath TSCONFIG_JSON_PATH = new Path(FileUtils.TSCONFIG_JSON);
	private static final IPath JSCONFIG_JSON_PATH = new Path(FileUtils.JSCONFIG_JSON);

	public static JsonConfigResourcesManager getInstance() {
		return INSTANCE;
	}

	private final Map<IFile, Long> allConfigs;
	private final Map<IFile, IDETsconfigJson> jsconConfig;
	private Map<IFile, JsonConfigScope> definedScopes = null;
	private LRUCache<IResource, Collection<JsonConfigScope>> includingScopesCache = new LRUCache<>(500);
	private LRUCache<IResource, Collection<JsonConfigScope>> emittingScopesCache = new LRUCache<>(100);

	public JsonConfigResourcesManager() {
		this.allConfigs = new HashMap<>();
		this.jsconConfig = new HashMap<IFile, IDETsconfigJson>();
	}

	/**
	 * Starts tracking the given tsconfig.json. If already added, the
	 * information about the file is updated to reflect any change.
	 * 
	 * @param file
	 */
	public void addOrUpdate(IFile file) {
		synchronized (this) {
			long newModificationStamp = file.getModificationStamp();
			Long oldModificationStamp = allConfigs.get(file);
			if (oldModificationStamp == null || oldModificationStamp.longValue() != file.getModificationStamp()) {
				allConfigs.put(file, newModificationStamp);
				// stamp changed (or new file added): invalidate Pojo cache and
				// recompute paths
				invalidate(file);
			}
		}
	}

	/**
	 * Remove the given tsconfig.json from the cache.
	 * 
	 * @param file
	 */
	public void remove(IFile file) {
		synchronized (this) {
			allConfigs.remove(file);
			invalidate(file);
		}
	}

	private void invalidate(IFile file) {
		jsconConfig.remove(file);
		definedScopes = null;
		includingScopesCache.clear();
		emittingScopesCache.clear();
	}

	/**
	 * Find tsconfig.json from the folder (or parent folder) of the given
	 * resource.
	 * 
	 * @param resource
	 * @return
	 */
	public IDETsconfigJson findClosestTsconfig(IResource resource) throws CoreException {
		IFile tsconfigFile = findClosestTsconfigFile(resource);
		if (tsconfigFile != null) {
			return getTsconfig(tsconfigFile);
		}
		return null;
	}

	public IFile findClosestTsconfigFile(IResource resource) throws CoreException {
		IFile tsconfigFile = WorkbenchResourceUtil.findFileInContainerOrParent(resource, TSCONFIG_JSON_PATH);
		return tsconfigFile;
	}

	/**
	 * Find all loaded TypeScript configurations that include the given
	 * resource.
	 * 
	 * @param resource
	 * @return
	 */
	public Set<IDETsconfigJson> findIncludingTsconfigs(IResource resource) {
		return findIncludingTsconfigFilesStream(resource).map(this::getTsconfig).collect(Collectors.toSet());
	}

	public Set<IFile> findIncludingTsconfigFiles(IResource resource) {
		return findIncludingTsconfigFilesStream(resource).collect(Collectors.toSet());
	}

	private Stream<IFile> findIncludingTsconfigFilesStream(IResource resource) {
		return getIncludingScopes(resource).stream().map(JsonConfigScope::getConfigFile);
	}

	/**
	 * Returns the Pojo of the given tsconfig.json file.
	 * 
	 * @param tsconfigFile
	 * @return the Pojo of the given tsconfig.json file.
	 */
	public IDETsconfigJson getTsconfig(IFile tsconfigFile) {
		IDETsconfigJson tsconfig = jsconConfig.get(tsconfigFile);
		if (tsconfig == null) {
			try {
				return createTsConfig(tsconfigFile);
			} catch (CoreException e) {
				TypeScriptCorePlugin.logError(e, "Error retrieving TypeScript configuration of " + tsconfigFile);
			}
		}
		return tsconfig;
	}

	/**
	 * Create Pojo instance of the given tsconfig.json file.
	 * 
	 * @param tsconfigFile
	 * @return Pojo instance of the given tsconfig.json file.
	 * @throws CoreException
	 */
	private synchronized IDETsconfigJson createTsConfig(IFile tsconfigFile) throws CoreException {
		IDETsconfigJson tsconfig = jsconConfig.get(tsconfigFile);
		if (tsconfig != null) {
			return tsconfig;
		}

		tsconfig = IDETsconfigJson.load(tsconfigFile);
		synchronized (jsconConfig) {
			jsconConfig.put(tsconfigFile, tsconfig);
		}
		return tsconfig;
	}

	/**
	 * Find jsconfig.json
	 * 
	 * @param resource
	 * @return
	 * @throws CoreException
	 */
	public IFile findJsconfigFile(IResource resource) throws CoreException {
		return WorkbenchResourceUtil.findFileInContainerOrParent(resource, JSCONFIG_JSON_PATH);
	}

	/**
	 * Gets the scope of the specified configuration file.
	 * 
	 * @param tsconfigFile
	 * @return
	 */
	public JsonConfigScope getDefinedScope(IFile configFile) {
		synchronized (this) {
			if (definedScopes == null) {
				recomputeScopes();
			}
			return definedScopes.get(configFile);
		}
	}

	private synchronized void recomputeScopes() {
		definedScopes = new LinkedHashMap<>();
		for (IFile configFile : allConfigs.keySet()) {
			try {
				JsonConfigScope scope = JsonConfigScope.createFromTsconfig(createTsConfig(configFile));
				definedScopes.put(configFile, scope);
			} catch (CoreException e) {
				TypeScriptCorePlugin.logError(e, "Error computing JSON configuration scopes");
			}
		}
	}

	/**
	 * Gets all scopes that <i>include</i> the specified resource, that it, they
	 * use it as source for compilation.
	 * 
	 * @param file
	 * @return
	 */
	public Collection<JsonConfigScope> getIncludingScopes(IResource resource) {
		synchronized (this) {
			return includingScopesCache.get(resource, this::findIncludingScopes);
		}
	}

	private synchronized Collection<JsonConfigScope> findIncludingScopes(IResource resource) {
		if (definedScopes == null) {
			recomputeScopes();
		}
		List<JsonConfigScope> result = new ArrayList<>(definedScopes.size() / 2);
		for (JsonConfigScope scope : definedScopes.values()) {
			if (scope.includes(resource)) {
				result.add(scope);
			}
		}
		return Collections.unmodifiableList(result);
	}

	/**
	 * Gets all scopes that <i>emit</i> the specified resource, that it, they
	 * create/update it as a result of compilation.
	 * 
	 * @param file
	 * @return
	 */
	public Collection<JsonConfigScope> getEmittingScopes(IResource resource) {
		synchronized (this) {
			return emittingScopesCache.get(resource, this::findEmittingScopes);
		}
	}

	private synchronized Collection<JsonConfigScope> findEmittingScopes(IResource resource) {
		if (definedScopes == null) {
			recomputeScopes();
		}
		List<JsonConfigScope> result = new ArrayList<>(definedScopes.size() / 2);
		for (JsonConfigScope scope : definedScopes.values()) {
			if (scope.emits(resource)) {
				result.add(scope);
			}
		}
		return Collections.unmodifiableList(result);
	}

}
