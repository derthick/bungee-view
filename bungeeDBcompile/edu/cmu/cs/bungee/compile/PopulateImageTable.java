package edu.cmu.cs.bungee.compile;

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jdt.annotation.NonNull;

import com.sun.image.codec.jpeg.ImageFormatException;

import edu.cmu.cs.bungee.javaExtensions.JDBCSample;
import edu.cmu.cs.bungee.javaExtensions.MyLogger;
import edu.cmu.cs.bungee.javaExtensions.UtilFiles;
import edu.cmu.cs.bungee.javaExtensions.UtilImage;

/**
 * Provides the static method populateImageTable. It's in bungeeDBcompile
 * because it's used by both bungeeDBPopulate and bungeeServlet.
 */
public class PopulateImageTable {

	private PopulateImageTable() {
	}

	public static void populateImageTable(final String URLexpr, final Pattern cPattern, final int maxW, final int maxH,
			final int quality, final JDBCSample jdbc, final boolean dontUpdate, final String items)
					throws SQLException, ImageFormatException {
		try (final ResultSet rs = jdbc.sqlQuery("SELECT DISTINCT " + URLexpr + " url, item.record_num " + "FROM item "
				+ "LEFT JOIN images USING (record_num) " + "WHERE images.image IS NULL"
				+ (items != null ? " AND item.record_num IN (" + items + ")" : ""));) {
			while (rs.next()) {
				insertIntoImageTable(rs.getString("url"), rs.getInt("record_num"), maxW, maxH, quality, jdbc,
						dontUpdate, cPattern);
			}
		}
	}

	private static URL getImageURLfromRegexp(final Pattern cPattern, final String loc)
			throws IOException, MalformedURLException {
		URL thumbURL = new URL(loc);
		if (cPattern != null) {
			final String html = UtilFiles.readURL(loc);
			// if (html == null) {
			// throw new IOException("Can't read from URL.");
			// }
			final Matcher matcher = cPattern.matcher(html);
			if (matcher.find()) {
				final String regexp_loc = matcher.group(1);
				thumbURL = new URL(thumbURL, regexp_loc);
			} else {
				String errMsg;
				if (html.length() == 0) {
					errMsg = "Document is empty. Is URL forwarded?";
				} else {
					// This close stuff is pretty specific to InternetArchive
					final String pattern = cPattern.pattern();
					final int dotIndex = pattern.lastIndexOf("\\.") + 2;
					final String closePattern = pattern.substring(0, dotIndex) + "[^\\\"]*?\\\")";
					final Matcher closeMatcher = Pattern.compile(closePattern).matcher(html);
					final String close = closeMatcher.find() ? closeMatcher.group(0) : null;
					errMsg = "Can't find pattern '" + cPattern + ".\n'" + closePattern + "' matches '" + close + "'";
				}
				throw new IOException(errMsg);
			}
		}
		return thumbURL;
	}

	// public static void loadImage(final String loc, final int record_num,
	// final int maxDimension, final int quality, final JDBCSample jdbc,
	// final boolean dontUpdate, final Pattern cPattern) {
	// loadImage(loc, record_num, maxDimension, maxDimension, quality, jdbc,
	// dontUpdate, cPattern);
	// }

	/**
	 * Read an image from the URL specified by imageLocation and cPattern, scale
	 * it down (maintaining aspect ratio) to fit within maxW x maxH if
	 * necessary, and insert it in the image table.
	 */
	static void insertIntoImageTable(final String imageLocation, final int record_num, final int maxW, final int maxH,
			final int quality, final JDBCSample jdbc, final boolean dontUpdate, final Pattern cPattern) {
		if (imageLocation != null) {
			URL url = null;
			try {
				url = getImageURLfromRegexp(cPattern, imageLocation);
				final BufferedImage image = UtilImage.resizeMaintainingAspectRatio(UtilImage.readBufferedImage(url),
						maxW, maxH);
				final int imageW = image.getWidth();
				final int imageH = image.getHeight();
				final byte[] blob = getImageBlob(quality, image, imageW, imageH);

				insertIntoImageTable(record_num, imageW, imageH, blob, jdbc, dontUpdate);
			} catch (final Throwable e) {
				jdbc.logp(
						"Unable to load image '" + url + "'. imageLocation=" + imageLocation + " cPattern=" + cPattern,
						MyLogger.WARNING, e, "PopulateHandler.insertIntoImageTable");
			}
		}
	}

	/**
	 * @param quality
	 *            0-100
	 */
	private static byte[] getImageBlob(final int quality, final Image image, final int desiredImageWidth,
			final int desiredImageHeight) {
		byte[] result = null;
		try (final ByteArrayOutputStream blobStream = UtilImage.getJPGstream(image, desiredImageWidth,
				desiredImageHeight, quality);) {
			result = blobStream.toByteArray();
		} catch (final IOException e) {
			e.printStackTrace();
		}
		// assert checkSize(desiredImageWidth, desiredImageHeight, result);
		return result;
	}

	@SuppressWarnings("resource")
	private static void insertIntoImageTable(final int record_num, final int imageWidth, final int imageHeight,
			final byte[] blob, final JDBCSample jdbc, final boolean dontUpdate) throws SQLException {
		final boolean isURI = jdbc.columnExists("images", "URI");
		final PreparedStatement setImage = jdbc
				.lookupPS("REPLACE INTO images VALUES(?, ?, ?, ?" + (isURI ? ", ?" : "") + ")");
		setImage.setInt(1, record_num);
		setImage.setBytes(2, blob);
		setImage.setInt(3, imageWidth);
		setImage.setInt(4, imageHeight);
		if (isURI) {
			setImage.setString(5, getItemURI(record_num, jdbc));
		}
		sqlUpdate(setImage, jdbc, dontUpdate);
	}

	protected static int sqlUpdate(final @NonNull PreparedStatement ps, final JDBCSample jdbc,
			final boolean dontUpdate) {
		int nRows = 0;
		if (dontUpdate) {
			System.out.println(ps);
		} else {
			try {
				nRows = jdbc.sqlUpdateWithIntArgs(ps);
			} catch (final SQLException e) {
				System.err.println("SQLException for " + ps);
				e.printStackTrace();
			}
		}
		return nRows;
	}

	@SuppressWarnings("resource")
	private static String getItemURI(final int record_num, final JDBCSample jdbc2) throws SQLException {
		final PreparedStatement ps = jdbc2.lookupPS("SELECT URI FROM item WHERE record_num = ?");
		ps.setInt(1, record_num);
		return jdbc2.sqlQueryString(ps);
	}

}
