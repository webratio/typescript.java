package ts.eclipse.ide.core.resources.problems;

/**
 * Manager for getting updates about the global state of TypeScript problems on
 * workspace resources.
 */
public interface IProblemManager {

	/**
	 * Adds a listener to be notified when problems change on workspace
	 * resources.
	 * 
	 * @param listener
	 *            listener to notify.
	 */
	public void addProblemChangedListener(IProblemChangeListener listener);

	/**
	 * Removes a problem change listener previously added via
	 * {@link #addProblemChangedListener}.
	 * 
	 * @param listener
	 *            listener to remove.
	 */
	public void removeProblemChangedListener(IProblemChangeListener listener);

}
