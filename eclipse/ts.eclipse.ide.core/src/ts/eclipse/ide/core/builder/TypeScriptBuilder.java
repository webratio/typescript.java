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

import ts.TypeScriptException;
import ts.client.CommandNames;
import ts.eclipse.ide.core.TypeScriptCorePlugin;
import ts.eclipse.ide.core.compiler.IIDETypeScriptCompileResult;
import ts.eclipse.ide.core.resources.IIDETypeScriptFile;
import ts.eclipse.ide.core.resources.IIDETypeScriptProject;
import ts.eclipse.ide.core.resources.buildpath.ITsconfigBuildPath;
import ts.eclipse.ide.core.resources.buildpath.ITypeScriptBuildPath;
import ts.eclipse.ide.core.resources.jsconfig.IDETsconfigJson;
import ts.eclipse.ide.core.utils.PersistedState;
import ts.eclipse.ide.core.utils.TypeScriptResourceUtil;
import ts.eclipse.ide.core.utils.WorkbenchResourceUtil;
import ts.eclipse.ide.internal.core.Trace;
import ts.eclipse.ide.internal.core.resources.jsonconfig.JsonConfigResourcesManager;
import ts.eclipse.ide.internal.core.resources.jsonconfig.JsonConfigScope;

/**
 * Builder to transpiles TypeScript files into JavaScript files and source map
 * if needed.
 *
 */
public class TypeScriptBuilder extends IncrementalProjectBuilder {

	public static final String ID = "ts.eclipse.ide.core.typeScriptBuilder";

//	private static final ITypeScriptDiagnosticsCollector DIAGNOSTICS_COLLECTOR = new ITypeScriptDiagnosticsCollector() {
//
//		@Override
//		public void addDiagnostic(String event, String filename, String text, int startLine, int startOffset,
//				int endLine, int endOffset, String category, int code) {
//			try {
//				IFile f = WorkbenchResourceUtil.findFileFromWorkspace(filename);
//				if (f != null && f.exists()) {
//					TypeScriptResourceUtil.addTscMarker(f, text, IMarker.SEVERITY_ERROR, startLine);
//				}
//			} catch (CoreException e) {
//				TypeScriptCorePlugin.logError(e);
//			}
//		}
//	};

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

	private void incrementalBuild(IIDETypeScriptProject tsProject, Collection<IResourceDelta> deltas,
			IProgressMonitor monitor)
			throws CoreException {

		final List<IFile> invalidatedTsconfigFiles = new ArrayList<>();
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
				case IResource.FOLDER:
					return true;
				case IResource.FILE:
					int kind = delta.getKind();
					switch (kind) {
					case IResourceDelta.ADDED:
					case IResourceDelta.CHANGED:
						if (TypeScriptResourceUtil.isTsConfigFile(resource)) {
							JsonConfigResourcesManager.getInstance().addOrUpdate((IFile) resource);
							invalidatedTsconfigFiles.add((IFile) resource);
						}
						break;
					case IResourceDelta.REMOVED:
						if (TypeScriptResourceUtil.isTsConfigFile(resource)) {
							JsonConfigResourcesManager.getInstance().remove((IFile) resource);
							invalidatedTsconfigFiles.add((IFile) resource);
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

		// If a tsconfig.json in this project changed, back off and do a full
		// build instead
		for (IFile tsconfigFile : invalidatedTsconfigFiles) {
			if (tsconfigFile.getProject().equals(getProject())) {
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
						if (TypeScriptResourceUtil.isTsOrTsxFile(resource)) {
							addTsFile(buildPath, tsFilesToCompile, resource);
						}
						break;
					case IResourceDelta.REMOVED:
						if (TypeScriptResourceUtil.isTsOrTsxFile(resource)) {
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
					//compileWithTsserver(tsProject, tsFiles, tsconfig);
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
	public void compileWithTsc(IIDETypeScriptProject tsProject, List<IFile> tsFiles, IDETsconfigJson tsconfig)
			throws TypeScriptException, CoreException {
		IIDETypeScriptCompileResult result = tsProject.getCompiler().compile(tsconfig, tsFiles);
		this.lastCompileResults.add(result);
	}

	/**
	 * Compile the given ts files with tsserver by consumming
	 * "compileOnSaveEmitFile" tsserver command.
	 * 
	 * @param tsProject
	 * @param tsFiles
	 * @param tsconfig
	 * @throws CoreException
	 */
	private void compileWithTsserver(IIDETypeScriptProject tsProject, List<IFile> tsFiles, IDETsconfigJson tsconfig)
			throws CoreException {		
		for (final IFile file : tsFiles) {
			try {
				IIDETypeScriptFile tsFile = tsProject.getOpenedFile(file);
				// delete marker for the given ts file
				TypeScriptResourceUtil.deleteTscMarker(file);
				// compile the current ts file with "compileOnSaveEmitFile"
				tsFile.compileOnSaveEmitFile(null);
				// Refresh of js file, map file cannot work.
				// See
				TypeScriptResourceUtil.refreshAndCollectEmittedFiles(file, tsconfig, true, null);
			} catch (TypeScriptException e) {
				Trace.trace(Trace.SEVERE, "Error while tsserver compilation", e);
			}
		}
		
		try {
			tsProject.geterrForProject(WorkbenchResourceUtil.getFileName(tsFiles.get(0)), 0).thenAccept(events -> {
				System.err.println(events);
			});
		} catch (TypeScriptException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		try {
			tsProject.geterrForProject(WorkbenchResourceUtil.getFileName(tsFiles.get(0)), 0);
		} catch (TypeScriptException e) {
			Trace.trace(Trace.SEVERE, "Error while tsserver compilation", e);
		}
//		try {
//			tsProject.getClient().projectInfo("", WorkbenchResourceUtil.getFileName(tsconfig.getTsconfigFile()), true);
//		} catch (TypeScriptException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
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
