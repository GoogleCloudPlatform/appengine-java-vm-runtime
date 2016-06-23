package com.google.apphosting.vmruntime.jetty9;

import com.google.apphosting.logging.SystemLogger;

import java.io.IOException;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

public class RequestLoggerHandler extends FileHandler implements SystemLogger {
  public static final String LOG_DIRECTORY_PROPERTY = "com.google.apphosting.logs";
  private static final String DEFAULT_LOG_DIRECTORY = "/var/log/app_engine";
  private static final String DEFAULT_LOG_PATTERN = "access.%g.log";

  private static final int LOG_PER_FILE_SIZE = 10 * 1024 * 1024;
  private static final int LOG_MAX_FILES = 3;
  private static final boolean LOG_APPEND = true;

  public RequestLoggerHandler() throws IOException, SecurityException {
    super(getFilePattern(), LOG_PER_FILE_SIZE, LOG_MAX_FILES, LOG_APPEND);
  }

  private static String getFilePattern() {
    String directory = System.getProperty(LOG_DIRECTORY_PROPERTY, DEFAULT_LOG_DIRECTORY);
    return directory + "/" + DEFAULT_LOG_PATTERN;
  }

  @Override
  public void configure() throws IOException {
    // This name is logger name that the Sfl4jRequestLog emits events to
    Logger logger = Logger.getLogger("org.eclipse.jetty.server.RequestLog");
    logger.setLevel(Level.INFO);

    // Ensure handler is present
    // Look for the expected Handlers
    for (Handler handler : logger.getHandlers()) {
      if (handler instanceof RequestLoggerHandler) {
        // Nothing else to do, return
        return;
      }
    }

    logger.addHandler(new RequestLoggerHandler());
  }
}
