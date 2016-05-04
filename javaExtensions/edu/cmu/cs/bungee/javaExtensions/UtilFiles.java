package edu.cmu.cs.bungee.javaExtensions;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.lang.ProcessBuilder.Redirect;
import java.net.URI;
import java.net.URL;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.DecimalFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

import org.eclipse.jdt.annotation.NonNull;

public class UtilFiles {

	// Use readURL instead
	// public static String fetch(final URL a_url) throws IOException {
	// BufferedReader dis = null;
	// try {
	// final URLConnection uc = a_url.openConnection();
	// final StringBuilder sb = new StringBuilder();
	// dis = new BufferedReader(new InputStreamReader(uc.getInputStream()));
	// while (true) {
	// final String s = dis.readLine();
	// if (s == null) {
	// break;
	// }
	// sb.append(s + "\n");
	// }
	// return sb.toString();
	// } catch (final IOException e) {
	// System.out.println("Unable to fetch " + a_url + "(IOexception)");
	// return "no document";
	// } finally {
	// if (dis != null) {
	// dis.close();
	// }
	// }
	// }

	private static final DecimalFormat EXTENSION_FORMAT = new DecimalFormat("000");

	public static @NonNull File uniquifyFilename(final String prefix, final String extension) {
		File file = null;
		for (int i = 0; file == null; i++) {
			final File candidate = new File(prefix + EXTENSION_FORMAT.format(i) + extension);
			if (!candidate.isFile()) {
				file = candidate;
			}
		}
		// assert file != null;
		return file;
	}

	// TODO Remove unused code found by UCDetector
	// public static BufferedReader getReader(final String filename) {
	// return getReader(new File(filename));
	// }

	public static BufferedReader getReader(final File file) {
		BufferedReader in = null;
		try {
			InputStream inputStream = new FileInputStream(file);
			if (file.getName().endsWith(".gz")) {
				inputStream = new GZIPInputStream(inputStream);
			}
			in = new BufferedReader(new InputStreamReader(inputStream));
		} catch (final FileNotFoundException e) {
			System.err.println("Can't find file " + file);
		} catch (final IOException e) {
			e.printStackTrace();
		}
		return in;
	}

	// TODO Remove unused code found by UCDetector
	// public static BufferedWriter getWriter(final String filename) {
	// return getWriter(new File(filename));
	// }

	public static BufferedWriter getWriter(final File file) {
		BufferedWriter out = null;
		try {
			out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), "UTF8"));
		} catch (final FileNotFoundException | UnsupportedEncodingException e) {
			System.err.println("Can't find file " + file);
			e.printStackTrace();
		}
		return out;
	}

	public static OutputStream getOutputStream(final @NonNull File file) {
		assert file != null;
		OutputStream out = null;
		try {
			out = new FileOutputStream(file);
		} catch (final FileNotFoundException e) {
			System.err.println("Can't find file " + file);
			e.printStackTrace();
		}
		return out;
	}

	public static String[] readLines(final String directory, final String filename) {
		// System.out.println("Parsing " + filename);
		final File f = new File(directory, filename);
		return UtilFiles.readFile(f).split("\n");
	}

	public static String readFile(final String filename) {
		return readFile(new File(filename));
	}

	public static String readFile(final File file) {
		String result = null;
		try (final FileInputStream inStream = new FileInputStream(file);) {
			result = inputStreamToString(inStream);
		} catch (final IOException e) {
			e.printStackTrace();
		}
		assert result != null;
		return result;
	}

	public static @NonNull String readURL(final String urlString) throws IOException {
		final URL url = new URL(urlString);
		return inputStreamToString(url.openStream());
	}

	public static @NonNull String inputStreamToString(final InputStream inputStream) {
		String result = null;
		try {
			result = bufferedReaderToString(new BufferedReader(new InputStreamReader(inputStream, "UTF8")));
		} catch (final UnsupportedEncodingException e) {
			e.printStackTrace();
		}
		assert result != null;
		return result;
	}

	private static @NonNull String bufferedReaderToString(final @NonNull BufferedReader bufferedReader) {
		String result = null;
		final StringBuilder buf = new StringBuilder();
		String line;
		try {
			while ((line = bufferedReader.readLine()) != null) {
				if (buf.length() > 0) {
					buf.append("\n");
				}
				buf.append(line);
			}
			bufferedReader.close();
			result = buf.toString();
		} catch (final IOException e) {
			e.printStackTrace();
		}
		assert result != null;
		return result;
	}

	public static boolean writeFile(final File f, final String s) {
		try (BufferedWriter out = getWriter(f);) {
			if (out != null) {
				out.write(s);
				// out.close();
				return true;
			}
		} catch (final IOException e) {
			e.printStackTrace();
		}
		return false;
	}

	// TODO Remove unused code found by UCDetector
	// public static void copyFile(final String from, final String to)
	// throws IOException {
	// // System.out.println(to);
	// copyFile(new File(from), new File(to));
	// }

	// // Copies src file to dst file.
	// // If the dst file does not exist, it is created
	// private static void copyFile(final File src, final File dst)
	// throws IOException {
	// try (final InputStream in = new FileInputStream(src);) {
	// copyStreamToFile(dst, in);
	// }
	// }

	// Copies src file to dst file.
	// If the dst file does not exist, it is created
	public static void copyURI(final URI src, final File dst) throws IOException {
		try (final InputStream in = src.toURL().openStream();) {
			copyStreamToFile(dst, in);
		}
	}

	private static void copyStreamToFile(final File dst, final InputStream in)
			throws FileNotFoundException, IOException {
		try (OutputStream out = new FileOutputStream(dst);) {
			// Transfer bytes from in to out
			final byte[] buf = new byte[1024];
			int len;
			while ((len = in.read(buf)) > 0) {
				out.write(buf, 0, len);
			}
		}
		in.close();
		// out.close();
	}

	public static FilenameFilter getFilenameFilter(final String pattern) {
		return new MyFilenameFilter(pattern);
	}

	public static String reverseDNSlookup(final String ipAddress) {
		final ProcessBuilder processBuilder = new ProcessBuilder("nslookup", ipAddress);
		processBuilder.redirectErrorStream(true);
		processBuilder.redirectOutput(Redirect.PIPE);
		String result = "Not Found";
		try {
			final Process p = processBuilder.start();
			p.waitFor();
			final String info = UtilFiles.inputStreamToString(p.getInputStream());
			final String[] fields = info.split("\n");
			for (final String field : fields) {
				final String[] parts = field.split(":");
				if (parts[0].equals("Name")) {
					result = parts[1].trim();
				}
			}
		} catch (IOException | InterruptedException e) {
			e.printStackTrace();
		}
		// System.out.println("reverseDNSlookup return " + result);
		return result;
	}

	// TODO Remove unused code found by UCDetector
	// public static String getGeoInfo(final String ipAddress) {
	// final String url = "http://api.hostip.info/get_html.php?ip="
	// + ipAddress;
	// // System.out.println(url);
	// String geoInfo = null;
	// try {
	// geoInfo = UtilFiles.readURL(url);
	// String city = null;
	// String country = null;
	// final String[] fields = geoInfo.split("\n");
	// for (final String field : fields) {
	// final String[] parts = field.split(":");
	// if (parts[0].equals("City")) {
	// city = parts[1].trim();
	// } else if (parts[0].equals("Country")) {
	// country = parts[1].trim();
	// }
	// geoInfo = city + "," + country;
	// }
	// } catch (final IOException e) {
	// e.printStackTrace();
	// }
	// return geoInfo;
	// }

	private static class MyFilenameFilter implements FilenameFilter {
		Pattern p;

		MyFilenameFilter(final String pattern) {
			p = Pattern.compile(pattern);
		}

		@Override
		public boolean accept(@SuppressWarnings("unused") final File directory, final String name) {
			final Matcher m = p.matcher(name);
			return m.matches();
		}
	}

	public static File[] getFiles(String directory, String filenamePattern) {
		final File temp = new File(filenamePattern);
		if (temp.isAbsolute()) {
			directory = temp.getParent();
			filenamePattern = temp.getName();
			// System.out.println("Absolute "+filenamePattern);
		}
		final String pattern = filenamePattern.replaceAll("\\\\", "\\\\\\\\").replaceAll("\\.", "\\\\.")
				.replaceAll("\\*", ".*");
		final FilenameFilter filter = new FilenameFilter() {
			@Override
			public boolean accept(@SuppressWarnings("unused") final File dir1, final String name) {
				return name.matches(pattern);
			}
		};
		final File[] result = getFiles(directory, filter);
		return result;
	}

	public static File[] getFiles(final String directory) {
		return getFiles(directory, (FilenameFilter) null);
	}

	private static File[] getFiles(final String directory, final FilenameFilter filter) {
		final File dir = new File(directory);
		assert dir.isDirectory() : dir + " is not a directory";
		final String[] names = dir.list(filter);
		assert names != null : dir;
		// System.out.println(dir + " " + pattern);
		final File[] result = new File[names.length];
		for (int i = 0; i < result.length; i++) {
			result[i] = new File(dir, names[i].replace(':', '-'));
		}
		return result;
	}

	public static void delete(final Path directory) {
		try {
			Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult visitFile(final Path file,
						@SuppressWarnings("unused") final BasicFileAttributes attrs) throws IOException {
					Files.delete(file);
					return FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult postVisitDirectory(final Path dir,
						@SuppressWarnings("unused") final IOException exc) throws IOException {
					Files.delete(dir);
					return FileVisitResult.CONTINUE;
				}
			});
		} catch (final Throwable e) {
			System.err.println("While deleting " + directory + ":\n");
			e.printStackTrace();
		}
	}

}
