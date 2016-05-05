package edu.cmu.cs.bungee.populate;

// Example args: -db loc2 -user root -pass tartan01

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.eclipse.jdt.annotation.NonNull;
import org.jboss.dna.Inflector;

import edu.cmu.cs.bungee.javaExtensions.JDBCSample;
import edu.cmu.cs.bungee.javaExtensions.psk.cmdline.ApplicationSettings;
import edu.cmu.cs.bungee.javaExtensions.psk.cmdline.StringToken;
import edu.cmu.cs.bungee.javaExtensions.psk.cmdline.Token;

/**
 * Unreliable // NO_UCD (unused code)
 */
public class SingularizeFacetNames { // NO_UCD (unused code)

	private static final StringToken SM_DB = new StringToken("db", "database to convert", "",
			Token.optRequired | Token.optSwitch, "");
	private static final StringToken SM_SERVER = new StringToken("servre", "MySQL server", "", Token.optSwitch,
			"jdbc:mysql://localhost/");
	private static final StringToken SM_USER = new StringToken("user", "MySQL user", "", Token.optSwitch, "bungee");
	private static final StringToken SM_PASS = new StringToken("pass", "MySQL user password", "", Token.optSwitch,
			"p5pass");
	private static final ApplicationSettings SM_MAIN = new ApplicationSettings();

	static {
		SM_MAIN.addToken(SM_DB);
		SM_MAIN.addToken(SM_SERVER);
		SM_MAIN.addToken(SM_USER);
		SM_MAIN.addToken(SM_PASS);
	}

	private final JDBCSample jdbc;
	private final PreparedStatement updateName;

	public static void main(final String[] args) throws SQLException {
		if (SM_MAIN.parseArgs(args)) {
			@SuppressWarnings("null")
			final SingularizeFacetNames singularize = new SingularizeFacetNames(SM_SERVER.getValue(), SM_DB.getValue(),
					SM_USER.getValue(), SM_PASS.getValue());
			singularize.singularize();
			singularize.jdbc.close();
		}
	}

	private SingularizeFacetNames(final @NonNull String _server, final @NonNull String _db, final @NonNull String _user,
			final @NonNull String _pass) throws SQLException {
		jdbc = JDBCSample.getMySqlJDBCSample(_server, _db, _user, _pass);

		updateName = jdbc.lookupPS("UPDATE facet SET name = ? WHERE facet_id = ?");
	}

	private void singularize() {
		try (final ResultSet rs = jdbc.sqlQuery("SELECT facet_id, name FROM facet");) {
			jdbc.dropAndCreateTable("facetBKP", "LIKE facet");
			jdbc.sqlUpdate("INSERT INTO facetBKP SELECT * FROM facet");

			while (rs.next()) {
				final String pluralName = rs.getString(2);
				final String singularName = Inflector.singularize(pluralName.trim());
				if (!singularName.equals(pluralName)) {
					System.out.println(pluralName + " => " + singularName);
					updateName.setString(1, singularName);
					updateName.setInt(2, rs.getInt(1));
					updateName.executeUpdate();
				}
			}
		} catch (final SQLException e) {
			e.printStackTrace();
		}

	}

}
