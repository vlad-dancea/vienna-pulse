package com.vladdancea.viennapulse.gtfs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.sun.net.httpserver.HttpServer;
import com.vladdancea.viennapulse.gtfs.GtfsDownloadResult.Downloaded;
import com.vladdancea.viennapulse.gtfs.GtfsDownloadResult.FeedValidators;
import com.vladdancea.viennapulse.gtfs.GtfsDownloadResult.NotModified;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.client.RestClient;

class GtfsDownloaderTests {

	private static final String ETAG = "\"feed-1\"";

	private static final String LAST_MODIFIED = "Mon, 05 Oct 2026 04:09:05 GMT";

	@TempDir
	Path workDir;

	private HttpServer server;

	private final AtomicReference<byte[]> body = new AtomicReference<>();

	private final AtomicReference<Integer> status = new AtomicReference<>(200);

	private GtfsDownloader downloader;

	@BeforeEach
	void startServer() throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/gtfs.zip", exchange -> {
			if (ETAG.equals(exchange.getRequestHeaders().getFirst("If-None-Match"))) {
				exchange.sendResponseHeaders(304, -1);
				exchange.close();
				return;
			}
			exchange.getResponseHeaders().add("ETag", ETAG);
			exchange.getResponseHeaders().add("Last-Modified", LAST_MODIFIED);
			byte[] bytes = body.get();
			exchange.sendResponseHeaders(status.get(), bytes.length);
			exchange.getResponseBody().write(bytes);
			exchange.close();
		});
		server.start();
		URI url = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/gtfs.zip");
		downloader = new GtfsDownloader(RestClient.builder(),
				new GtfsProperties(url, workDir, Duration.ofSeconds(2), Duration.ofSeconds(2), Set.of(1), null));
	}

	@AfterEach
	void stopServer() {
		server.stop(0);
	}

	@Test
	void downloadsAndHashesAValidFeed() throws Exception {
		byte[] zip = zip(GtfsDownloader.REQUIRED_FILES);
		body.set(zip);

		String expectedSha256 = sha256(zip);

		GtfsDownloadResult result = downloader.download(FeedValidators.NONE);

		assertThat(result).isInstanceOfSatisfying(Downloaded.class, feed -> {
			assertThat(feed.file()).isEqualTo(workDir.resolve("gtfs.zip")).hasBinaryContent(zip);
			assertThat(feed.sizeBytes()).isEqualTo(zip.length);
			assertThat(feed.sha256()).isEqualTo(expectedSha256);
			assertThat(feed.validators().etag()).isEqualTo(ETAG);
			assertThat(feed.validators().lastModified()).isEqualTo(Instant.parse("2026-10-05T04:09:05Z"));
		});
		assertThat(partialFiles()).isEmpty();
	}

	@Test
	void reportsAnUnchangedFeed() {
		body.set(zip(GtfsDownloader.REQUIRED_FILES));

		assertThat(downloader.download(new FeedValidators(null, ETAG))).isInstanceOf(NotModified.class);
		assertThat(workDir.resolve("gtfs.zip")).doesNotExist();
	}

	@Test
	void rejectsAFeedWithoutStopTimesAndKeepsTheOldFile() throws IOException {
		Path existing = Files.writeString(workDir.resolve("gtfs.zip"), "previous feed");
		body.set(zip(GtfsDownloader.REQUIRED_FILES.stream().filter(name -> !name.equals("stop_times.txt")).toList()));

		assertThatExceptionOfType(GtfsDownloadException.class).isThrownBy(() -> downloader.download(FeedValidators.NONE))
			.withMessageContaining("stop_times.txt");
		assertThat(existing).hasContent("previous feed");
		assertThat(partialFiles()).isEmpty();
	}

	@Test
	void rejectsAResponseThatIsNotAZip() {
		body.set("<html>maintenance</html>".getBytes(StandardCharsets.UTF_8));

		assertThatExceptionOfType(GtfsDownloadException.class).isThrownBy(() -> downloader.download(FeedValidators.NONE))
			.withMessageContaining("not a valid zip");
	}

	@Test
	void failsOnServerErrors() {
		body.set(new byte[] { 1 });
		status.set(503);

		assertThatExceptionOfType(GtfsDownloadException.class).isThrownBy(() -> downloader.download(FeedValidators.NONE))
			.withMessageContaining("HTTP 503");
	}

	private List<Path> partialFiles() throws IOException {
		try (var files = Files.list(workDir)) {
			return files.filter(path -> path.toString().endsWith(".part")).toList();
		}
	}

	private static byte[] zip(List<String> names) {
		try {
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			try (ZipOutputStream out = new ZipOutputStream(bytes)) {
				for (String name : names) {
					out.putNextEntry(new ZipEntry(name));
					out.write("header\nrow\n".getBytes(StandardCharsets.UTF_8));
					out.closeEntry();
				}
			}
			return bytes.toByteArray();
		}
		catch (IOException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static String sha256(byte[] bytes) throws Exception {
		return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
	}

}
