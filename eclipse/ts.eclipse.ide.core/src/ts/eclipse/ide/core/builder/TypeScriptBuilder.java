/**
 *  Copyright (c) 2015-2017 Angelo ZERR.
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  which accompanies this distribution, and is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 *
 *  Contributors:
 *  Angelo Zerr <angelo.zerr@gmail.com> - initial API and implementation
 */
package ts.eclipse.ide.core.builder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IResourceDeltaVisitor;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;

import ts.TypeScriptException;
import ts.client.CommandNames;
import ts.eclipse.ide.core.TypeScriptCorePlugin;
import ts.eclipse.ide.core.compiler.IIDETypeScriptCompileResult;
import ts.eclipse.ide.core.resources.IIDETypeScriptProject;
import ts.eclipse.ide.core.resources.buildpath.ITsconfigBuildPath;
import ts.eclipse.ide.core.resources.buildpath.ITypeScriptBuildPath;
import ts.eclipse.ide.core.resources.jsconfig.IDETsconfigJson;
import ts.eclipse.ide.core.utils.PersistedState;
import ts.eclipse.ide.core.utils.TypeScriptResourceUtil;
import ts.eclipse.ide.internal.core.Trace;
import ts.eclipse.ide.internal.core.resources.jsonconfig.JsonConfigResourcesManager;
import ts.eclipse.ide.internal.core.resources.jsonconfig.JsonConfigScope;

/**
 * Builder to transpile TypeScript files into JavaScript files and source map if
 * needed.
 *
 */
public class TypeScriptBuilder extends IncrementalProjectBuilder {

	public static final String ID = "ts.eclipse.ide.core.typeScriptBuilder";

	private static final PersistedState PERSISTED_STATE;
	static {
		try {
			PERSISTED_STATE = new PersistedState(TypeScriptCorePlugin.getDefault(), "TypeScriptBuilder");
		} catch (CoreException e) {
			throw new RuntimeException("Error initializing builder persisted state", e);
		}
	}

	private List<IIDETypeScriptCompileResult> lastCompileResults;

	@Override
	protected IProject[] build(int kind, Map<String, String> args, final IProgressMonitor monitor)
			throws CoreException {
		IProject project = this.getProject();
		if (!TypeScriptResourceUtil.isTypeScriptProject(project)) {
			return null;
		}

		List<IIDETypeScriptCompileResult> results = this.lastCompileResults = new ArrayList<>();
		try {
			IIDETypeScriptProject tsProject = TypeScriptResourceUtil.getTypeScriptProject(project);
			if (kind == FULL_BUILD) {
				fullBuild(tsProject, monitor);
			} else {
				Collection<IResourceDelta> deltas = collectDeltas();
				if (deltas.isEmpty()) {
					fullBuild(tsProject, monitor);
				} else {
					incrementalBuild(tsProject, deltas, monitor);
				}
			}
		} finally {
			this.lastCompileResults = null;
		}

		Set<IProject> inputProjects = computeInputProjects(results);
		return inputProjects.isEmpty() ? null : inputProjects.toArray(new IProject[inputProjects.size()]);
	}

	private Collection<IResourceDelta> collectDeltas() {
		Collection<IProject> projects = retrievePreviousInputProjects();
		List<IResourceDelta> deltas = new ArrayList<>(projects.size());
		{
			IResourceDelta delta = getDelta(getProject());
			if (delta == null) {
				return Collections.emptyList();
			}
			deltas.add(delta);
		}
		for (IProject project : projects) {
			IResourceDelta delta = getDelta(project);
			if (delta == null) {
				return Collections.emptyList();
			}
			deltas.add(delta);
		}
		return deltas;
	}

	private Set<IProject> retrievePreviousInputProjects() {
		String previousInputProjectsNames = (String) PERSISTED_STATE.get("inputProjects." + getProject().getName(),
				() -> "");
		if (previousInputProjectsNames.isEmpty()) {
			return new HashSet<>();
		}
		IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
		return Stream.of(previousInputProjectsNames.split(",")).map(root::getProject).collect(Collectors.toSet());
	}

	private Set<IProject> computeInputProjects(Collection<IIDETypeScriptCompileResult> compileResults) {
		Set<IProject> previousInputProjects = retrievePreviousInputProjects();
		Set<IProject> newInputProjects = new HashSet<>();
		boolean reliable = true;
		for (IIDETypeScriptCompileResult result : compileResults) {
			if (!result.didComplete() || !result.wasFullBuild()) {
				reliable = false;
			}
			newInputProjects.addAll(result.getAccessedProjects());
		}
		if (!reliable) {
			newInputProjects.addAll(previousInputProjects);
		}
		newInputProjects.remove(getProject());
		PERSISTED_STATE.put("inputProjects." + getProject().getName(),
				newInputProjects.stream().map(IProject::getName).collect(Collectors.joining(",")));
		return newInputProjects;
	}

	private void fullBuild(IIDETypeScriptProject tsProject, IProgressMonitor monitor) throws CoreException {
		ITypeScriptBuildPath buildPath = tsProject.getTypeScriptBuildPath();
		ITsconfigBuildPath[] tsContainers = buildPath.getTsconfigBuildPaths();
		for (int i = 0; i < tsContainers.length; i++) {
			ITsconfigBuildPath tsContainer = tsContainers[i];
			try {
				IDETsconfigJson tsconfig = tsContainer.getTsconfig();
				compileWithTsc(tsProject, tsconfig);
			} catch (TypeScriptException e) {
				Trace.trace(Trace.SEVERE, "Error while tsc compilation", e);
			}
		}
	}

	private void incrementalBuild(IIDETypeScriptProject tsProject, Collection<IResourceDelta> deltas, IProgressMonitor monitor)
			throws CoreException {
		if (tsProject.canSupport(CommandNames.CompileOnSaveEmitFile)) {
			// compile with tsserver (since TypeScript 2.0.5)
			// WR cannot compile with tsserver because of open bugs that make
			// markers unreliable
			// compileWithTsserver(tsProject, deltas, monitor);
			compileWithTsc(tsProject, deltas, monitor);
			// WR end
		} else {
			// compile with tsc (more slow than tsserver).
			compileWithTsc(tsProject, deltas, monitor);
		}
	}

	/**
	 * Compile files with tsc.
	 * 
	 * @param tsProject
	 * @param deltas
	 * @param monitor
	 * @throws CoreException
	 */
	private void compileWithTsc(IIDETypeScriptProject tsProject, Collection<IResourceDelta> deltas, IProgressMonitor monitor)
			throws CoreException {

		final Set<IProject> invalidatedProjects = new HashSet<>();
		final IResourceDeltaVisitor tsconfigDeltaVisitor = new IResourceDeltaVisitor() {

			@Override
			public boolean visit(IResourceDelta delta) throws CoreException {
				IResource resource = delta.getResource();
				if (resource == null) {
					return false;
				}
				switch (resource.getType()) {
				case IResource.ROOT:
					return true;
				case IResource.PROJECT:
					return true;
				case IResource.FOLDER:
					if ("node_modules".equals(resource.getName()) && delta.getAffectedChildren().length > 0) {
						invalidatedProjects.add(resource.getProject());
						return false; // no need to explore module differences
					}
					return true;
				case IResource.FILE:
					int kind = delta.getKind();
					switch (kind) {
					case IResourceDelta.ADDED:
					case IResourceDelta.CHANGED:
						if (TypeScriptResourceUtil.isTsConfigFile(resource)) {
							JsonConfigResourcesManager.getInstance().addOrUpdate((IFile) resource);
							invalidatedProjects.add(resource.getProject());
						}
						break;
					case IResourceDelta.REMOVED:
						if (TypeScriptResourceUtil.isTsConfigFile(resource)) {
							JsonConfigResourcesManager.getInstance().remove((IFile) resource);
							invalidatedProjects.add(resource.getProject());
						}
						break;
					}
					return false;
				}
				return false;
			}
		};
		for (IResourceDelta delta : deltas) {
			delta.accept(tsconfigDeltaVisitor);
		}
		
		// If a tsconfig.json or installed modules in this project changed, back
		// off and do a full build instead
		for (IProject prj : invalidatedProjects) {
			if (prj.equals(getProject())) {
				fullBuild(tsProject, monitor);
				return;
			}
		}
		
		final ITypeScriptBuildPath buildPath = tsProject.getTypeScriptBuildPath();
		final Map<ITsconfigBuildPath, List<IFile>> tsFilesToCompile = new HashMap<ITsconfigBuildPath, List<IFile>>();
		final Map<ITsconfigBuildPath, List<IFile>> tsFilesToDelete = new HashMap<ITsconfigBuildPath, List<IFile>>();
		final IResourceDeltaVisitor deltaVisitor = new IResourceDeltaVisitor() {

			private int lastScopeFolderDepth = -1;
			
			@Override
			public boolean visit(IResourceDelta delta) throws CoreException {
				IResource resource = delta.getResource();
				if (resource == null) {
					return false;
				}
				switch (resource.getType()) {
				case IResource.ROOT:
					return true;
				case IResource.PROJECT:
					return TypeScriptResourceUtil.isTypeScriptProject((IProject) resource);
				case IResource.FOLDER:
					int folderDepth = resource.getFullPath().segmentCount();
					if (lastScopeFolderDepth < 0 || folderDepth <= lastScopeFolderDepth) {
						if (buildPath.isScopeEntrance(resource)) {
							lastScopeFolderDepth = folderDepth;
							return true;
						} else {
							lastScopeFolderDepth = -1;
							return false;
						}
					} else {
						return true;
					}
				case IResource.FILE:
					int kind = delta.getKind();
					switch (kind) {
					case IResourceDelta.ADDED:
					case IResourceDelta.CHANGED:
						if (TypeScriptResourceUtil.isTsOrTsxFile(resource)
								&& !TypeScriptResourceUtil.isDefinitionTsFile(resource)) {
							addTsFile(buildPath, tsFilesToCompile, resource);
						}
						break;
					case IResourceDelta.REMOVED:
						if (TypeScriptResourceUtil.isTsOrTsxFile(resource)
								&& !TypeScriptResourceUtil.isDefinitionTsFile(resource)) {
							addTsFile(buildPath, tsFilesToDelete, resource);
						}
						break;
					}
					return false;
				}
				return false;
			}

			private void addTsFile(final ITypeScriptBuildPath buildPath,
					final Map<ITsconfigBuildPath, List<IFile>> tsFiles, IResource resource) {
				ITsconfigBuildPath tsContainer = buildPath.findTsconfigBuildPath(resource);
				if (tsContainer != null) {
					List<IFile> deltas = tsFiles.get(tsContainer);
					if (deltas == null) {
						deltas = new ArrayList<IFile>();
						tsFiles.put(tsContainer, deltas);
					}
					deltas.add((IFile) resource);
				}
			}
		};
		for (IResourceDelta delta : deltas) {
			delta.accept(deltaVisitor);
		}

		// Compile ts files *.ts
		for (Entry<ITsconfigBuildPath, List<IFile>> entries : tsFilesToCompile.entrySet()) {
			ITsconfigBuildPath tsContainer = entries.getKey();
			List<IFile> tsFiles = entries.getValue();
			try {
				// compile ts files
				IDETsconfigJson tsconfig = tsContainer.getTsconfig();
				if (!tsconfig.isBuildOnSave() && tsconfig.isCompileOnSave()
						&& tsProject.canSupport(CommandNames.CompileOnSaveEmitFile)) {
					// TypeScript >=2.0.5: compile is done with tsserver
					// compileWithTsserver(tsProject, tsFiles, tsconfig);
					compileWithTsc(tsProject, tsFiles, tsconfig);
				} else {
					// TypeScript < 2.0.5: compile is done with tsc which is not
					// very
					// performant.
					compileWithTsc(tsProject, tsFiles, tsconfig);
				}
				// validate ts files with tslint
				tsProject.getTslint().lint(tsconfig, tsFiles, tsProject.getProjectSettings());
			} catch (TypeScriptException e) {
				Trace.trace(Trace.SEVERE, "Error while tsc compilation", e);
			}
		}

		// Delete emitted files *.js, *.js.map
		for (Entry<ITsconfigBuildPath, List<IFile>> entries : tsFilesToDelete.entrySet()) {
			ITsconfigBuildPath tsContainer = entries.getKey();
			List<IFile> tsFiles = entries.getValue();
			IDETsconfigJson tsconfig = tsContainer.getTsconfig();
			for (IFile tsFile : tsFiles) {
				TypeScriptResourceUtil.deleteEmittedFiles(tsFile, tsconfig);
			}

		}
	}

	/**
	 * Compile the given tsconfig.json with tsc.
	 * 
	 * @param tsProject
	 * @param tsconfig
	 * @throws TypeScriptException
	 * @throws CoreException
	 */
	public void compileWithTsc(IIDETypeScriptProject tsProject, IDETsconfigJson tsconfig)
			throws TypeScriptException, CoreException {
		IIDETypeScriptCompileResult result = tsProject.getCompiler().compile(tsconfig);
		this.lastCompileResults.add(result);
	}
	
	/**
	 * Compile the given ts files with tsc.
	 * 
	 * @param tsProject
	 * @param tsFiles
	 * @param tsconfig
	 * @throws TypeScriptException
	 * @throws CoreException
	 */
	private void compileWithTsc(IIDETypeScriptProject tsProject, List<IFile> tsFiles, IDETsconfigJson tsconfig)
			throws TypeScriptException, CoreException {
		IIDETypeScriptCompileResult result = tsProject.getCompiler().compile(tsconfig, tsFiles);
		this.lastCompileResults.add(result);
	}

	/**
	 * Compile files with tsserver (since TypeScript 2.0.5).
	 * 
	 * @param tsProject
	 * @param delta
	 * @param monitor
	 * @throws CoreException
	 */
	private void compileWithTsserver(IIDETypeScriptProject tsProject, Collection<IResourceDelta> deltas,
			IProgressMonitor monitor)
			throws CoreException {

		final List<IFile> updatedTsFiles = new ArrayList<>();
		final List<IFile> removedTsFiles = new ArrayList<>();
		final IResourceDeltaVisitor deltaVisitor = new IResourceDeltaVisitor() {

			@Override
			public boolean visit(IResourceDelta delta) throws CoreException {
				IResource resource = delta.getResource();
				if (resource == null) {
					return false;
				}
				switch (resource.getType()) {
				case IResource.ROOT:
				case IResource.FOLDER:
					return true;
				case IResource.PROJECT:
					return TypeScriptResourceUtil.isTypeScriptProject((IProject) resource);
				case IResource.FILE:
					if (!TypeScriptResourceUtil.isTsOrTsxFile(resource)
							|| TypeScriptResourceUtil.isDefinitionTsFile(resource)) {
						return false;
					}
					int kind = delta.getKind();
					switch (kind) {
					case IResourceDelta.ADDED:
					case IResourceDelta.CHANGED:
						updatedTsFiles.add((IFile) resource);
						break;
					case IResourceDelta.REMOVED:
						removedTsFiles.add((IFile) resource);
						break;
					}
					return false;
				default:
					return false;
				}
			};
		};
		for (IResourceDelta delta : deltas) {
			delta.accept(deltaVisitor);
		}

		try {
			tsProject.compileWithTsserver(updatedTsFiles, removedTsFiles, monitor);
		} catch (TypeScriptException e) {
			throw new CoreException(new Status(IStatus.ERROR, TypeScriptCorePlugin.PLUGIN_ID,
					"Error while compiling with tsserver", e));
		}
	}
	
	@Override
	protected void clean(IProgressMonitor monitor) throws CoreException {
		IProject project = this.getProject();
		if (!TypeScriptResourceUtil.isTypeScriptProject(project)) {
			return;
		}

		IIDETypeScriptProject tsProject = TypeScriptResourceUtil.getTypeScriptProject(project);
		ITypeScriptBuildPath buildPath = tsProject.getTypeScriptBuildPath();
		ITsconfigBuildPath[] tsContainers = buildPath.getTsconfigBuildPaths();
		for (int i = 0; i < tsContainers.length; i++) {
			ITsconfigBuildPath tsContainer = tsContainers[i];
			try {
				IDETsconfigJson tsconfig = tsContainer.getTsconfig();
				JsonConfigScope scope = JsonConfigResourcesManager.getInstance()
						.getDefinedScope(tsconfig.getTsconfigFile());
				// Delete all emitted files and markers
				getProject().accept(resource -> {
					if (resource instanceof IFile) {
						if (scope.emits(resource)) {
							resource.delete(true, null);
						}
						return false;
					}
					return (resource instanceof IContainer);
				});
				TypeScriptResourceUtil.deleteTscMarker(getProject());
			} catch (CoreException e) {
				Trace.trace(Trace.SEVERE, "Error while cleaning", e);
			}
		}
	}

}
