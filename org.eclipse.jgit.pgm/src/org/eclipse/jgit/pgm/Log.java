/*
 * Copyright (C) 2010, Google Inc.
 * Copyright (C) 2006-2008, Robin Rosenberg <robin.rosenberg@dewire.com>
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org>
 * and other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Distribution License v1.0 which
 * accompanies this distribution, is reproduced below, and is
 * available at http://www.eclipse.org/org/documents/edl-v10.php
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the following
 * conditions are met:
 *
 * - Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above
 *   copyright notice, this list of conditions and the following
 *   disclaimer in the documentation and/or other materials provided
 *   with the distribution.
 *
 * - Neither the name of the Eclipse Foundation, Inc. nor the
 *   names of its contributors may be used to endorse or promote
 *   products derived from this software without specific prior
 *   written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.eclipse.jgit.pgm;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.RawText;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.diff.RenameDetector;
import org.eclipse.jgit.errors.LargeObjectException;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.notes.NoteMap;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.util.GitDateFormatter;
import org.eclipse.jgit.util.GitDateFormatter.Format;
import org.kohsuke.args4j.Option;

@Command(common = true, usage = "usage_viewCommitHistory")
class Log extends RevWalkTextBuiltin {

	private GitDateFormatter dateFormatter = new GitDateFormatter(
			Format.DEFAULT);

	private final DiffFormatter diffFmt = new DiffFormatter( //
			new BufferedOutputStream(System.out));

	private Map<AnyObjectId, Set<Ref>> allRefsByPeeledObjectId;

	private Map<String, NoteMap> noteMaps;

	@Option(name = "--no-standard-notes", usage = "usage_noShowStandardNotes")
	private boolean noStandardNotes;

	private List<String> additionalNoteRefs = new ArrayList<String>();

	@Option(name = "--show-notes", usage = "usage_showNotes", metaVar = "metaVar_ref")
	void addAdditionalNoteRef(String notesRef) {
		additionalNoteRefs.add(notesRef);
	}

	@Option(name = "--date", usage = "usage_date")
	void dateFormat(String date) {
		if (date.toLowerCase().equals(date))
			date = date.toUpperCase();
		dateFormatter = new GitDateFormatter(Format.valueOf(date));
	}

	// BEGIN -- Options shared with Diff
	@Option(name = "-p", usage = "usage_showPatch")
	boolean showPatch;

	@Option(name = "-M", usage = "usage_detectRenames")
	private Boolean detectRenames;

	@Option(name = "--no-renames", usage = "usage_noRenames")
	void noRenames(@SuppressWarnings("unused") boolean on) {
		detectRenames = Boolean.FALSE;
	}

	@Option(name = "-l", usage = "usage_renameLimit")
	private Integer renameLimit;

	@Option(name = "--name-status", usage = "usage_nameStatus")
	private boolean showNameAndStatusOnly;

	@Option(name = "--ignore-space-at-eol")
	void ignoreSpaceAtEol(@SuppressWarnings("unused") boolean on) {
		diffFmt.setDiffComparator(RawTextComparator.WS_IGNORE_TRAILING);
	}

	@Option(name = "--ignore-leading-space")
	void ignoreLeadingSpace(@SuppressWarnings("unused") boolean on) {
		diffFmt.setDiffComparator(RawTextComparator.WS_IGNORE_LEADING);
	}

	@Option(name = "-b", aliases = { "--ignore-space-change" })
	void ignoreSpaceChange(@SuppressWarnings("unused") boolean on) {
		diffFmt.setDiffComparator(RawTextComparator.WS_IGNORE_CHANGE);
	}

	@Option(name = "-w", aliases = { "--ignore-all-space" })
	void ignoreAllSpace(@SuppressWarnings("unused") boolean on) {
		diffFmt.setDiffComparator(RawTextComparator.WS_IGNORE_ALL);
	}

	@Option(name = "-U", aliases = { "--unified" }, metaVar = "metaVar_linesOfContext")
	void unified(int lines) {
		diffFmt.setContext(lines);
	}

	@Option(name = "--abbrev", metaVar = "metaVar_n")
	void abbrev(int lines) {
		diffFmt.setAbbreviationLength(lines);
	}

	@Option(name = "--full-index")
	void abbrev(@SuppressWarnings("unused") boolean on) {
		diffFmt.setAbbreviationLength(Constants.OBJECT_ID_STRING_LENGTH);
	}

	@Option(name = "--src-prefix", usage = "usage_srcPrefix")
	void sourcePrefix(String path) {
		diffFmt.setOldPrefix(path);
	}

	@Option(name = "--dst-prefix", usage = "usage_dstPrefix")
	void dstPrefix(String path) {
		diffFmt.setNewPrefix(path);
	}

	@Option(name = "--no-prefix", usage = "usage_noPrefix")
	void noPrefix(@SuppressWarnings("unused") boolean on) {
		diffFmt.setOldPrefix("");
		diffFmt.setNewPrefix("");
	}

	// END -- Options shared with Diff

    String term;

    Log() {
		dateFormatter = new GitDateFormatter(Format.DEFAULT);
        term = System.getenv("TERM");
        term = term == null ? "none" : term;
    }

	@Override
	protected void run() throws Exception {
		diffFmt.setRepository(db);
		try {
			diffFmt.setPathFilter(pathFilter);
			if (detectRenames != null)
				diffFmt.setDetectRenames(detectRenames.booleanValue());
			if (renameLimit != null && diffFmt.isDetectRenames()) {
				RenameDetector rd = diffFmt.getRenameDetector();
				rd.setRenameLimit(renameLimit.intValue());
			}

			if (!noStandardNotes || !additionalNoteRefs.isEmpty()) {
				createWalk();
				noteMaps = new LinkedHashMap<String, NoteMap>();
				if (!noStandardNotes) {
					addNoteMap(Constants.R_NOTES_COMMITS);
				}
				if (!additionalNoteRefs.isEmpty()) {
					for (String notesRef : additionalNoteRefs) {
						if (!notesRef.startsWith(Constants.R_NOTES)) {
							notesRef = Constants.R_NOTES + notesRef;
						}
						addNoteMap(notesRef);
					}
				}
			}

			if (decorate) {
                allRefsByPeeledObjectId = getRepository().getAllRefsByPeeledObjectId();
            }

            super.run();
		} finally {
			diffFmt.release();
		}
	}

	private void addNoteMap(String notesRef) throws IOException {
		Ref notes = db.getRef(notesRef);
		if (notes == null)
			return;
		RevCommit notesCommit = argWalk.parseCommit(notes.getObjectId());
		noteMaps.put(notesRef,
				NoteMap.read(argWalk.getObjectReader(), notesCommit));
	}

	@Override
	protected void show(final RevCommit c) throws Exception {
        if (!showOneLine) {
		    out.print(CLIText.get().commitLabel);
            out.print(" ");
        }
        if (color) {
            out.print(getColor(RESET, YELLOW_FG, BLACK_BG));
        }
        if (!showOneLine) {
            out.print(c.getId().getName());
        } else {
            out.print(c.getId().abbreviate(7).name());
        }
        if (color) {
            out.print(getColor(RESET, WHITE_FG, BLACK_BG));
        }
		if (decorate) {
			Collection<Ref> list = allRefsByPeeledObjectId.get(c);
			if (list != null) {
				out.print(" (");
				for (Iterator<Ref> i = list.iterator(); i.hasNext(); ) {
                    Ref object = i.next();
                    boolean symbolic = object.isSymbolic();
                    String name = object.getName();
                    boolean remote = name.contains(Constants.R_REMOTES);
                    name = Repository.shortenRefName(name);
                    if (color) {
                        if (remote) {
                            out.print(getColor(BRIGHT, RED_FG, BLACK_BG));
                        } else {
                            if (symbolic) {
                                out.print(getColor(BRIGHT, CYAN_FG, BLACK_BG));
                            } else {
                                out.print(getColor(BRIGHT, GREEN_FG, BLACK_BG));
                            }
                        }
                    }

                    out.print(name);
                    if (color) {
                        out.print(getColor(RESET, WHITE_FG, BLACK_BG));
                    }
					if (i.hasNext())
						out.print(", ");
				}
				out.print(")");
			}
		}
        if (!showOneLine) {
		    out.println();

            final PersonIdent author = c.getAuthorIdent();
            out.println(MessageFormat.format(CLIText.get().authorInfo, author.getName(), author.getEmailAddress()));
            out.println(MessageFormat.format(CLIText.get().dateInfo,
                    dateFormatter.formatDate(author)));
            out.println();
        }

		final String[] lines = c.getFullMessage().split("\n");
        int count = 1;
        if (showOneLine) {
            out.print(" " + c.getShortMessage());
        } else {
            for (final String s : lines) {
                out.print("    ");
                out.print(s);
                out.println();
            }

        }

		out.println();
		if (showNotes(c))
			out.println();

		if (c.getParentCount() == 1 && (showNameAndStatusOnly || showPatch))
			showDiff(c);
		out.flush();
	}

    private String getColor(int attrs, int fg, int bg) {
        String colorSequence = "";
        if (term.equals("none")) {
            colorSequence = "";
        } else if (term.equals("xterm") || term.equals("linux")) {
            colorSequence = "\033[" + attrs + ";" + fg + ";" + bg + "m";
        }
        return colorSequence;
    }


    public static int RESET 	 = 0;  // Reset All Attributes (return to normal mode)
    public static int BRIGHT     = 1;  // Bright (Usually turns on BOLD)
    public static int DIM        = 2;  // Dim
    public static int UNDERLINE  = 3;  // Underline
    public static int BLINK      = 5;  // Blink
    public static int REVERSE    = 7;  // Reverse
    public static int HIDDEN     = 8;  // Hidden

//    {fg} is one of the following

    public static int BLACK_FG   = 30;
    public static int RED_FG     = 31;
    public static int GREEN_FG   = 32;
    public static int YELLOW_FG  = 33;
    public static int BLUE_FG    = 34;
    public static int MAGENTA_FG = 35;
    public static int CYAN_FG    = 36;
    public static int WHITE_FG   = 37;

//    {bg} is one of the following

   	public static int BLACK_BG      = 40;
   	public static int RED_BG        = 41;
   	public static int GREEN_BG      = 42;
   	public static int YELLOW_BG     = 43;
   	public static int BLUE_BG       = 44;
   	public static int MAGENTA_BG    = 45;
   	public static int CYAN_BG       = 46;
   	public static int WHITE_BG      = 47;

	/**
	 * @param c
	 * @return <code>true</code> if at least one note was printed,
	 *         <code>false</code> otherwise
	 * @throws IOException
	 */
	private boolean showNotes(RevCommit c) throws IOException {
		if (noteMaps == null)
			return false;

		boolean printEmptyLine = false;
		boolean atLeastOnePrinted = false;
		for (Map.Entry<String, NoteMap> e : noteMaps.entrySet()) {
			String label = null;
			String notesRef = e.getKey();
			if (! notesRef.equals(Constants.R_NOTES_COMMITS)) {
				if (notesRef.startsWith(Constants.R_NOTES))
					label = notesRef.substring(Constants.R_NOTES.length());
				else
					label = notesRef;
			}
			boolean printedNote = showNotes(c, e.getValue(), label,
					printEmptyLine);
			atLeastOnePrinted |= printedNote;
			printEmptyLine = printedNote;
		}
		return atLeastOnePrinted;
	}

	/**
	 * @param c
	 * @param map
	 * @param label
	 * @param emptyLine
	 * @return <code>true</code> if note was printed, <code>false</code>
	 *         otherwise
	 * @throws IOException
	 */
	private boolean showNotes(RevCommit c, NoteMap map, String label,
			boolean emptyLine)
			throws IOException {
		ObjectId blobId = map.get(c);
		if (blobId == null)
			return false;
		if (emptyLine)
			out.println();
		out.print("Notes");
		if (label != null) {
			out.print(" (");
			out.print(label);
			out.print(")");
		}
		out.println(":");
		try {
			RawText rawText = new RawText(argWalk.getObjectReader()
					.open(blobId).getCachedBytes(Integer.MAX_VALUE));
			for (int i = 0; i < rawText.size(); i++) {
				out.print("    ");
				out.println(rawText.getString(i));
			}
		} catch (LargeObjectException e) {
			out.println(MessageFormat.format(
					CLIText.get().noteObjectTooLargeToPrint, blobId.name()));
		}
		return true;
	}

	private void showDiff(RevCommit c) throws IOException {
		final RevTree a = c.getParent(0).getTree();
		final RevTree b = c.getTree();

		if (showNameAndStatusOnly)
			Diff.nameStatus(out, diffFmt.scan(a, b));
		else {
			out.flush();
			diffFmt.format(a, b);
			diffFmt.flush();
		}
		out.println();
	}
}
