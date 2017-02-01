package ts.eclipse.ide.internal.core.resources.jsonconfig;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;

import ts.cmd.tsc.CompilerOptions;
import ts.eclipse.ide.core.resources.jsconfig.IDETsconfigJson;
import ts.eclipse.ide.internal.core.resources.jsonconfig.GlobPattern.ImplicitExtensions;
import ts.utils.FileUtils;
import ts.utils.StringUtils;

/**
 * Represents the scope of files that are somewhat meaningful for a
 * tsconfig.json file.
 * 
 * <i>Included</i> files are the ones consumed by the TypeScript compilation.
 * <i>Emitted</i> files are the product of TypeScript compilation.
 * 
 * Note that both sets of files are usually open, including some root containers
 * into which files are found recursively.
 */
public final class JsonConfigScope {

	static JsonConfigScope createFromTsconfig(IDETsconfigJson tsconfig) {
		Builder builder = new Builder(tsconfig);

		CompilerOptions compilerOptions = tsconfig.getCompilerOptions();
		if (compilerOptions != null && !StringUtils.isEmpty(compilerOptions.getOutFile())) {
			builder.addOutFileEmitted(compilerOptions.getOutFile());
		} else {
			String outDirPathString;
			if (compilerOptions != null && !StringUtils.isEmpty(compilerOptions.getOutDir())) {
				outDirPathString = compilerOptions.getOutDir();
			} else {
				outDirPathString = ".";
			}
			builder.addOutDirEmitted(outDirPathString);
		}
		builder.addSourceIncludedAndExcluded();
		builder.addMappedPathsIncluded();
		builder.addTypesIncluded();

		return builder.build();
	}

	private static class Builder {

		private final IDETsconfigJson tsconfig;
		private final List<ResSet> alwaysIncluded = new ArrayList<>();
		private final List<ResSet> included = new ArrayList<>();
		private final List<ResSet> excluded = new ArrayList<>();
		private final List<ResSet> emitted = new ArrayList<>();
		private IPath sourceBasePath;
		private String singleOutputFileName; // null if multiple output
		private IPath jsOutputBasePath;
		private IPath mapOutputBasePath;
		private IPath declarationOutputBasePath;

		Builder(IDETsconfigJson tsconfig) {
			this.tsconfig = tsconfig;
		}

		void addOutFileEmitted(String outFileStringPath) {
			CompilerOptions compilerOptions = tsconfig.getCompilerOptions();

			IPath outFilePath = getPath(outFileStringPath);
			this.emitted.add(createSingleFileResSet(outFilePath));
			this.jsOutputBasePath = outFilePath.removeLastSegments(1);
			this.singleOutputFileName = outFilePath.lastSegment();

			if (compilerOptions != null && compilerOptions.isSourceMap()) {
				IPath outMapFilePath = outFilePath.addFileExtension(FileUtils.MAP_EXTENSION);
				this.emitted.add(createSingleFileResSet(outMapFilePath));
				this.mapOutputBasePath = outMapFilePath.removeLastSegments(1);
			} else {
				this.mapOutputBasePath = null;
			}

			if (compilerOptions != null && compilerOptions.isDeclaration()) {
				IPath outDeclarationFilePath = outFilePath;
				if (FileUtils.JS_EXTENSION.equals(outFilePath.getFileExtension())) {
					outDeclarationFilePath = outDeclarationFilePath.removeFileExtension();
				}
				outDeclarationFilePath.addFileExtension(FileUtils.D_TS_EXTENSION);
				this.emitted.add(createSingleFileResSet(outDeclarationFilePath));
				this.declarationOutputBasePath = outDeclarationFilePath.removeLastSegments(1);
			} else {
				this.declarationOutputBasePath = null;
			}
		}

		void addOutDirEmitted(String outDirStringPath) {
			CompilerOptions compilerOptions = tsconfig.getCompilerOptions();

			IPath outDirPath = getPath(outDirStringPath);
			this.emitted.add(createTypedFileResSet(outDirPath, FileUtils.JS_EXTENSION));
			this.jsOutputBasePath = outDirPath;
			this.singleOutputFileName = null;

			if (compilerOptions != null && compilerOptions.isSourceMap()) {
				this.emitted.add(createTypedFileResSet(outDirPath, FileUtils.JS_MAP_EXTENSION));
				this.mapOutputBasePath = outDirPath;
			} else {
				this.mapOutputBasePath = null;
			}

			if (compilerOptions != null && compilerOptions.isDeclaration()) {
				if (!StringUtils.isEmpty(compilerOptions.getDeclarationDir())) {
					IPath declarationDirPath = getPath(compilerOptions.getDeclarationDir());
					this.emitted.add(createTypedFileResSet(declarationDirPath, FileUtils.D_TS_EXTENSION));
					this.declarationOutputBasePath = declarationDirPath;
				} else {
					this.emitted.add(createTypedFileResSet(outDirPath, FileUtils.D_TS_EXTENSION));
					this.declarationOutputBasePath = outDirPath;
				}
			} else {
				this.declarationOutputBasePath = null;
			}
		}

		void addSourceIncludedAndExcluded() {
			ImplicitExtensions implicitExts = getImplicitExtensions();

			List<String> files = tsconfig.getFiles();
			List<String> include = tsconfig.getInclude();
			if (files != null || include != null) {
				if (files != null) {
					for (String filePathString : files) {
						IPath filePath = getPath(filePathString);
						this.alwaysIncluded.add(createSingleFileResSet(filePath));
					}
				}
				if (include != null) {
					for (String includeGlob : include) {
						this.included.add(createResSet(getBasePath(), includeGlob, implicitExts));
					}
				}
			} else {
				this.included.add(createResSet(getBasePath(), implicitExts));
			}

			List<String> exclude = tsconfig.getDefaultOrDefinedExclude();
			if (exclude != null) {
				for (String excludeGlob : exclude) {
					this.excluded.add(createResSet(getBasePath(), excludeGlob, null));
				}
			}
		}

		void addMappedPathsIncluded() {
			CompilerOptions compilerOptions = tsconfig.getCompilerOptions();
			ImplicitExtensions implicitExts = getImplicitExtensions();

			if (compilerOptions != null) {
				for (String pathKey : compilerOptions.getPathsKeys()) {
					for (String pathGlob : compilerOptions.getPathsKeyValues(pathKey)) {
						this.included.add(createFileResSet(getBasePath(), pathGlob, implicitExts));
					}
				}
			}
		}

		void addTypesIncluded() {
			CompilerOptions compilerOptions = tsconfig.getCompilerOptions();

			List<IPath> rootPaths = new ArrayList<>();
			if (compilerOptions != null && compilerOptions.getTypeRoots() != null) {
				for (String typeRootString : compilerOptions.getTypeRoots()) {
					rootPaths.add(getPath(typeRootString));
				}
			} else {
				IPath currentPath = getBasePath();
				while (true) {
					rootPaths.add(currentPath.append("node_modules/@types"));
					if (currentPath.segmentCount() <= 0) {
						break;
					}
					currentPath = currentPath.append("..");
				}
			}

			if (compilerOptions != null && compilerOptions.getTypes() != null) {
				for (String typeName : compilerOptions.getTypes()) {
					for (IPath rootPath : rootPaths) {
						IPath typePath = rootPath.append(typeName);
						this.included.add(createTypedFileResSet(typePath, FileUtils.D_TS_EXTENSION));
					}
				}
			} else {
				for (IPath rootPath : rootPaths) {
					this.included.add(createTypedFileResSet(rootPath, FileUtils.D_TS_EXTENSION));
				}
			}
		}

		private ImplicitExtensions getImplicitExtensions() {
			CompilerOptions compilerOptions = tsconfig.getCompilerOptions();
			boolean allowJs = (compilerOptions != null && Boolean.TRUE.equals(compilerOptions.isAllowJs()));
			return allowJs ? ImplicitExtensions.TS_AND_JS : ImplicitExtensions.TS_ONLY;
		}

		JsonConfigScope build() {
			this.sourceBasePath = computeSourceBasePath();
			return new JsonConfigScope(this);
		}

		private IPath computeSourceBasePath() {
			CompilerOptions compilerOptions = tsconfig.getCompilerOptions();

			// use specified root dir
			if (compilerOptions != null && !StringUtils.isEmpty(compilerOptions.getRootDir())) {
				return getPath(compilerOptions.getRootDir());
			}

			// compute common dir of included files
			IPath commonRootPath = null;
			for (ResSet resSet : included) {
				IPath path = resSet.basePath;
				if (commonRootPath == null) {
					commonRootPath = path;
				} else {
					IPath shorterPath, otherPath;
					if (path.segmentCount() <= commonRootPath.segmentCount()) {
						shorterPath = path;
						otherPath = commonRootPath;
					} else {
						shorterPath = commonRootPath;
						otherPath = path;
					}
					while (!shorterPath.isPrefixOf(otherPath)) {
						shorterPath = shorterPath.removeLastSegments(1);
					}
					commonRootPath = shorterPath;
				}
			}
			if (commonRootPath != null) {
				return commonRootPath;
			}

			return getBasePath();
		}

		private IPath getBasePath() {
			return tsconfig.getTsconfigFile().getParent().getFullPath();
		}

		private IPath getPath(String pathString) {
			return getBasePath().append(pathString);
		}

		private ResSet createSingleFileResSet(IPath filePath) {
			return new ResSet(filePath, null, false);
		}

		private ResSet createTypedFileResSet(IPath basePath, String extension) {
			return new ResSet(basePath, GlobPattern.parse("**/*." + extension), false);
		}

		private ResSet createFileResSet(IPath basePath, String descendantGlobString, ImplicitExtensions implicitExts) {
			return new ResSet(basePath, GlobPattern.parse(descendantGlobString, implicitExts), false);
		}

		private ResSet createResSet(IPath basePath, ImplicitExtensions implicitExts) {
			return new ResSet(basePath, GlobPattern.parse("**/*", implicitExts), true);
		}

		private ResSet createResSet(IPath basePath, String descendantGlobString, ImplicitExtensions implicitExts) {
			return new ResSet(basePath, GlobPattern.parse(descendantGlobString, implicitExts), true);
		}

	}

	private final IFile configFile;
	private final List<ResSet> alwaysIncluded;
	private final List<ResSet> included;
	private final List<ResSet> excluded;
	private final List<ResSet> emitted;
	private final IPath sourceBasePath;
	private String singleOutputFileName; // null if multiple output
	private final IPath jsOutputBasePath;
	private final IPath mapOutputBasePath;
	private final IPath declarationOutputBasePath;

	private Set<IPath> entrancePaths; // lazy

	private JsonConfigScope(Builder builder) {
		this.configFile = builder.tsconfig.getTsconfigFile();
		this.alwaysIncluded = Collections.unmodifiableList(builder.alwaysIncluded);
		this.included = Collections.unmodifiableList(builder.included);
		this.excluded = Collections.unmodifiableList(builder.excluded);
		this.emitted = Collections.unmodifiableList(builder.emitted);
		this.sourceBasePath = builder.sourceBasePath;
		this.singleOutputFileName = builder.singleOutputFileName;
		this.jsOutputBasePath = builder.jsOutputBasePath;
		this.mapOutputBasePath = builder.mapOutputBasePath;
		this.declarationOutputBasePath = builder.declarationOutputBasePath;
	}

	private static final class ResSet {

		private final IPath basePath;
		private final GlobPattern descendantGlob; // null if just the base path
		private final boolean matchContainers; // null if just the base path

		ResSet(IPath basePath, GlobPattern descendantGlob, boolean matchContainers) {
			if (descendantGlob != null) {
				IPath leadingFixedPath = descendantGlob.getLeadingFixedPath();
				if (leadingFixedPath != null) {
					basePath = basePath.append(leadingFixedPath);
					descendantGlob = descendantGlob.withoutLeadingFixedPath();
				}
			}

			this.basePath = basePath;
			this.descendantGlob = descendantGlob;
			this.matchContainers = matchContainers;
		}

		boolean contains(IPath path, boolean isContainer) {
			if (isContainer && !matchContainers) {
				return false;
			}
			if (descendantGlob != null) {
				if (!basePath.isPrefixOf(path)) {
					return false;
				}
				IPath descendantPath = path.makeRelativeTo(basePath);
				Pattern descendantPattern = isContainer ? descendantGlob.getContainersPattern()
						: descendantGlob.getFilesPattern();
				return descendantPattern.matcher(descendantPath.toString()).matches();
			}
			return basePath.equals(path);
		}

		@Override
		public String toString() {
			String s = basePath.toString();
			if (descendantGlob != null) {
				s += " : " + descendantGlob;
			}
			if (matchContainers) {
				s += " (+contents)";
			}
			return s;
		}

	}

	/**
	 * Gets the JSON configuration file that defines this scope.
	 * 
	 * @return
	 */
	public IFile getConfigFile() {
		return configFile;
	}

	/**
	 * Determine whether a resource is <i>included</i> in this scope, that is,
	 * it is used as source for compilation.
	 * 
	 * @param resource
	 * @return
	 */
	public boolean includes(IResource resource) {
		boolean isContainer = resource instanceof IContainer;
		IPath path = resource.getFullPath();
		while (path.segmentCount() > 0) {
			for (ResSet resSet : alwaysIncluded) {
				if (resSet.contains(path, isContainer)) {
					return true;
				}
			}
			for (ResSet resSet : excluded) {
				if (resSet.contains(path, isContainer)) {
					return false;
				}
			}
			for (ResSet resSet : included) {
				if (resSet.contains(path, isContainer)) {
					return true;
				}
			}
			path = path.removeLastSegments(1);
			isContainer = true;
		}
		return false;
	}

	/**
	 * Determine whether a resource <i>might deeply contain</i> resources that
	 * are directly included in this scope, as per {@link #includes}.
	 * 
	 * @param resource
	 * @return
	 */
	public boolean mightIncludeWithin(IResource resource) {
		synchronized (this) {
			if (entrancePaths == null) {
				entrancePaths = new HashSet<>();
				Stream.of(included, excluded, alwaysIncluded).flatMap(List::stream).forEach(resSet -> {
					IPath path = resSet.basePath;
					while (path.segmentCount() > 0) {
						entrancePaths.add(path);
						path = path.removeLastSegments(1);
					}
				});
			}
		}
		return entrancePaths.contains(resource.getFullPath());
	}

	/**
	 * Determine whether a resource is <i>emitted</i> by this scope, that is, it
	 * is a product of compilation.
	 * 
	 * @param resource
	 * @return
	 */
	public boolean emits(IResource resource) {
		boolean isContainer = resource instanceof IContainer;
		IPath path = resource.getFullPath();
		while (path.segmentCount() > 0) {
			for (ResSet resSet : emitted) {
				if (resSet.contains(path, isContainer)) {
					return true;
				}
			}
			path = path.removeLastSegments(1);
			isContainer = true;
		}
		return false;
	}

	/**
	 * Gets the "single" (concatenated) files that are emitted by this scope.
	 * 
	 * @return collection of files; empty if this scope emits multiple files.
	 */
	public Collection<IFile> getSingleEmittedFiles() {
		if (singleOutputFileName == null) {
			return Collections.emptyList();
		}
		IWorkspaceRoot wsRoot = ResourcesPlugin.getWorkspace().getRoot();
		List<IFile> result = new ArrayList<>(3);
		result.add(wsRoot.getFile(jsOutputBasePath.append(singleOutputFileName)));
		if (mapOutputBasePath != null) {
			IPath outMapFilePath = mapOutputBasePath.append(singleOutputFileName)
					.addFileExtension(FileUtils.MAP_EXTENSION);
			result.add(wsRoot.getFile(outMapFilePath));
		}
		if (declarationOutputBasePath != null) {
			IPath outDeclarationFilePath = declarationOutputBasePath.append(singleOutputFileName);
			if (FileUtils.JS_EXTENSION.equals(outDeclarationFilePath.getFileExtension())) {
				outDeclarationFilePath = outDeclarationFilePath.removeFileExtension();
			}
			outDeclarationFilePath.addFileExtension(FileUtils.D_TS_EXTENSION);
			result.add(wsRoot.getFile(outDeclarationFilePath));
		}
		return result;
	}

	/**
	 * Gets all files emitted by this scope <i>specifically</> for an included
	 * file.
	 * 
	 * @param includedFile
	 *            included file to test.
	 * @return collection of files; empty if the scope emits a single file.
	 */
	public Collection<IFile> getSpecificEmittedFiles(IFile includedFile) {
		IPath path = includedFile.getFullPath().removeFileExtension();
		if (!sourceBasePath.isPrefixOf(path) || singleOutputFileName != null) {
			return Collections.emptyList();
		}
		IPath relativePath = path.makeRelativeTo(sourceBasePath);
		IWorkspaceRoot wsRoot = ResourcesPlugin.getWorkspace().getRoot();
		List<IFile> result = new ArrayList<>(3);
		result.add(wsRoot.getFile(jsOutputBasePath.append(relativePath).addFileExtension(FileUtils.JS_EXTENSION)));
		if (mapOutputBasePath != null) {
			result.add(wsRoot
					.getFile(mapOutputBasePath.append(relativePath).addFileExtension(FileUtils.JS_MAP_EXTENSION)));
		}
		if (declarationOutputBasePath != null) {
			result.add(wsRoot.getFile(
					declarationOutputBasePath.append(relativePath).addFileExtension(FileUtils.D_TS_EXTENSION)));
		}
		return result;
	}

	@Override
	public String toString() {
		StringBuilder s = new StringBuilder(configFile.toString());
		appendResSetsToString(s, alwaysIncluded, "Always included");
		appendResSetsToString(s, included, "Included");
		appendResSetsToString(s, excluded, "Excluded");
		appendResSetsToString(s, emitted, "Emitted");
		return s.toString();
	}

	private static void appendResSetsToString(StringBuilder s, Collection<ResSet> resSets, String label) {
		if (!resSets.isEmpty()) {
			if (s.length() > 0) {
				s.append(System.lineSeparator());
			}
			s.append(label + ":");
			resSets.forEach(resSet -> s.append(System.lineSeparator() + "\t" + resSet));
		}
	}

}
