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
package ts.eclipse.ide.core.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.eclipse.core.filebuffers.FileBuffers;
import org.eclipse.core.filebuffers.ITextFileBuffer;
import org.eclipse.core.filebuffers.ITextFileBufferManager;
import org.eclipse.core.filebuffers.LocationKind;
import org.eclipse.core.resources.ICommand;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.text.IDocument;

import ts.eclipse.ide.core.TypeScriptCorePlugin;
import ts.eclipse.ide.core.builder.TypeScriptBuilder;
import ts.eclipse.ide.core.resources.IIDETypeScriptProject;
import ts.eclipse.ide.core.resources.jsconfig.IDETsconfigJson;
import ts.eclipse.ide.internal.core.resources.IDEResourcesManager;
import ts.eclipse.ide.internal.core.resources.jsonconfig.JsonConfigResourcesManager;
import ts.eclipse.ide.internal.core.resources.jsonconfig.JsonConfigScope;
import ts.resources.TypeScriptResourcesManager;
import ts.utils.FileUtils;
import ts.utils.StringUtils;

/**
 * TypeScript resource utilities.
 *
 */
public class TypeScriptResourceUtil {

	private static final String TSC_TYPE = "tsc";
	private static final String TSLINT_TYPE = "tslint";
	private static final String TSC_MARKER_TYPE = "ts.eclipse.ide.core.typeScriptProblem";

	public static boolean isTsOrTsxFile(Object element) {
		return IDEResourcesManager.getInstance().isTsOrTsxFile(element);
	}

	public static boolean isTsOrTsxOrJsxFile(Object element) {
		return IDEResourcesManager.getInstance().isTsOrTsxOrJsxFile(element);
	}

	public static boolean isTsxOrJsxFile(Object element) {
		return IDEResourcesManager.getInstance().isTsxOrJsxFile(element);
	}

	public static boolean isJsOrJsMapFile(Object element) {
		return IDEResourcesManager.getInstance().isJsOrJsMapFile(element);
	}

	/**
	 * Returns true if the given project contains one or several "tsconfig.json"
	 * file(s) false otherwise.
	 * 
	 * To have a very good performance, "tsconfig.json" is not searched by
	 * scanning the whole files of the project but it checks if "tsconfig.json"
	 * exists in several folders ('/tsconfig.json' or '/src/tsconfig.json).
	 * Those folders can be customized with preferences buildpath
	 * {@link TypeScriptCorePreferenceConstants#TYPESCRIPT_BUILD_PATH}.
	 * 
	 * @param project
	 *            Eclipse project.
	 * @return true if the given project contains one or several "tsconfig.json"
	 *         file(s) false otherwise.
	 */
	public static boolean isTypeScriptProject(IProject project) {
		if (!project.isAccessible()) {
			return false;
		}
		return IDEResourcesManager.getInstance().isTypeScriptProject(project);
	}

	public static boolean canConsumeTsserver(IProject project, Object fileObject) {
		return IDEResourcesManager.getInstance().canConsumeTsserver(project, fileObject);
	}

	public static boolean canConsumeTsserver(IResource resource) {
		if (resource == null) {
			return false;
		}
		return canConsumeTsserver(resource.getProject(), resource);
	}

	// --------------------------- TypeScript Builder

	/**
	 * 
	 * @param project
	 * @return
	 */
	public static boolean hasTypeScriptBuilder(IProject project) {
		try {
			IProjectDescription description = project.getDescription();
			ICommand[] commands = description.getBuildSpec();
			for (int i = 0; i < commands.length; i++) {
				if (TypeScriptBuilder.ID.equals(commands[i].getBuilderName())) {
					return true;
				}
			}
		} catch (CoreException e) {
			return false;
		}
		return false;
	}

	public static void removeTypeScriptBuilder(IProject project) throws CoreException {
		IProjectDescription description = project.getDescription();
		ICommand[] commands = description.getBuildSpec();
		for (int i = 0; i < commands.length; i++) {
			if (TypeScriptBuilder.ID.equals(commands[i].getBuilderName())) {
				// Remove the builder
				ICommand[] newCommands = new ICommand[commands.length - 1];
				System.arraycopy(commands, 0, newCommands, 0, i);
				System.arraycopy(commands, i + 1, newCommands, i, commands.length - i - 1);
				description.setBuildSpec(newCommands);
				project.setDescription(description, null);
			}
		}
	}

	public static void addTypeScriptBuilder(IProject project) throws CoreException {
		IProjectDescription description = project.getDescription();
		ICommand[] commands = description.getBuildSpec();
		ICommand[] newCommands = new ICommand[commands.length + 1];
		System.arraycopy(commands, 0, newCommands, 0, commands.length);
		ICommand command = description.newCommand();
		command.setBuilderName(TypeScriptBuilder.ID);
		newCommands[newCommands.length - 1] = command;
		description.setBuildSpec(newCommands);
		project.setDescription(description, null);
	}

	/**
	 * Returns the TypeScript project of the given eclipse project and throws
	 * exception if the eclipse project has not TypeScript nature.
	 * 
	 * @param project
	 *            eclipse project.
	 * @return the TypeScript project of the given eclipse projectand throws
	 *         exception if the eclipse project has not TypeScript nature.
	 * @throws CoreException
	 */
	public static IIDETypeScriptProject getTypeScriptProject(IProject project, boolean force) throws CoreException {
		try {
			return (IIDETypeScriptProject) TypeScriptResourcesManager.getTypeScriptProject(project, force);
		} catch (Exception e) {
			throw new CoreException(new Status(IStatus.ERROR, TypeScriptCorePlugin.PLUGIN_ID,
					"The project " + project.getName() + " cannot be converted as TypeScript project.", e));
		}
	}

	/**
	 * Returns the TypeScript project of the given eclipse project and throws
	 * exception if the eclipse project has not TypeScript nature.
	 * 
	 * @param project
	 *            eclipse project.
	 * @return the TypeScript project of the given eclipse projectand throws
	 *         exception if the eclipse project has not TypeScript nature.
	 * @throws CoreException
	 */
	public static IIDETypeScriptProject getTypeScriptProject(IProject project) throws CoreException {
		IIDETypeScriptProject result = (IIDETypeScriptProject) TypeScriptResourcesManager.getTypeScriptProject(project);
		if (result == null) {
			throw new CoreException(new Status(IStatus.ERROR, TypeScriptCorePlugin.PLUGIN_ID,
					"The project " + project.getName() + " is not a TypeScript project."));
		}
		return result;
	}

	// ------------------ emitted files *.js, *.js.map

	/**
	 * Returns true if the given *.js file or *.js.map have a corresponding *.ts
	 * file in the same folder and false otherwise.
	 * 
	 * @param jsOrJsMapFile
	 *            *.js file or *.js.map file.
	 * @return true if the given *.js file or *.js.map have a corresponding *.ts
	 *         file in the same folder and false otherwise.
	 */
	public static boolean isObviousEmittedFile(IFile jsOrJsMapFile) {
		if (!isJsOrJsMapFile(jsOrJsMapFile)) {
			return false;
		}
		String tsFilename = IDEResourcesManager.getInstance().getTypeScriptFilename(jsOrJsMapFile);
		if (StringUtils.isEmpty(tsFilename)) {
			return false;
		}
		return jsOrJsMapFile.getParent().exists(new Path(tsFilename))
				|| jsOrJsMapFile.getParent().exists(new Path(tsFilename + "x"));
	}

	public static Object[] getEmittedFiles(IFile tsFile) throws CoreException {
		if (!isTsOrTsxFile(tsFile)) {
			return null;
		}
		List<IFile> emittedFiles = new ArrayList<IFile>();
		refreshAndCollectEmittedFiles(tsFile, false, emittedFiles);
		return emittedFiles.toArray();
	}

	public static void refreshAndCollectEmittedFiles(IFile tsFile, boolean refresh, List<IFile> emittedFiles)
			throws CoreException {
		if (!isTsOrTsxFile(tsFile)) {
			return;
		}

		// Find all tsconfig.json's
		for (IDETsconfigJson tsconfig : findIncludingTsconfigs(tsFile)) {
			refreshAndCollectEmittedFiles(tsFile, tsconfig, refresh, emittedFiles);
		}
	}

	public static void refreshAndCollectEmittedFiles(IFile tsFile, IDETsconfigJson tsconfig, boolean refresh,
			List<IFile> emittedFiles) throws CoreException {
		JsonConfigScope scope = JsonConfigResourcesManager.getInstance().getDefinedScope(tsconfig.getTsconfigFile());

		// Refresh emitted files
		for (IFile file : scope.getSpecificEmittedFiles(tsFile)) {
			refreshAndCollectEmittedFile(file.getFullPath(), refresh, emittedFiles);
		}
		// Refresh ts file
		if (refresh) {
			refreshFile(tsFile, false);
		}
	}

	/**
	 * 
	 * @param emittedFilePath
	 * @param refresh
	 * @param emittedFiles
	 * @throws CoreException
	 */
	public static void refreshAndCollectEmittedFile(IPath emittedFilePath, boolean refresh, List<IFile> emittedFiles)
			throws CoreException {
		IFile emittedFile = null;
		if (refresh) {
			// refresh emitted file *.js, *.js.map
			emittedFile = getRoot().getFile(emittedFilePath);
			refreshFile(emittedFile, true);
		}

		if (emittedFiles != null) {
			if (emittedFile == null && getRoot().exists(emittedFilePath)) {
				emittedFile = getRoot().getFile(emittedFilePath);
			}
			if (emittedFile != null) {
				emittedFiles.add(emittedFile);
			}
		}
	}

	private static IWorkspaceRoot getRoot() {
		return ResourcesPlugin.getWorkspace().getRoot();
	}

	public static void refreshFile(IFile file, boolean emittedFile) throws CoreException {
		file.refreshLocal(IResource.DEPTH_INFINITE, null);
		if (emittedFile && file.exists()) {
			file.setDerived(true, null);
		}
	}

	public static void deleteEmittedFiles(IFile tsFile, IDETsconfigJson tsconfig) throws CoreException {
		List<IFile> emittedFiles = new ArrayList<IFile>();
		TypeScriptResourceUtil.refreshAndCollectEmittedFiles(tsFile, tsconfig, false, emittedFiles);
		for (IFile emittedFile : emittedFiles) {
			emittedFile.delete(true, null);
		}
	}

	public static IDETsconfigJson findClosestTsconfig(IResource resource) throws CoreException {
		return JsonConfigResourcesManager.getInstance().findClosestTsconfig(resource);
	}

	public static Set<IDETsconfigJson> findIncludingTsconfigs(IResource resource) {
		return JsonConfigResourcesManager.getInstance().findIncludingTsconfigs(resource);
	}

	public static IDETsconfigJson getTsconfig(IFile tsconfigFile) throws CoreException {
		return JsonConfigResourcesManager.getInstance().getTsconfig(tsconfigFile);
	}

	public static IFile getBuildPathContainer(Object receiver) {
		if (receiver instanceof IAdaptable) {
			IResource resource = (IResource) ((IAdaptable) receiver).getAdapter(IResource.class);
			if (resource != null) {
				switch (resource.getType()) {
				case IResource.FILE:
					if (isTsConfigFile(resource)) {
						return (IFile) resource;
					}
				default:
					return null;
				}
			}
		}
		return null;
	}

	public static boolean isTsConfigFile(IResource resource) {
		return resource.getType() == IResource.FILE && FileUtils.isTsConfigFile(resource.getName());
	}

	public static String getBuildPathLabel(IFile tsconfigFile) {
		return new StringBuilder("").append(tsconfigFile.getProjectRelativePath().toString()).toString();
	}

	public static IMarker addTscMarker(IResource resource, String message, int severity, int lineNumber)
			throws CoreException {
		IMarker marker = resource.createMarker(TSC_MARKER_TYPE);
		marker.setAttribute(IMarker.MESSAGE, message);
		marker.setAttribute(IMarker.SEVERITY, severity);
		marker.setAttribute(IMarker.LINE_NUMBER, lineNumber);
		return marker;
	}

	public static IMarker addTscMarker(IResource resource, String message, int severity, int lineNumber, int charStart,
			int charEnd) throws CoreException {
		IMarker marker = addTscMarker(resource, message, severity, lineNumber);
		marker.setAttribute(IMarker.CHAR_START, charStart);
		marker.setAttribute(IMarker.CHAR_END, charEnd);
		return marker;
	}

	public static void deleteTscMarker(IResource resource) throws CoreException {
		resource.deleteMarkers(TSC_MARKER_TYPE, true, IResource.DEPTH_INFINITE);
	}

	public static String formatError(String cmd, String code, String message) {
		StringBuilder error = new StringBuilder(cmd);
		error.append(" (");
		error.append(code);
		error.append("): '");
		error.append(message);
		error.append("'");
		return error.toString();
	}

	public static String formatTslintError(String code, String message) {
		return formatError(TSLINT_TYPE, code, message);
	}

	public static String formatTscError(String code, String message) {
		return formatError(TSC_TYPE, code, message);
	}

	/**
	 * Returns the {@link IDocument} from the given file and null if it's not
	 * possible.
	 */
	public static IDocument getDocument(IFile file) {
		ITextFileBufferManager manager = FileBuffers.getTextFileBufferManager();
		IPath location = file.getLocation();
		boolean connected = false;
		try {
			ITextFileBuffer buffer = manager.getTextFileBuffer(location, LocationKind.NORMALIZE);
			if (buffer == null) {
				// no existing file buffer..create one
				manager.connect(location, LocationKind.NORMALIZE, new NullProgressMonitor());
				connected = true;
				buffer = manager.getTextFileBuffer(location, LocationKind.NORMALIZE);
				if (buffer == null) {
					return null;
				}
			}

			return buffer.getDocument();
		} catch (CoreException ce) {
			TypeScriptCorePlugin.logError(ce, "Error while getting document from file");
			return null;
		} finally {
			if (connected) {
				try {
					manager.disconnect(location, LocationKind.NORMALIZE, new NullProgressMonitor());
				} catch (CoreException e) {
					TypeScriptCorePlugin.logError(e, "Error while getting document from file");
				}
			}
		}
	}

	/**
	 * Returns the file from the given {@link IDocument}.
	 */
	public static IFile getFile(IDocument document) {
		ITextFileBufferManager bufferManager = FileBuffers.getTextFileBufferManager(); // get
																						// the
																						// buffer
																						// manager
		ITextFileBuffer buffer = bufferManager.getTextFileBuffer(document);
		IPath location = buffer == null ? null : buffer.getLocation();
		if (location == null) {
			return null;
		}

		return ResourcesPlugin.getWorkspace().getRoot().getFile(location);
	}

}
