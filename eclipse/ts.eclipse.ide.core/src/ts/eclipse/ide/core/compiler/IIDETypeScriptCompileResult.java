package ts.eclipse.ide.core.compiler;

import java.util.Set;

import org.eclipse.core.resources.IProject;

/**
 * IDE TypeScript information about a finished compilation.
 *
 */
public interface IIDETypeScriptCompileResult {

	/**
	 * Determines whether the completed compilation was a full build of its
	 * tsconfig.json as opposed to a partial compile of a subset of files.
	 * 
	 * @return
	 */
	public boolean wasFullBuild();

	/**
	 * Determine whether the compilation actually completed or was halted
	 * because of some blocking build configuration error.
	 * 
	 * Note that this method returns {@code true} in cases of problems found in
	 * source file, since in that case the compilation started and finished
	 * correctly.
	 * 
	 * @return
	 */
	public boolean didComplete();

	/**
	 * Gets all projects that were accessed during compilation.
	 * 
	 * @return
	 */
	public Set<IProject> getAccessedProjects();

}
