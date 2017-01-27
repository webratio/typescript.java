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
package ts.eclipse.ide.core;

import java.io.File;
import java.io.IOException;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Plugin;
import org.eclipse.core.runtime.Status;
import org.osgi.framework.BundleContext;

import ts.eclipse.ide.core.nodejs.INodejsInstallManager;
import ts.eclipse.ide.core.repository.IIDETypeScriptRepositoryManager;
import ts.eclipse.ide.core.resources.ITypeScriptElementChangedListener;
import ts.eclipse.ide.core.resources.watcher.IFileWatcherListener;
import ts.eclipse.ide.core.resources.watcher.IResourcesWatcher;
import ts.eclipse.ide.core.utils.WorkbenchResourceUtil;
import ts.eclipse.ide.internal.core.nodejs.NodejsInstallManager;
import ts.eclipse.ide.internal.core.repository.IDETypeScriptRepositoryManager;
import ts.eclipse.ide.internal.core.resources.IDEResourcesManager;
import ts.eclipse.ide.internal.core.resources.jsonconfig.JsonConfigResourcesManager;
import ts.eclipse.ide.internal.core.resources.watcher.ResourcesWatcher;
import ts.resources.ConfigurableTypeScriptResourcesManager;
import ts.utils.FileUtils;

/**
 * The activator class controls the plug-in life cycle
 */
public class TypeScriptCorePlugin extends Plugin {

	public static final String PLUGIN_ID = "ts.eclipse.ide.core"; //$NON-NLS-1$

	// The shared instance.
	private static TypeScriptCorePlugin plugin;

	/**
	 * The constructor.
	 */
	public TypeScriptCorePlugin() {
		super();
		plugin = this;
	}

	@Override
	public void start(BundleContext context) throws Exception {
		super.start(context);

		// set up resource management for IDE
		ConfigurableTypeScriptResourcesManager resourceManager = ConfigurableTypeScriptResourcesManager.getInstance();
		resourceManager.setTypeScriptResourcesManagerDelegate(IDEResourcesManager.getInstance());

		// keep JSON configuration manager posted about changes in tsconfig.json
		JsonConfigResourcesManager jsonConfigResourceManager = JsonConfigResourcesManager.getInstance();
		ResourcesWatcher.getInstance().addGlobalFileWatcherListener(FileUtils.TSCONFIG_JSON, new IFileWatcherListener() {
			@Override
			public void onDeleted(IFile file) {
				jsonConfigResourceManager.remove(file);
			}
			@Override
			public void onChanged(IFile file) {
				jsonConfigResourceManager.addOrUpdate(file);
			}
			@Override
			public void onAdded(IFile file) {
				jsonConfigResourceManager.addOrUpdate(file);
			}
		});
		WorkbenchResourceUtil.findAllFilesFromWorkspaceByName(FileUtils.TSCONFIG_JSON).forEach(jsonConfigResourceManager::addOrUpdate);
	}

	/**
	 * Returns the TypeScript repository base directory.
	 * 
	 * @return the TypeScript repository base directory.
	 * @throws IOException
	 */
	public static File getTypeScriptRepositoryBaseDir() throws IOException {
		return FileLocator.getBundleFile(Platform.getBundle("ts.repository"));
	}

	@Override
	public void stop(BundleContext context) throws Exception {
		ResourcesWatcher.getInstance().dispose();
		plugin = null;
		super.stop(context);
	}

	/**
	 * Returns the shared instance
	 * 
	 * @return the shared instance
	 */
	public static TypeScriptCorePlugin getDefault() {
		return plugin;
	}

	/**
	 * Returns the instance of the TypeScript server type manager.
	 * 
	 * @return the instance of the TypeScript server type manager.
	 */
	// public static ITSServerTypeManager getTSServerTypeManager() {
	// return TSServerTypeManager.getManager();
	// }
	//
	// public static void addTSProjectLifeCycleListener(
	// ITSProjectLifecycleListener listener) {
	// TSProjectLifecycleManager.getManager()
	// .addTSProjectLifeCycleListener(listener);
	// }
	//
	// public static void removeTSProjectLifeCycleListener(
	// ITSProjectLifecycleListener listener) {
	// TSProjectLifecycleManager.getManager()
	// .removeTSProjectLifeCycleListener(listener);
	//
	// }
	//
	// public static ITSRepositoryManager getTSRepositoryManager() {
	// return TSRepositoryManager.getManager();
	// }

	/**
	 * Returns the Nodejs install manager.
	 * 
	 * @return the Nodejs install manager.
	 */
	public static INodejsInstallManager getNodejsInstallManager() {
		return NodejsInstallManager.getManager();
	}

	public static IIDETypeScriptRepositoryManager getTypeScriptRepositoryManager() {
		return IDETypeScriptRepositoryManager.INSTANCE;
	}

	public static IResourcesWatcher getResourcesWatcher() {
		return ResourcesWatcher.getInstance();
	}

	public void addTypeScriptElementChangedListener(ITypeScriptElementChangedListener listener) {
		IDEResourcesManager.getInstance().addTypeScriptElementChangedListener(listener);
	}

	public void removeTypeScriptElementChangedListener(ITypeScriptElementChangedListener listener) {
		IDEResourcesManager.getInstance().removeTypeScriptElementChangedListener(listener);
	}

	public static void logError(Throwable e) {
		getDefault().getLog().log(new Status(IStatus.ERROR, PLUGIN_ID, e.getMessage(), e));
	}

	public static void logError(Throwable e, String message) {
		getDefault().getLog().log(new Status(IStatus.ERROR, PLUGIN_ID, message, e));
	}

}
