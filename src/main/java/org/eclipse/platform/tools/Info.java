package org.eclipse.platform.tools;

/**
 * Values are replaced by Maven in the <tt>generate-sources</tt> phase.
 */
public class Info {
	public static final String POMVERSION = "0.2.1-SNAPSHOT";
	public static final String FINALNAME = "eclipse-platform-versionbumper-0.2.1-SNAPSHOT";
	public static final String COMMANDNAME = "java -jar " + FINALNAME + "-jar-with-dependencies.jar";
	public static final String COPYRIGHT = "Copyright(c) 2021 Karsten Thoms";
}
