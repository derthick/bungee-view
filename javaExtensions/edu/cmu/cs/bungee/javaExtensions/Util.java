/*

 Created on Mar 4, 2005

 The Bungee View applet lets you search, browse, and data-mine an image collection.
 Copyright (C) 2006  Mark Derthick

 This program is free software; you can redistribute it and/or
 modify it under the terms of the GNU General Public License
 as published by the Free Software Foundation; either version 2
 of the License, or (at your option) any later version.  See gpl.html.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program; if not, write to the Free Software
 Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.

 You may also contact the author at
 mad@cs.cmu.edu,
 or at
 Mark Derthick
 Carnegie-Mellon University
 Human-Computer Interaction Institute
 Pittsburgh, PA 15213

 */

package edu.cmu.cs.bungee.javaExtensions;

import static edu.cmu.cs.bungee.javaExtensions.UtilMath.weight;

import java.awt.event.InputEvent;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

import javax.jnlp.BasicService;
import javax.jnlp.ClipboardService;
import javax.jnlp.ServiceManager;
import javax.jnlp.UnavailableServiceException;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;

import jdk.nashorn.internal.ir.annotations.Immutable;

/**
 * misc static functions on numbers, strings, treating arrays as sets
 *
 */
public final class Util {

	/**
	 * modifier flagging that user gesture adds a negated filter. Choose a bit
	 * that isn't used by InputEvent (control, shift, etc)
	 */
	public static final int EXCLUDE_ACTION = 0x4000;

	public static final int CLICK_THUMB_INTENTIONALLY_MODIFIER = 1;

	/**
	 * LEFT_BUTTON == BUTTON1
	 */
	public static final int LEFT_BUTTON_MASK = InputEvent.BUTTON1_DOWN_MASK;
	/**
	 * MIDDLE_BUTTON == BUTTON2
	 */
	public static final int MIDDLE_BUTTON_MASK = InputEvent.BUTTON2_DOWN_MASK;
	/**
	 * RIGHT_BUTTON == BUTTON3
	 */
	public static final int RIGHT_BUTTON_MASK = InputEvent.BUTTON3_DOWN_MASK;

	public static final int BUTTON_MASK = LEFT_BUTTON_MASK | MIDDLE_BUTTON_MASK | RIGHT_BUTTON_MASK;

	public static final @NonNull List<Boolean> BOOLEAN_VALUES = booleanValues();

	private Util() {
		// Disallow instantiation
	}

	public static int ensureButton(int modifiers) {
		if (nButtons(modifiers) == 0) {
			modifiers |= LEFT_BUTTON_MASK;
		}
		return modifiers;
	}

	public static int nButtons(final int modifiers) {
		return weight(modifiers & BUTTON_MASK);
	}

	public static boolean areAssertionsEnabled() {
		boolean result = false;
		assert (result = true) == true;
		return result;
	}

	public static @NonNull <T> T nonNull(final T t) {
		assert t != null;
		return t;
	}

	public static void sleep(final long ms) {
		try {
			Thread.sleep(ms);
		} catch (final InterruptedException e) {
			// Ignore
		}
	}

	public static boolean assertMouseProcess() {
		assert java.awt.EventQueue.isDispatchThread() : Thread.currentThread();
		return true;
	}

	public static boolean assertNotMouseProcess() {
		assert !java.awt.EventQueue.isDispatchThread() : Thread.currentThread();
		return true;
	}

	/**
	 * @param ignore
	 *            a variable for which to ignore never-read Warnings
	 * @return true
	 */
	public static boolean ignore(final boolean ignore) { // NO_UCD (unused code)
		return ignore || true;
	}

	/**
	 * @param ignore
	 *            a variable for which to ignore never-read Warnings
	 */
	public static void ignore(final int ignore) {
		// ignore
	}

	/**
	 * @param ignore
	 *            a variable for which to ignore never-read Warnings
	 */
	public static void ignore(final Object ignore) {
		// ignore
	}

	/**
	 * @param ignore
	 *            a variable for which to ignore never-read Warnings
	 */
	public static void ignore(final double ignore) { // NO_UCD (unused code)
		// ignore
	}

	public static boolean isControlDown(final int modifiers) {
		return (modifiers & InputEvent.CTRL_DOWN_MASK) != 0;
	}

	public static boolean isShiftDown(final int modifiers) {
		return (modifiers & InputEvent.SHIFT_DOWN_MASK) != 0;
	}

	public static boolean isControlOrShiftDown(final int modifiers) {
		return (modifiers & (InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK)) != 0;
	}

	/**
	 * @param modifiers
	 *            mouse gesture modifiers. EXCLUDE_ACTION doesn't correspond to
	 *            any InputEvent mask.
	 * @return should the gesture add a negated filter?
	 */
	public static boolean isExcludeAction(final int modifiers) {
		return (modifiers & EXCLUDE_ACTION) != 0;
	}

	/**
	 * @param modifiers
	 * @return are modifier exactly ALT_DOWN_MASK
	 */
	public static boolean isDisplayOnlyAction(final int modifiers) {
		return (modifiers & Util.ALL_SHIFT_KEYS_MASK) == InputEvent.ALT_DOWN_MASK;
	}

	/**
	 * CONTROL, SHIFT, ALT, META
	 */
	public static boolean isAnyShiftKeyDown(final int modifiers) {
		return (modifiers & ALL_SHIFT_KEYS_MASK) != 0;
	}

	/**
	 * CONTROL, SHIFT, ALT, META
	 */
	static final int ALL_SHIFT_KEYS_MASK = InputEvent.CTRL_DOWN_MASK | InputEvent.CTRL_MASK | InputEvent.SHIFT_DOWN_MASK
			| InputEvent.SHIFT_MASK | InputEvent.ALT_DOWN_MASK | InputEvent.ALT_MASK | InputEvent.META_DOWN_MASK
			| InputEvent.META_MASK;

	/**
	 * CONTROL, SHIFT, ALT, META, EXCLUDE, CLICK_THUMB_INTENTIONALLY_MODIFIER
	 * (for Replayer)
	 */
	public static final int ALL_SHIFT_KEYS_PLUS_EXCLUDE_PLUS_CLICK_THUMB_INTENTIONALLY = InputEvent.CTRL_DOWN_MASK
			| InputEvent.SHIFT_DOWN_MASK | InputEvent.ALT_DOWN_MASK | InputEvent.META_DOWN_MASK | EXCLUDE_ACTION
			| CLICK_THUMB_INTENTIONALLY_MODIFIER;

	/**
	 * @return String starting with " "
	 */
	public static @NonNull String printModifiersHex(final int modifiers) {
		return " (0x" + Integer.toHexString(modifiers) + ")";
	}

	public static void printModifiersHex(final int modifiers, @NonNull final StringBuilder buf) {
		buf.append(" (0x").append(Integer.toHexString(modifiers)).append(")");
	}

	/**
	 * @return String starting with " "
	 */
	public static @NonNull String printModifiersEx(final int modifiers) {
		final StringBuilder buf = new StringBuilder(" modifiers='");
		boolean needComma = false;
		if (isExcludeAction(modifiers)) {
			buf.append("Exclude");
			needComma = true;
		}
		if ((modifiers & CLICK_THUMB_INTENTIONALLY_MODIFIER) != 0) {
			if (needComma) {
				buf.append(", ");
			}
			buf.append("Click Thumb Intentionally");
			needComma = true;
		}
		final int modifiers2 = modifiers & ~EXCLUDE_ACTION & ~CLICK_THUMB_INTENTIONALLY_MODIFIER;
		assert (InputEvent.getModifiersExText(modifiers2).length() > 0) == (modifiers2 != 0);
		if (modifiers2 != 0) {
			if (needComma) {
				buf.append(", ");
			}
			buf.append(InputEvent.getModifiersExText(modifiers2));
		}
		buf.append("'");
		printModifiersHex(modifiers, buf);
		final String result = buf.toString();
		assert result != null;
		return result;
	}

	// TODO Remove unused code found by UCDetector
	// public static String printKeyEventKey(final int key) {
	// return java.awt.event.KeyEvent.getKeyText(key);
	// }

	public static @Nullable BasicService maybeGetBasicService() {
		BasicService s = null;
		try {
			s = (BasicService) ServiceManager.lookup("javax.jnlp.BasicService");
		} catch (final UnavailableServiceException e) {
			System.err.println("Can't get BasicService: " + e);
		}
		return s;
	}

	public static @Nullable ClipboardService maybeGetClipboardService() {
		ClipboardService result = null;
		try {
			result = (ClipboardService) ServiceManager.lookup("javax.jnlp.ClipboardService");
		} catch (final UnavailableServiceException e) {
			System.err.println("Can't get ClipboardService: " + e);
		}
		return result;
	}

	private static @NonNull @Immutable List<Boolean> booleanValues() {
		final List<Boolean> result = UtilArray.getUnmodifiableList(Boolean.TRUE, Boolean.FALSE);
		assert result != null;
		return result;
	}

	// This CPU-time code is from
	// http://stackoverflow.com/questions/29931391/obtaining-cpu-thread-usage-in-java
	public static class MyThreadInfo {
		// private final int sampleTime = 10_000;
		ThreadMXBean threadMxBean = ManagementFactory.getThreadMXBean();
		// final RuntimeMXBean runtimeMxBean =
		// ManagementFactory.getRuntimeMXBean();
		// private final OperatingSystemMXBean osMxBean =
		// ManagementFactory.getOperatingSystemMXBean();
		Map<Long, Long> threadInitialCPU = new HashMap<>();
		// private final Map<Long, Float> threadCPUUsage = new HashMap<>();
		// final long initialUptime = runtimeMxBean.getUptime();
	}

	public static @Nullable MyThreadInfo initThreadCPUtimes() {
		MyThreadInfo result = new MyThreadInfo();
		final ThreadMXBean threadMxBean = result.threadMxBean;
		if (threadMxBean.isObjectMonitorUsageSupported()) {
			try {
				final Map<Long, Long> threadInitialCPU = result.threadInitialCPU;
				final ThreadInfo[] threadInfos = threadMxBean.dumpAllThreads(false, false);
				for (final ThreadInfo info : threadInfos) {
					final long threadId = info.getThreadId();
					threadInitialCPU.put(threadId, threadMxBean.getThreadCpuTime(threadId));
				}
			} catch (final java.security.AccessControlException e) {
				System.err
						.println("Util.initThreadCPUtimes got java.security.AccessControlXception: " + e.getMessage());
				result = null;
			}
		} else {
			result = null;
		}
		return result;
	}

	public static @NonNull String getThreadCPUtimes(final @NonNull MyThreadInfo startThreadInfo) {
		// final long upTime = myThreadInfo.runtimeMxBean.getUptime();
		final Long longZero = 0L;
		long allThreadsTime = 0;
		final Map<Long, Long> startThreadCPU = startThreadInfo.threadInitialCPU;
		final ThreadMXBean threadMxBean = startThreadInfo.threadMxBean;
		final ThreadInfo[] threadInfos = threadMxBean.dumpAllThreads(false, false);
		final Map<Long, Long> threadCPUUsage = new TreeMap<>();
		final SortedSet<Long> cpuUsage = new TreeSet<>();
		final Map<Long, String> threadNames = new HashMap<>();
		for (final ThreadInfo info : threadInfos) {
			final Long threadId = info.getThreadId();
			threadNames.put(threadId, info.getThreadName());
			final Long startCPU = startThreadCPU.getOrDefault(threadId, longZero);
			final long elapsedCPUns = threadMxBean.getThreadCpuTime(threadId) - startCPU;
			if (elapsedCPUns > 0) {
				allThreadsTime += elapsedCPUns;
				cpuUsage.add(-elapsedCPUns);
				final Long previous = threadCPUUsage.put(threadId, -elapsedCPUns);
				assert previous == null : "Multiple ThreadInfos with threadId=" + threadId + ":\n "
						+ UtilString.valueOfDeep(threadInfos);
			}
		}
		final double allThreadsTimePercentDenom = allThreadsTime / 100.0;
		double totalPercent = 0.0;

		final FormattedTableBuilder align = new FormattedTableBuilder();
		align.addLine("Thread Name", "CPU Time (ms)", "% Total CPU Time");
		align.addLine();
		for (final Long elapsedNS : cpuUsage) {
			for (final Entry<Long, Long> entry : threadCPUUsage.entrySet()) {
				if (entry.getValue().equals(elapsedNS)) {
					final Long threadID = entry.getKey();
					final String name = threadNames.get(threadID);
					final long ns = -elapsedNS;
					assert ns >= 0;
					final double percent = ns / allThreadsTimePercentDenom;
					totalPercent += percent;
					align.addLine(name, UtilMath.roundToInt(ns / NS_PER_MS), UtilMath.roundToInt(percent));
				}
			}
		}
		align.addLine();
		align.addLine("Total", UtilMath.roundToInt(allThreadsTime / NS_PER_MS), UtilMath.roundToInt(totalPercent));

		return "\n      Client CPU Times [excludes database time]  " + new Date() + "\n" + align.format() + "\n";

		// You can use osMxBean.getThreadInfo(theadId) to get information on
		// every thread reported in threadCPUUsage and analyze the most CPU
		// intentive threads
	}

	private static final double NS_PER_MS = 1_000_000.0;

	// private static Thread findThread(final Long threadID) {
	// Thread result = null;
	// final int maxThreads = 2 * Thread.activeCount();
	// final Thread[] threads = new Thread[maxThreads];
	// final int nThreads = Thread.enumerate(threads);
	// assert nThreads < maxThreads : nThreads + " " + maxThreads;
	// for (int i = 0; i < nThreads; i++) {
	// final Thread thread = threads[i];
	// if (thread.getId() == threadID) {
	// result = thread;
	// break;
	// }
	// }
	// return result;
	// }

}
