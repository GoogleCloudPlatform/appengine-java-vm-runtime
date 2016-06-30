package com.google.apphosting.vmruntime.jetty9;

import com.google.apphosting.logging.SystemLogger;

import java.io.IOException;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

public class RequestLoggerHandler extends FileHandler implements SystemLogger {
  public static class RequestLogFormatter extends Formatter {
    @Override
    public String format(LogRecord record) {
      // We don't expand the message, or use the ResourceBundle stuff for this formatter.
      return record.getMessage();
    }
  }


  public static final String LOG_DIRECTORY_PROPERTY = "com.google.apphosting.logs";
  public static final String LOG_PATTERN_CONFIG_PROPERTY =
      RequestLoggerHandler.class.getName() + ".pattern";
  private static final String DEFAULT_LOG_DIRECTORY = "/var/log/app_engine";
  private static final String DEFAULT_LOG_PATTERN = "access.%g.log";

  private static final int LOG_PER_FILE_SIZE = 10 * 1024 * 1024;
  private static final int LOG_MAX_FILES = 3;
  private static final boolean LOG_APPEND = true;

  public RequestLoggerHandler() throws IOException, SecurityException {
    super(getFilePattern(), LOG_PER_FILE_SIZE, LOG_MAX_FILES, LOG_APPEND);
    setFormatter(new RequestLogFormatter());
  }

  private static String getFilePattern() {
    String directory = System.getProperty(LOG_DIRECTORY_PROPERTY, DEFAULT_LOG_DIRECTORY);

    String pattern = System.getProperty(LOG_PATTERN_CONFIG_PROPERTY);
    if (pattern != null) {
      if (pattern.startsWith("/")) {
        return pattern;
      }
      return directory + "/" + pattern;
    }

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
