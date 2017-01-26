package com.cq.akamai.replication.agent.ccu.util;

import java.util.HashSet;
import java.util.Set;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Class AkamaiUtil.
 */
public class AkamaiUtil {

	private final static String DOT_HTML = ".html";
	private final static String HTTP = "http";
	private final static String HTTPS = "https";
	private final static String HTTP_PROTOCOL = "http://";
	private final static String HTTPS_PROTOCOL = "https://";

	/** The Constant logger. */
	private static final Logger logger = LoggerFactory
			.getLogger(AkamaiUtil.class);

	/**
	 * Correct url.
	 * 
	 * @param baseUrl
	 *            the base url
	 * @return the string
	 */
	public static String correctURL(String baseUrl) {
		if (baseUrl != null) {
			if (baseUrl.startsWith(HTTP_PROTOCOL)
					|| baseUrl.startsWith(HTTPS_PROTOCOL)) {
				// external link
				return baseUrl;
			}
			final String lowerURL = baseUrl.toLowerCase();
			// check to see if mixed case
			if (lowerURL.startsWith(HTTP_PROTOCOL)
					|| lowerURL.startsWith(HTTPS_PROTOCOL)) {
				// external link need to lower case
				int pos = lowerURL.indexOf("//") + 2;
				String newURL = lowerURL.substring(0, pos)
						+ lowerURL.substring(pos);
				return newURL;
			}

			// Check for /content/dam/
			if (baseUrl.startsWith("/content/dam/")) {
				return baseUrl;
			}

			// Check to see if it is a link to a page
			if (baseUrl.startsWith("/content/")) {
				if (baseUrl.endsWith(DOT_HTML)) {
					// ready to go
					return baseUrl;
				} else {
					if (lowerURL.contains(DOT_HTML))
						return baseUrl;
					else {
						int pos = baseUrl.indexOf("?");
						if (pos > 0) {
							return baseUrl.substring(0, pos) + DOT_HTML
									+ baseUrl.substring(pos);
						} else {
							// escape of ?
							pos = baseUrl.indexOf("%3f");
							if (pos > 0) {
								return baseUrl.substring(0, pos) + DOT_HTML
										+ baseUrl.substring(pos);
							} else {
								pos = baseUrl.indexOf("&");
								if (pos > 0) {
									return baseUrl.substring(0, pos) + DOT_HTML
											+ baseUrl.substring(pos);
								} else {
									pos = baseUrl.indexOf("%26");
									if (pos > 0) {
										return baseUrl.substring(0, pos)
												+ DOT_HTML
												+ baseUrl.substring(pos);
									} else {
										pos = baseUrl.indexOf("#");
										if (pos > 0) {
											return baseUrl.substring(0, pos)
													+ DOT_HTML
													+ baseUrl.substring(pos);
										} else
											return baseUrl + DOT_HTML;
									}
								}
							}
						}

					}
				}
			}
		}
		return baseUrl;
	}

	// Check if both HTTP and HTTPS purge/invalidate calls need to be made
	/**
	 * Adds the http https url's.
	 * 
	 * @param paths
	 *            the paths
	 * @return the sets the
	 */
	public static Set<String> addHttpHttpsURLs(final Set<String> paths) {
		String additionalURL = "";
		final Set<String> additionalURLs = new HashSet<String>(paths.size());

		for (String path : paths) {
			if (path.startsWith(HTTPS)) {
				additionalURL = path.replaceFirst(HTTPS, HTTP);
			} else {
				additionalURL = path.replaceFirst(HTTP, HTTPS);
			}
			additionalURLs.add(additionalURL);
		}
		return additionalURLs;

	}

}
