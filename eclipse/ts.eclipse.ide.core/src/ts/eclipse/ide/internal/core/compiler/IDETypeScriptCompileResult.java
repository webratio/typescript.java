package ts.eclipse.ide.internal.core.compiler;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;

import ts.eclipse.ide.core.compiler.IIDETypeScriptCompileResult;

/**
 * Internal implementation of {@link IIDETypeScriptCompileResult}.
 *
 */
public class IDETypeScriptCompileResult implements IIDETypeScriptCompileResult {

	/**
	 * Constructs a result representing an attempted build that actually did
	 * nothing because the set of sources to compile was empty. This includes
	 * cases where a partial compilation is invoked and there are no changed
	 * files.
	 * 
	 * @param full
	 * @param accessedFiles
	 * @return
	 */
	static final IDETypeScriptCompileResult forEmptyBuild(boolean full) {
		return new IDETypeScriptCompileResult(full, false, Collections.emptyList());
	}

	/**
	 * Constructs a result representing a build that was aborted before
	 * completing because of some blocking build configuration error.
	 * 
	 * @param full
	 * @return
	 */
	static final IDETypeScriptCompileResult forAbortedBuild(boolean full) {
		return new IDETypeScriptCompileResult(full, false, Collections.emptyList());
	}

	/**
	 * Constructs a result representing a completed build that accessed a number
	 * of files.
	 * 
	 * @param full
	 * @param accessedFiles
	 * @return
	 */
	static final IDETypeScriptCompileResult forCompletedBuild(boolean full, Collection<IFile> accessedFiles) {
		return new IDETypeScriptCompileResult(full, true, accessedFiles);
	}

	private final boolean fullBuild;
	private final boolean complete;
	private final Set<IProject> accessedProjects;

	private IDETypeScriptCompileResult(boolean fullBuild, boolean complete, Collection<IFile> accessedFiles) {
		this.fullBuild = fullBuild;
		this.complete = complete;
		this.accessedProjects = Collections
				.unmodifiableSet(accessedFiles.stream().map(IFile::getProject).collect(Collectors.toSet()));
	}

	@Override
	public boolean wasFullBuild() {
		return fullBuild;
	}

	@Override
	public boolean didComplete() {
		return complete;
	}

	@Override
	public Set<IProject> getAccessedProjects() {
		return accessedProjects;
	}

}
