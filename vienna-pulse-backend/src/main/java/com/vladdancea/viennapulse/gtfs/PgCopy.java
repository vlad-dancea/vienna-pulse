package com.vladdancea.viennapulse.gtfs;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;

import org.postgresql.PGConnection;
import org.postgresql.copy.CopyIn;

/**
 * Streams rows into one table with Postgres {@code COPY ... FROM STDIN} (text format).
 * Much faster than INSERTs for bulk loads. Rows are buffered and sent in chunks.
 */
final class PgCopy implements AutoCloseable {

	private static final int FLUSH_BYTES = 256 * 1024;

	private final CopyIn copy;

	private final StringBuilder buffer = new StringBuilder(FLUSH_BYTES + 4096);

	private long rows;

	private PgCopy(CopyIn copy) {
		this.copy = copy;
	}

	static PgCopy into(Connection connection, String table, String... columns) throws SQLException {
		String sql = "COPY " + table + " (" + String.join(", ", columns) + ") FROM STDIN";
		return new PgCopy(connection.unwrap(PGConnection.class).getCopyAPI().copyIn(sql));
	}

	/** Adds one row. Supported values: null, String, Number, Boolean, LocalDate, double[]. */
	void row(Object... values) throws SQLException {
		for (int i = 0; i < values.length; i++) {
			if (i > 0) {
				buffer.append('\t');
			}
			encode(values[i], buffer);
		}
		buffer.append('\n');
		rows++;
		if (buffer.length() >= FLUSH_BYTES) {
			flush();
		}
	}

	/** Sends the remaining rows and ends the COPY. */
	long finish() throws SQLException {
		flush();
		long copied = copy.endCopy();
		if (copied != rows) {
			throw new SQLException("COPY wrote " + copied + " rows, expected " + rows);
		}
		return copied;
	}

	@Override
	public void close() throws SQLException {
		if (copy.isActive()) {
			copy.cancelCopy();
		}
	}

	private void flush() throws SQLException {
		if (!buffer.isEmpty()) {
			byte[] bytes = buffer.toString().getBytes(StandardCharsets.UTF_8);
			copy.writeToCopy(bytes, 0, bytes.length);
			buffer.setLength(0);
		}
	}

	static void encode(Object value, StringBuilder out) {
		switch (value) {
			case null -> out.append("\\N");
			case String text -> escape(text, out);
			case Number number -> out.append(number);
			case Boolean bool -> out.append(bool ? 't' : 'f');
			case LocalDate date -> out.append(date);
			case double[] array -> {
				out.append('{');
				for (int i = 0; i < array.length; i++) {
					if (i > 0) {
						out.append(',');
					}
					out.append(array[i]);
				}
				out.append('}');
			}
			default -> throw new IllegalArgumentException("Cannot COPY a " + value.getClass().getName());
		}
	}

	private static void escape(String text, StringBuilder out) {
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			switch (c) {
				case '\\' -> out.append("\\\\");
				case '\t' -> out.append("\\t");
				case '\n' -> out.append("\\n");
				case '\r' -> out.append("\\r");
				default -> out.append(c);
			}
		}
	}

}
