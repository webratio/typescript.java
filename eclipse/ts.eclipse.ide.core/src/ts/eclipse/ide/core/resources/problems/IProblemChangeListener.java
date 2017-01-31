package ts.eclipse.ide.core.resources.problems;

import java.util.Set;

import org.eclipse.core.resources.IResource;

/**
 * Listener that is notified after changes occur in the TypeScript problems of
 * workspace resources.
 */
public interface IProblemChangeListener {

	/**
	 * Called when problems change.
	 * 
	 * @param changedResources
	 *            set of resources on which problems have changed. This also
	 *            includes containers where the <i>combined</i> problem status
	 *            may have changed because of changes in the contained files or
	 *            sub-containers.
	 */
	public void problemsChanged(Set<IResource> changedResources);

}
