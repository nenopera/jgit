# Improvements to jgit

## Intro

Some improvements to jgit project to be useful as a visual git component

This is how it looks like by now:
![jgit glog --decorate](res/snapshot.png)

## previous README

            == Java GIT ==

This package is licensed under the BSD.

  org.eclipse.jgit/

    A pure Java library capable of being run standalone, with no
    additional support libraries.  Some JUnit tests are provided
    to exercise the library.  The library provides functions to
    read and write a GIT formatted repository.

    All portions of jgit are covered by the BSD.  Absolutely no GPL,
    LGPL or EPL contributions are accepted within this package.

  org.eclipse.jgit.test/
    Unit tests for org.eclipse.jgit and the same licensing rules.

            == WARNINGS / CAVEATS              ==

- Symbolic links are not supported because java does not support it.
  Such links could be damaged.

- Only the timestamp of the index is used by jgit check if  the index
  is dirty.

- Don't try the library with a JDK other than 1.6 (Java 6) unless you
  are prepared to investigate problems yourself. JDK 1.5.0_11 and later
  Java 5 versions *may* work. Earlier versions do not. JDK 1.4 is *not*
  supported. Apple's Java 1.5.0_07 is reported to work acceptably. We
  have no information about other vendors. Please report your findings
  if you try.

- CRLF conversion is never performed. On Windows you should thereforc
  make sure your projects and workspaces are configured to save files
  with Unix (LF) line endings.

            == Package Features                ==

  org.eclipse.jgit/

    * Read loose and packed commits, trees, blobs, including
      deltafied objects.

    * Read objects from shared repositories

    * Write loose commits, trees, blobs.

    * Write blobs from local files or Java InputStreams.

    * Read blobs as Java InputStreams.

    * Copy trees to local directory, or local directory to a tree.

    * Lazily loads objects as necessary.

    * Read and write .git/config files.

    * Create a new repository.

    * Read and write refs, including walking through symrefs.

    * Read, update and write the Git index.

    * Checkout in dirty working directory if trivial.

    * Walk the history from a given set of commits looking for commits
      introducing changes in files under a specified path.

    * Object transport
      Fetch via ssh, git, http, Amazon S3 and bundles.
      Push via ssh, git and Amazon S3. JGit does not yet deltify
      the pushed packs so they may be a lot larger than C Git packs.

  org.eclipse.jgit.pgm/

    * Assorted set of command line utilities. Mostly for ad-hoc testing of jgit
      log, glog, fetch etc.

            == Missing Features                ==

There are a lot of missing features. You need the real Git for this.
For some operations it may just be the preferred solution also. There
are not just a command line, there is e.g. git-gui that makes committing
partial files simple.

- Merging. 

- Repacking.

- Generate a GIT format patch.

- Apply a GIT format patch.

- Documentation. :-)

- gitattributes support
  In particular CRLF conversion is not implemented. Files are treated
  as byte sequences.

- submodule support
  Submodules are not supported or even recognized.

            == Support                         ==

  Post question, comments or patches to the git@vger.kernel.org mailing list.


            == Contributing                    ==

  See SUBMITTING_PATCHES in this directory. However, feedback and bug reports
  are also contributions.


            == About GIT                       ==

More information about GIT, its repository format, and the canonical
C based implementation can be obtained from the GIT websites:

  http://git.or.cz/
  http://www.kernel.org/pub/software/scm/git/
  http://www.kernel.org/pub/software/scm/git/docs/

