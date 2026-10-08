package com.vladdancea.viennapulse.gtfs;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import com.vladdancea.viennapulse.gtfs.GtfsDownloadResult.Downloaded;
import com.vladdancea.viennapulse.gtfs.GtfsDownloadResult.FeedValidators;
import com.vladdancea.viennapulse.gtfs.GtfsDownloadResult.NotModified;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Downloads the Wiener Linien GTFS zip. The file is streamed to disk, hashed on the way,
 * checked for the files the import needs, and only then moved to {@code gtfs.zip} in the
 * work directory. A failed or partial download never replaces a good file.
 */
@Service
public class GtfsDownloader {

	/** Files the import reads. A feed without one of them is rejected. */
	static final List<String> REQUIRED_FILES = List.of("routes.txt", "trips.txt", "stop_times.txt", "stops.txt",
			"shapes.txt", "calendar.txt", "calendar_dates.txt");

	static final String FILE_NAME = "gtfs.zip";

	private static final Logger log = LoggerFactory.getLogger(GtfsDownloader.class);

	private static final String USER_AGENT = "vienna-pulse (+https://pulse.vladdancea.com)";

	private final RestClient http;

	private final GtfsProperties properties;

	public GtfsDownloader(RestClient.Builder builder, GtfsProperties properties) {
		HttpClient client = HttpClient.newBuilder()
			.connectTimeout(properties.connectTimeout())
			.followRedirects(HttpClient.Redirect.NORMAL)
			.build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(client);
		requestFactory.setReadTimeout(properties.readTimeout());
		this.http = builder.clone()
			.requestFactory(requestFactory)
			.defaultHeader(HttpHeaders.USER_AGENT, USER_AGENT)
			.build();
		this.properties = properties;
	}

	/**
	 * Downloads the feed unless the server reports it unchanged since {@code previous}.
	 *
	 * @param previous validators from the last successful download, or {@link FeedValidators#NONE}
	 * @return the new feed, or {@link NotModified}
	 * @throws GtfsDownloadException on HTTP errors, I/O errors or an incomplete feed
	 */
	public GtfsDownloadResult download(FeedValidators previous) {
		try {
			Files.createDirectories(properties.workDir());
			return http.get().uri(properties.feedUrl()).headers(headers -> {
				if (previous.etag() != null) {
					headers.setIfNoneMatch(previous.etag());
				}
				if (previous.lastModified() != null) {
					headers.setIfModifiedSince(previous.lastModified().toEpochMilli());
				}
			}).exchange((request, response) -> {
				if (response.getStatusCode().isSameCodeAs(HttpStatus.NOT_MODIFIED)) {
					log.info("GTFS feed not modified since {}", previous);
					return new NotModified();
				}
				if (!response.getStatusCode().is2xxSuccessful()) {
					throw new GtfsDownloadException("GTFS download failed with HTTP " + response.getStatusCode().value());
				}
				long lastModified = response.getHeaders().getLastModified();
				FeedValidators validators = new FeedValidators(lastModified > 0 ? Instant.ofEpochMilli(lastModified) : null,
						response.getHeaders().getETag());
				return store(response.getBody(), validators);
			});
		}
		catch (IOException ex) {
			throw new GtfsDownloadException("GTFS download failed: " + ex.getMessage(), ex);
		}
	}

	private Downloaded store(InputStream body, FeedValidators validators) throws IOException {
		Path partial = Files.createTempFile(properties.workDir(), "gtfs-", ".zip.part");
		try {
			MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
			long size;
			try (InputStream in = new DigestInputStream(body, sha256); OutputStream out = Files.newOutputStream(partial)) {
				size = in.transferTo(out);
			}
			checkFeed(partial);
			Path target = properties.workDir().resolve(FILE_NAME);
			Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			Downloaded result = new Downloaded(target, HexFormat.of().formatHex(sha256.digest()), size, validators);
			log.info("Downloaded GTFS feed: {} bytes, sha256 {}, last modified {}", size, result.sha256(),
					validators.lastModified());
			return result;
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 is not available", ex);
		}
		finally {
			Files.deleteIfExists(partial);
		}
	}

	private static void checkFeed(Path zip) throws IOException {
		try (ZipFile file = new ZipFile(zip.toFile())) {
			for (String name : REQUIRED_FILES) {
				ZipEntry entry = file.getEntry(name);
				if (entry == null || entry.getSize() == 0) {
					throw new GtfsDownloadException("GTFS feed is missing " + name);
				}
			}
		}
		catch (ZipException ex) {
			throw new GtfsDownloadException("GTFS download is not a valid zip file", ex);
		}
	}

}
