/**
 * This class represents a file structure using an automaton.
 *
 * @author Rodney Rodriguez
 */
package edu.utsa.fileflow.client.fileflow;

import dk.brics.automaton.Automaton;
import dk.brics.automaton.FiniteStateTransducer;
import dk.brics.automaton.TransducerTransition;
import edu.utsa.fileflow.analysis.Mergeable;
import edu.utsa.fileflow.client.fileflow.variable.Variable;

public class FileStructure implements Cloneable, Mergeable<FileStructure> {

	// automaton representing files in a file structure
	Automaton files;

	static {
		Automaton.setMinimization(Automaton.MINIMIZE_BRZOZOWSKI);
		Automaton.setMinimizeAlways(true);
	}

	private static final Automaton SEPARATOR = Automaton.makeChar(VariableAutomaton.SEPARATOR_CHAR);
	private static final Automaton ANY = Automaton.makeAnyString();

	private VariableAutomaton cwd = new VariableAutomaton(VariableAutomaton.SEPARATOR_AUT);

	public FileStructure() {
		this.files = SEPARATOR.clone();
	}

	private FileStructure(Automaton files) {
		this.files = files;
	}

	public static FileStructure top() {
		Automaton files = SEPARATOR.clone();
		files.concatenate(ANY);
		return new FileStructure(files);
	}

	/**
	 * @return a clone of the separator automaton.
	 */
	public static Automaton separator() {
		return SEPARATOR.clone();
	}

	/**
	 * Cleans a string representing a file path.
	 *
	 * @param fp The file path to clean.
	 * @return a new String with the cleaned file path.
	 */
	public static String clean(String fp) {
		fp = fp.trim();
		fp = fp.replaceAll("[/\\\\]+", "/");
		return fp;
	}

	/**
	 * Changes the current working directory.
	 * TODO: handle '..' and '.' (Use Transducers.FST_PARENT_DIR)
	 */
	public void changeWorkingDirectory(VariableAutomaton fp) {
		if (fp.startsWith(FileStructure.separator())) {
			cwd = fp.clone();
		} else {
			cwd = new VariableAutomaton(absolute(fp));
		}
		// Append a slash if fp did not have one
		if (!cwd.endsWith(VariableAutomaton.SEPARATOR_AUT)) {
			cwd = cwd.concatenate(VariableAutomaton.SEPARATOR_VA);
		}
	}

	/**
	 * Creates a file or directory at the path provided. This method will not throw
	 * an exception. If a file does not exist it will forcefully be created by
	 * creating every non-existing file in its path. If the file already exists,
	 * then no changes will be made.
	 *
	 * @param fp The file path to create the file or directory at.
	 */
	public void forceCreate(VariableAutomaton fp) {
		union(fp);
	}

	/**
	 * Creates a file at the file path provided. Parent directory must exist for
	 * this operation to be successful. <code>fp</code> must not represent a
	 * directory (include a trailing slash).
	 *
	 * @param fp The file path to create the file.
	 * @throws FileStructureException if the parent directory does not exist
	 */
	public void createFile(VariableAutomaton fp) throws FileStructureException {
		if (fp.isDirectory())
			throw new FileStructureException(String.format("touch: cannot touch '%s**': Cannot touch a directory", fp));

		// if the parent directory does not exist throw an exception
		if (!fileExists(fp.getParentDirectory()))
			throw new FileStructureException(String.format("touch: cannot touch '%s**': No such file or directory", fp));

		// if the file already exists, throw an exception
		if (fileExists(fp))
			throw new FileStructureException(String.format("touch: cannot touch '%s**': File already exists", fp));

		union(fp);
	}

	/**
	 * Creates a directory in the file structure at the path provided. If the
	 * path to that directory does not exist, it will be created.
	 *
	 * @param fp The file path to the directory to be created.
	 * @throws FileStructureException if a file already exists at <code>fp</code>.
	 */
	public void createDirectory(VariableAutomaton fp) throws FileStructureException {
		// if fp does not have a trailing separator, then add one
		if (!fp.endsWith(SEPARATOR))
			fp = fp.concatenate(VariableAutomaton.SEPARATOR_VA);

		VariableAutomaton a = new VariableAutomaton(absolute(fp));
		if (fileExists(a))
			throw new FileStructureException(String.format("mkdir: cannot create directory '%s**': File exists", a));

		union(fp);
	}

	/**
	 * Removes a file from the file structure at the path provided. If the path to
	 * the file does not exist, an exception will be thrown.
	 *
	 * @param fp The file path to the file to be removed.
	 * @throws FileStructureException if the file path does not exist in the file structure.
	 */
	public void removeFile(VariableAutomaton fp) throws FileStructureException {
		if (!fileExists(fp)) {
			throw new FileStructureException(String.format("rm: cannot remove '%s**': No such file or directory", fp));
		}

		if (!isDirectory(fp)) {
			minus(fp);
		} else {
			throw new FileStructureException(
					String.format("rm: cannot remove '%s**': attempting to remove directory without recursive option", fp));
		}
	}

	/**
	 * Removes a file or directory from the file structure at the path provided.
	 * If the file is a directory, the directory and all paths under it will
	 * be removed. If the path to the file does not exist, an exception will be thrown.
	 *
	 * @param fp The file path to the file or directory to be removed.
	 * @throws FileStructureException if the file path does not exist in the file structure.
	 */
	public void removeFileRecursive(VariableAutomaton fp) throws FileStructureException {
		if (!fileExists(fp)) {
			throw new FileStructureException(String.format("rm: cannot remove '%s**': No such file or directory", fp));
		}

		if (isDirectory(fp)) {
			// if dir, minus(fp) & minus(fp/*)
			minus(fp.join(VariableAutomaton.ANY_PATH));
		} else {
			minus(fp.union(fp.join(VariableAutomaton.ANY_PATH)));
		}
	}

	/**
	 * Copies a file to another location in the file structure. Consider the
	 * following cases:
	 * <ul>
	 * <li/>
	 * <p/>
	 * file1 to file2: If file2 exists it will be overwritten; else it will be
	 * created
	 * <li/>
	 * <p/>
	 * file1 to dir2: file1 will be copied into dir2 overwriting file1 in dir2
	 * if it exists
	 * <li/>
	 * <p/>
	 * dir1 to dir2: a copy of dir1 will be created in dir2. if dir2/dir1
	 * happens to exist, contents will be merged overwriting the existing
	 * <li/>
	 * <p/>
	 * dir1 to file2: cp: cannot overwrite non-directory 'file2' with directory
	 * 'dir1'
	 * <li/>
	 * <p/>
	 * file1 to non-existing: cp: cannot create regular file
	 * 'non-existing/file1': No such file or directory
	 * <li/>
	 * <p/>
	 * dir1 to non-existing: cp: cannot create directory 'non-existing/dir1': No
	 * such file or directory
	 * </ul>
	 *
	 * @param source      The path pointing to the source file to copy
	 * @param destination The path pointing to the destination location
	 * @throws FileStructureException
	 */
	public void copy(final VariableAutomaton source, final VariableAutomaton destination) throws FileStructureException {
		boolean destExists = fileExists(destination);
		VariableAutomaton $destination = destination;

		// check if source exists
		if (!fileExists(source))
			throw new FileStructureException(String.format("cp: cannot stat '%s**': No such file or directory", source));

		// check if the paths point to the same file
		if (source.isSamePathAs(destination))
			throw new FileStructureException(String.format("cp: '%s**' and '%s**' are the same file", source, source));

		// cache some booleans
		boolean destIsDir = isDirectory(destination);
		boolean destIsReg = isRegularFile(destination);
		boolean srcIsDir = isDirectory(source);
		boolean srcIsReg = isRegularFile(source);

		// make sure either the destination or parent to destination exists
		if (!destExists) {
			if (srcIsReg && destination.isDirectory())
				throw new FileStructureException(
						String.format("cp: cannot create regular file '%s**': No such file or directory", destination));

			VariableAutomaton destParent = destination.getParentDirectory();
			if (!fileExists(destParent)) {
				if (srcIsReg) {
					throw new FileStructureException(
							String.format("cp: cannot create file '%s**': No such file or directory", destination));
				} else {
					throw new FileStructureException(
							String.format("cp: cannot create directory '%s**': No such file or directory", destination));
				}
			}
			// if destination does not exist then change it to the parent
			$destination = destParent;
		}

		if (destIsDir) {
			// if dest is a directory, dest must end with a slash
			$destination = destination.concatenate(VariableAutomaton.SEPARATOR_VA);
		} else if (srcIsDir && destIsReg) {
			throw new FileStructureException(
					String.format("cp: cannot overwrite non-directory '%s**' with directory '%s**'", destination, source));
		}

		Automaton src = absolute(source);
		Automaton dst = absolute($destination);
		Automaton a;

		if (!destExists) {
			// if dest does not exist then dest base name should be created
			a = Transducers.basename(absolute(destination));
		} else {
			// get all files to be copied (absolute paths)
			a = files.intersection(src.concatenate(ANY));

			if (srcIsReg)
				src = absolute(source.getParentDirectory());
			Automaton $src = Transducers.removeLastSeparator(src).concatenate(SEPARATOR);
			if ($src.isEmpty())
				$src = src;
			$src = $src.concatenate(ANY);

			// we need a FST to replace src prefix with empty
			FiniteStateTransducer replace = FiniteStateTransducer.AutomatonToTransducer($src);
			replace.getAcceptStates().forEach(s -> {
				s.getTransitions().forEach(t -> {
					((TransducerTransition) t).setIdentical(true);
				});
			});
			a = replace.intersection(a);
			// if source was a directory then initial state will be true
			// but we need it to be false
			a.getInitialState().setAccept(false);

			// after this we are left with everything after source in a
			// we need to prepend the base name to the result
			// only prepend the base name if source is a directory
			if (srcIsDir) {
				Automaton basename = Transducers.basename(src);
				a = basename.concatenate(SEPARATOR).concatenate(a);
			}
		}

		// insert the source files to the file structure
		VariableAutomaton insert = new VariableAutomaton(dst.concatenate(a));
		files = files.union(insert.getSeparatedAutomaton());
	}

	/**
	 * Determines whether a file exists. It does not matter if it is a directory
	 * or regular file or possibly both.
	 *
	 * @param fp The file path of the file to check if it exists.
	 * @return true if the file exists; false otherwise.
	 */
	public boolean fileExists(VariableAutomaton fp) {
		fp = new VariableAutomaton(absolute(fp));
		// try as a regular file
		// should not return true if fp is empty
		if (fp.getAutomaton().isEmpty())
			return false;
		fp = fp.removeLastSeparator();
		if (fp.subsetOf(files))
			return true;

		// try as a directory
		fp = fp.concatenate(new VariableAutomaton(SEPARATOR));
		return fp.subsetOf(files);
	}

	/**
	 * Tells if a file path is a directory in the file structure.
	 *
	 * @param fp The file path to check if a directory exists at.
	 * @return True if a directory exists at <code>fp</code>; false otherwise.
	 */
	public boolean isDirectory(VariableAutomaton fp) {
		fp = new VariableAutomaton(absolute(fp));
		fp = fp.concatenate(VariableAutomaton.SEPARATOR_VA);
		return fp.subsetOf(files);
	}

	/**
	 * Tells if a file path is a regular file in the file structure.
	 *
	 * @param fp The file path to check if a regular file exists at.
	 * @return True if a regular file exists at <code>fp</code>; false
	 * otherwise.
	 */
	public boolean isRegularFile(VariableAutomaton fp) {
		fp = new VariableAutomaton(absolute(fp));
		fp = fp.removeLastSeparator();
		return fp.subsetOf(files);
	}

	/**
	 * Graphviz DOT representation of this file structure.
	 *
	 * @return a Graphviz DOT representation of the files automaton.
	 */
	public String toDot() {
		return files.toDot();
	}

	/**
	 * Prepends the current working directory to the file path variable given.
	 *
	 * @param fp The file path to be appended to the current working directory.
	 * @return the absolute file path as an automaton.
	 */
	private Automaton absolute(VariableAutomaton fp) {
		if (fp.startsWith(VariableAutomaton.SEPARATOR_AUT)) {
			return fp.getAutomaton();
		}
		return cwd.concatenate(fp).getAutomaton();
	}

	/**
	 * Performs a union operation on <code>files</code>. <code>fp</code> is
	 * converted to an absolute path before the union.
	 *
	 * @param fp The variable automaton to union with <code>files</code>.
	 */
	private void union(VariableAutomaton fp) {
		files = files.union(new VariableAutomaton(absolute(fp)).getSeparatedAutomaton());
	}

	/**
	 * Performs an intersection operation on <code>files</code>. <code>fp</code> is
	 * converted to an absolute path before the intersection.
	 *
	 * @param fp The variable automaton to intersect with <code>files</code>.
	 */
	private void intersect(VariableAutomaton fp) {
		files = files.intersection(new VariableAutomaton(absolute(fp)).getSeparatedAutomaton());
	}

	/**
	 * Performs a minus operation on <code>files</code>. <code>fp</code> is
	 * converted to an absolute path before the minus operation.
	 *
	 * @param fp The variable automaton to minus from <code>files</code>.
	 */
	private void minus(VariableAutomaton fp) {
		files = files.minus(new VariableAutomaton(absolute(fp)).getAutomaton());
	}

	@Override
	public FileStructure merge(FileStructure other) {
		files = files.union(other.files);
		return this;
	}

	@Override
	public boolean equals(Object obj) {
		if (!(obj instanceof FileStructure))
			return false;
		FileStructure o = (FileStructure) obj;
		return files.equals(o.files);
	}

	@Override
	public FileStructure clone() {
		FileStructure clone = new FileStructure();
		clone.files = files.clone();
		clone.cwd = cwd.clone();
		return clone;
	}
}
