package ts.eclipse.ide.internal.core.resources.jsonconfig;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.eclipse.core.filesystem.URIUtil;
import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;

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
		private IPath sourceBaseLoc;
		private String singleOutputFileName; // null if multiple output
		private IPath jsOutputBaseLoc;
		private IPath mapOutputBaseLoc;
		private IPath declarationOutputBaseLoc;

		Builder(IDETsconfigJson tsconfig) {
			this.tsconfig = tsconfig;
		}

		void addOutFileEmitted(String outFileStringPath) {
			CompilerOptions compilerOptions = tsconfig.getCompilerOptions();

			IPath outFileLoc = getLocation(outFileStringPath);
			this.emitted.add(createSingleFileResSet(outFileLoc));
			this.jsOutputBaseLoc = outFileLoc.removeLastSegments(1);
			this.singleOutputFileName = outFileLoc.lastSegment();

			if (compilerOptions != null && compilerOptions.isSourceMap()) {
				IPath outMapFileLoc = outFileLoc.addFileExtension(FileUtils.MAP_EXTENSION);
				this.emitted.add(createSingleFileResSet(outMapFileLoc));
				this.mapOutputBaseLoc = outMapFileLoc.removeLastSegments(1);
			} else {
				this.mapOutputBaseLoc = null;
			}

			if (compilerOptions != null && compilerOptions.isDeclaration()) {
				IPath outDeclarationFileLoc = outFileLoc;
				if (FileUtils.JS_EXTENSION.equals(outFileLoc.getFileExtension())) {
					outDeclarationFileLoc = outDeclarationFileLoc.removeFileExtension();
				}
				outDeclarationFileLoc.addFileExtension(FileUtils.D_TS_EXTENSION);
				this.emitted.add(createSingleFileResSet(outDeclarationFileLoc));
				this.declarationOutputBaseLoc = outDeclarationFileLoc.removeLastSegments(1);
			} else {
				this.declarationOutputBaseLoc = null;
			}
		}

		void addOutDirEmitted(String outDirStringPath) {
			CompilerOptions compilerOptions = tsconfig.getCompilerOptions();

			IPath outDirLoc = getLocation(outDirStringPath);
			this.emitted.add(createTypedFileResSet(outDirLoc, FileUtils.JS_EXTENSION));
			this.jsOutputBaseLoc = outDirLoc;
			this.singleOutputFileName = null;

			if (compilerOptions != null && compilerOptions.isSourceMap()) {
				this.emitted.add(createTypedFileResSet(outDirLoc, FileUtils.JS_MAP_EXTENSION));
				this.mapOutputBaseLoc = outDirLoc;
			} else {
				this.mapOutputBaseLoc = null;
			}

			if (compilerOptions != null && compilerOptions.isDeclaration()) {
				if (!StringUtils.isEmpty(compilerOptions.getDeclarationDir())) {
					IPath declarationDirLoc = getLocation(compilerOptions.getDeclarationDir());
					this.emitted.add(createTypedFileResSet(declarationDirLoc, FileUtils.D_TS_EXTENSION));
					this.declarationOutputBaseLoc = declarationDirLoc;
				} else {
					this.emitted.add(createTypedFileResSet(outDirLoc, FileUtils.D_TS_EXTENSION));
					this.declarationOutputBaseLoc = outDirLoc;
				}
			} else {
				this.declarationOutputBaseLoc = null;
			}
		}

		void addSourceIncludedAndExcluded() {
			ImplicitExtensions implicitExts = getImplicitExtensions();

			List<String> files = tsconfig.getFiles();
			List<String> include = tsconfig.getInclude();
			if (files != null || include != null) {
				if (files != null) {
					for (String filePathString : files) {
						IPath fileLoc = getLocation(filePathString);
						this.alwaysIncluded.add(createSingleFileResSet(fileLoc));
					}
				}
				if (include != null) {
					for (String includeGlob : include) {
						this.included.add(createResSet(getBaseLocation(), includeGlob, implicitExts));
					}
				}
			} else {
				this.included.add(createResSet(getBaseLocation(), implicitExts));
			}

			List<String> exclude = tsconfig.getDefaultOrDefinedExclude();
			if (exclude != null) {
				for (String excludeGlob : exclude) {
					this.excluded.add(createResSet(getBaseLocation(), excludeGlob, null));
				}
			}
		}

		void addMappedPathsIncluded() {
			CompilerOptions compilerOptions = tsconfig.getCompilerOptions();
			ImplicitExtensions implicitExts = getImplicitExtensions();

			if (compilerOptions != null) {
				for (String pathKey : compilerOptions.getPathsKeys()) {
					for (String pathGlob : compilerOptions.getPathsKeyValues(pathKey)) {
						this.included.add(createFileResSet(getBaseLocation(), pathGlob, implicitExts));
					}
				}
			}
		}

		void addTypesIncluded() {
			CompilerOptions compilerOptions = tsconfig.getCompilerOptions();

			List<IPath> rootLocs = new ArrayList<>();
			if (compilerOptions != null && compilerOptions.getTypeRoots() != null) {
				for (String typeRootString : compilerOptions.getTypeRoots()) {
					rootLocs.add(getLocation(typeRootString));
				}
			} else {
				IPath currentLoc = getBaseLocation();
				while (true) {
					rootLocs.add(currentLoc.append("node_modules/@types"));
					if (currentLoc.segmentCount() <= 0) {
						break;
					}
					currentLoc = currentLoc.append("..");
				}
			}

			if (compilerOptions != null && compilerOptions.getTypes() != null) {
				for (String typeName : compilerOptions.getTypes()) {
					for (IPath rootLoc : rootLocs) {
						IPath typeLoc = rootLoc.append(typeName);
						this.included.add(createTypedFileResSet(typeLoc, FileUtils.D_TS_EXTENSION, true));
					}
				}
			} else {
				for (IPath rootLoc : rootLocs) {
					this.included.add(createTypedFileResSet(rootLoc, FileUtils.D_TS_EXTENSION, true));
				}
			}
		}

		private ImplicitExtensions getImplicitExtensions() {
			CompilerOptions compilerOptions = tsconfig.getCompilerOptions();
			boolean allowJs = (compilerOptions != null && Boolean.TRUE.equals(compilerOptions.isAllowJs()));
			return allowJs ? ImplicitExtensions.TS_AND_JS : ImplicitExtensions.TS_ONLY;
		}

		JsonConfigScope build() {
			this.sourceBaseLoc = computeSourceBaseLocation();
			return new JsonConfigScope(this);
		}

		private IPath computeSourceBaseLocation() {
			CompilerOptions compilerOptions = tsconfig.getCompilerOptions();

			// use specified root dir
			if (compilerOptions != null && !StringUtils.isEmpty(compilerOptions.getRootDir())) {
				return getLocation(compilerOptions.getRootDir());
			}

			// compute common dir of included files
			IPath commonRootLoc = null;
			for (ResSet resSet : included) {
				if (!resSet.isSource()) {
					continue;
				}
				IPath path = resSet.baseLoc;
				if (commonRootLoc == null) {
					commonRootLoc = path;
				} else {
					IPath shorterPath, otherPath;
					if (path.segmentCount() <= commonRootLoc.segmentCount()) {
						shorterPath = path;
						otherPath = commonRootLoc;
					} else {
						shorterPath = commonRootLoc;
						otherPath = path;
					}
					while (!shorterPath.isPrefixOf(otherPath)) {
						shorterPath = shorterPath.removeLastSegments(1);
					}
					commonRootLoc = shorterPath;
				}
			}
			if (commonRootLoc != null) {
				return commonRootLoc;
			}

			return getBaseLocation();
		}

		private IPath getBaseLocation() {
			return tsconfig.getTsconfigFile().getParent().getLocation();
		}

		private IPath getLocation(String pathString) {
			IPath path = new Path(pathString);
			if (path.isAbsolute()) {
				return path;
			}
			return getBaseLocation().append(path);
		}

		private ResSet createSingleFileResSet(IPath fileLoc) {
			return new ResSet(fileLoc, null, true, false);
		}

		private ResSet createTypedFileResSet(IPath baseLoc, String extension) {
			return new ResSet(baseLoc, GlobPattern.parse("**/*." + extension), true, false);
		}

		private ResSet createTypedFileResSet(IPath baseLoc, String extension, boolean noSource) {
			return new ResSet(baseLoc, GlobPattern.parse("**/*." + extension), !noSource, false);
		}

		private ResSet createFileResSet(IPath baseLoc, String descendantGlobString, ImplicitExtensions implicitExts) {
			return new ResSet(baseLoc, GlobPattern.parse(descendantGlobString, implicitExts), true, false);
		}

		private ResSet createResSet(IPath baseLoc, ImplicitExtensions implicitExts) {
			return new ResSet(baseLoc, GlobPattern.parse("**/*", implicitExts), true, true);
		}

		private ResSet createResSet(IPath baseLoc, String descendantGlobString, ImplicitExtensions implicitExts) {
			return new ResSet(baseLoc, GlobPattern.parse(descendantGlobString, implicitExts), true, true);
		}

	}

	private final IFile configFile;
	private final List<ResSet> alwaysIncluded;
	private final List<ResSet> included;
	private final List<ResSet> excluded;
	private final List<ResSet> emitted;
	private final IPath sourceBaseLoc;
	private String singleOutputFileName; // null if multiple output
	private final IPath jsOutputBaseLoc;
	private final IPath mapOutputBaseLoc;
	private final IPath declarationOutputBaseLoc;

	private Set<IPath> entrancePaths; // lazy

	private JsonConfigScope(Builder builder) {
		this.configFile = builder.tsconfig.getTsconfigFile();
		this.alwaysIncluded = Collections.unmodifiableList(builder.alwaysIncluded);
		this.included = Collections.unmodifiableList(builder.included);
		this.excluded = Collections.unmodifiableList(builder.excluded);
		this.emitted = Collections.unmodifiableList(builder.emitted);
		this.sourceBaseLoc = builder.sourceBaseLoc;
		this.singleOutputFileName = builder.singleOutputFileName;
		this.jsOutputBaseLoc = builder.jsOutputBaseLoc;
		this.mapOutputBaseLoc = builder.mapOutputBaseLoc;
		this.declarationOutputBaseLoc = builder.declarationOutputBaseLoc;
	}

	private static final class ResSet {

		private final IPath baseLoc; // file system location
		private final GlobPattern descendantGlob; // null if just the base path
		private final boolean source;
		private final boolean matchContainers;

		ResSet(IPath baseLoc, GlobPattern descendantGlob, boolean source, boolean matchContainers) {
			if (descendantGlob != null) {
				IPath leadingFixedPath = descendantGlob.getLeadingFixedPath();
				if (leadingFixedPath != null) {
					baseLoc = baseLoc.append(leadingFixedPath);
					descendantGlob = descendantGlob.withoutLeadingFixedPath();
				}
			}

			this.baseLoc = baseLoc;
			this.descendantGlob = descendantGlob;
			this.source = source;
			this.matchContainers = matchContainers;
		}

		boolean isSource() {
			return source;
		}

		boolean contains(IPath location, boolean isContainer) {
			if (isContainer && !matchContainers) {
				return false;
			}
			if (descendantGlob != null) {
				if (!baseLoc.isPrefixOf(location)) {
					return false;
				}
				IPath descendantPath = location.makeRelativeTo(baseLoc);
				Pattern descendantPattern = isContainer ? descendantGlob.getContainersPattern()
						: descendantGlob.getFilesPattern();
				return descendantPattern.matcher(descendantPath.toString()).matches();
			}
			return baseLoc.equals(location);
		}

		@Override
		public String toString() {
			String s = baseLoc.toString();
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
		IPath location = resource.getLocation();
		while (location.segmentCount() > 0) {
			for (ResSet resSet : alwaysIncluded) {
				if (resSet.contains(location, isContainer)) {
					return true;
				}
			}
			for (ResSet resSet : excluded) {
				if (resSet.contains(location, isContainer)) {
					return false;
				}
			}
			for (ResSet resSet : included) {
				if (resSet.contains(location, isContainer)) {
					return true;
				}
			}
			location = location.removeLastSegments(1);
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
					IPath path = resSet.baseLoc;
					while (path.segmentCount() > 0) {
						entrancePaths.add(path);
						path = path.removeLastSegments(1);
					}
				});
			}
		}
		return entrancePaths.contains(resource.getLocation());
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
		IPath location = resource.getLocation();
		while (location.segmentCount() > 0) {
			for (ResSet resSet : emitted) {
				if (resSet.contains(location, isContainer)) {
					return true;
				}
			}
			location = location.removeLastSegments(1);
			isContainer = true;
		}
		return false;
	}

	/**
	 * Gets the "single" (concatenated) workspace files that are emitted by this
	 * scope. Additional files emitted outside of the workspace are not
	 * returned.
	 * 
	 * @return collection of files; empty if this scope emits multiple files.
	 */
	public Collection<IFile> getSingleEmittedFiles() {
		if (singleOutputFileName == null) {
			return Collections.emptyList();
		}
		List<IFile> result = new ArrayList<>(3);
		addWorkspaceFiles(result, jsOutputBaseLoc.append(singleOutputFileName));
		if (mapOutputBaseLoc != null) {
			IPath outMapFileLoc = mapOutputBaseLoc.append(singleOutputFileName)
					.addFileExtension(FileUtils.MAP_EXTENSION);
			addWorkspaceFiles(result, outMapFileLoc);
		}
		if (declarationOutputBaseLoc != null) {
			IPath outDeclarationFileLoc = declarationOutputBaseLoc.append(singleOutputFileName);
			if (FileUtils.JS_EXTENSION.equals(outDeclarationFileLoc.getFileExtension())) {
				outDeclarationFileLoc = outDeclarationFileLoc.removeFileExtension();
			}
			outDeclarationFileLoc.addFileExtension(FileUtils.D_TS_EXTENSION);
			addWorkspaceFiles(result, outDeclarationFileLoc);
		}
		return result;
	}

	/**
	 * Gets all workspace files emitted by this scope <i>specifically</> for an
	 * included file. Additional files emitted outside of the workspace are not
	 * returned.
	 * 
	 * @param includedFile
	 *            included file to test.
	 * @return collection of files; empty if the scope emits a single file.
	 */
	public Collection<IFile> getSpecificEmittedFiles(IFile includedFile) {
		IPath loc = includedFile.getLocation().removeFileExtension();
		if (!sourceBaseLoc.isPrefixOf(loc) || singleOutputFileName != null) {
			return Collections.emptyList();
		}
		IPath relativeLoc = loc.makeRelativeTo(sourceBaseLoc);
		List<IFile> result = new ArrayList<>(3);
		addWorkspaceFiles(result, jsOutputBaseLoc.append(relativeLoc).addFileExtension(FileUtils.JS_EXTENSION));
		if (mapOutputBaseLoc != null) {
			addWorkspaceFiles(result,
					mapOutputBaseLoc.append(relativeLoc).addFileExtension(FileUtils.JS_MAP_EXTENSION));
		}
		if (declarationOutputBaseLoc != null) {
			addWorkspaceFiles(result,
					declarationOutputBaseLoc.append(relativeLoc).addFileExtension(FileUtils.D_TS_EXTENSION));
		}
		return result;
	}

	private void addWorkspaceFiles(List<IFile> list, IPath location) {
		IWorkspaceRoot wsRoot = ResourcesPlugin.getWorkspace().getRoot();
		for (IFile file : wsRoot.findFilesForLocationURI(URIUtil.toURI(location.makeAbsolute()))) {
			list.add(file);
		}
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
