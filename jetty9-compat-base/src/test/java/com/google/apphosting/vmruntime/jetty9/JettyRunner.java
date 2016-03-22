/**
 * Copyright 2015 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS-IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.apphosting.vmruntime.jetty9;

import static com.google.apphosting.vmruntime.VmRuntimeFileLogHandler.JAVA_UTIL_LOGGING_CONFIG_PROPERTY;
import static com.google.apphosting.vmruntime.jetty9.VmRuntimeTestBase.JETTY_HOME_PATTERN;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.eclipse.jetty.apache.jsp.JettyJasperInitializer;
import org.eclipse.jetty.io.MappedByteBufferPool;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.NCSARequestLog;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.server.handler.DefaultHandler;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.server.handler.RequestLogHandler;
import org.eclipse.jetty.util.component.AbstractLifeCycle;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.junit.Assert;

import com.google.apphosting.vmruntime.VmRuntimeFileLogHandler;

class JettyRunner implements Runnable {

  private File logs;
  private Server server;
  private final int port;
  private String appengineWebXml;
  private final CountDownLatch started = new CountDownLatch(1);
  private static final String[] preconfigurationClasses = {
    org.eclipse.jetty.webapp.WebInfConfiguration.class.getCanonicalName(),
    org.eclipse.jetty.webapp.WebXmlConfiguration.class.getCanonicalName(),
    org.eclipse.jetty.webapp.MetaInfConfiguration.class.getCanonicalName(),
    org.eclipse.jetty.webapp.FragmentConfiguration.class.getCanonicalName(),
    org.eclipse.jetty.plus.webapp.EnvConfiguration.class.getCanonicalName(),
    org.eclipse.jetty.plus.webapp.PlusConfiguration.class.getCanonicalName(),
    // next one is way too slow for unit testing:
    //org.eclipse.jetty.annotations.AnnotationConfiguration.class.getCanonicalName()
  }; 
  public JettyRunner(int port) {
    this.port = port;
 }

  public void setAppEngineWebXml (String appengineWebXml)
  {
    this.appengineWebXml = appengineWebXml;
  }
  
  
  public void waitForStarted(long timeout,TimeUnit units) throws InterruptedException {
    if (!started.await(timeout, units) || !server.isStarted())
      throw new IllegalStateException("server state="+server.getState());

    Log.getLogger(Server.class).info("Waited!");
  }
  
  @Override
  public void run() {
    try
    {
      // find projectDir
      File project = new File(System.getProperty("user.dir",".")).getAbsoluteFile().getCanonicalFile();
      File target = new File(project,"target");
      while(!target.exists())
      {
        project=project.getParentFile();
        target = new File(project,"target");
      }

      File jetty_base = new File(System.getProperty("jetty.base",new File(target,"jetty-base").getAbsolutePath()));
      
      Assert.assertTrue(target.isDirectory());
      Assert.assertTrue(jetty_base.isDirectory());
      logs=new File(target,"logs");
      logs.delete();
      logs.mkdirs();
      logs.deleteOnExit();
      
      
      // Set GAE SystemProperties
      setSystemProperties(logs);
      
      // Create the server, connector and associated instances
      QueuedThreadPool threadpool = new QueuedThreadPool();
      server = new Server(threadpool);
      HttpConfiguration httpConfig = new HttpConfiguration();
      ServerConnector connector = new ServerConnector(server,new HttpConnectionFactory(httpConfig));
      connector.setPort(port);
      server.addConnector(connector);

      MappedByteBufferPool bufferpool = new MappedByteBufferPool();

      // Basic jetty.xml handler setup
      HandlerCollection handlers = new HandlerCollection();
      ContextHandlerCollection contexts = new ContextHandlerCollection();  // TODO is a context handler collection needed for a single context?
      handlers.setHandlers(new Handler[] {contexts,new DefaultHandler()});
      server.setHandler(handlers);
      
      // Configuration as done by gae.mod/gae.ini
      httpConfig.setOutputAggregationSize(32768);

      threadpool.setMinThreads(10);
      threadpool.setMaxThreads(500);
      threadpool.setIdleTimeout(60000);

      httpConfig.setOutputBufferSize(32768);
      httpConfig.setRequestHeaderSize(8192);
      httpConfig.setResponseHeaderSize(8192);
      httpConfig.setSendServerVersion(true);
      httpConfig.setSendDateHeader(false);
      httpConfig.setDelayDispatchUntilContent(false);

      // Setup Server as done by gae.xml
      server.addBean(bufferpool);

      httpConfig.setHeaderCacheSize(512);

      RequestLogHandler requestLogHandler = new RequestLogHandler();
      handlers.addHandler(requestLogHandler);
      
      NCSARequestLog requestLog=new NCSARequestLog(logs.getCanonicalPath()+"/request.yyyy_mm_dd.log");
      requestLogHandler.setRequestLog(requestLog);
      requestLog.setRetainDays(2);
      requestLog.setAppend(true);
      requestLog.setExtended(true);
      requestLog.setLogTimeZone("GMT");
      requestLog.setLogLatency(true);
      requestLog.setPreferProxiedForAddress(true);
    
      // configuration from root.xml
      final VmRuntimeWebAppContext context = new VmRuntimeWebAppContext();
      context.setContextPath("/");
      context.setConfigurationClasses(preconfigurationClasses);
      
      
      // Needed to initialize JSP!
      context.addBean(new AbstractLifeCycle() {
        @Override
        public void doStop() throws Exception {
        }

        @Override
        public void doStart() throws Exception {
          JettyJasperInitializer jspInit = new JettyJasperInitializer();
          jspInit.onStartup(Collections.emptySet(), context.getServletContext());
        }
      }, true);

      // find the sibling testwebapp target
      File webAppLocation = new File(target, "webapps/testwebapp");
      
      File logging = new File(webAppLocation,"WEB-INF/logging.properties").getCanonicalFile().getAbsoluteFile();
      System.setProperty(JAVA_UTIL_LOGGING_CONFIG_PROPERTY,logging.toPath().toString());

      Assert.assertTrue(webAppLocation.toString(),webAppLocation.isDirectory());
      
      context.setResourceBase(webAppLocation.getAbsolutePath());
      context.init((appengineWebXml==null?"WEB-INF/appengine-web.xml":appengineWebXml));
      context.setParentLoaderPriority(true); // true in tests for easier mocking
      
      // Hack to find the webdefault.xml
      File webDefault = new File(jetty_base, "etc/webdefault.xml");
      context.setDefaultsDescriptor(webDefault.getAbsolutePath());
     
      contexts.addHandler(context);
      // start and join
      server.start();
      
    } catch (Throwable e) {
      e.printStackTrace();
    }
    finally
    {
      Log.getLogger(Server.class).info("Started!");
      started.countDown();
    }

    try {
      if (Log.getLogger(Server.class).isDebugEnabled())
        server.dumpStdErr();
      server.join(); 
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  /**
   * Sets the system properties expected by jetty.xml.
   *
   * @throws IOException
   */
  protected void setSystemProperties(File logs) throws IOException {

    String log_file_pattern = logs.getAbsolutePath()+"/log.%g";
    
    System.setProperty(VmRuntimeFileLogHandler.LOG_PATTERN_CONFIG_PROPERTY, log_file_pattern);
    System.setProperty("jetty.appengineport", me.alexpanov.net.FreePortFinder.findFreeLocalPort() + "");
    System.setProperty("jetty.appenginehost", "localhost");
    System.setProperty("jetty.appengine.forwarded", "true");
    System.setProperty("jetty.home", JETTY_HOME_PATTERN);
    System.setProperty("GAE_SERVER_PORT", ""+port);
  }
  
  public void stop() throws Exception {
    server.stop();
  }
  
  public static void main(String... args) throws Exception {
    TestMetadataServer meta = new TestMetadataServer();
    try {
      meta.start();
      new JettyRunner(8080).run(); 
    } finally {
      meta.stop();
    }
  }

  public File getLogDir() {
    return logs;
  }

  public void dump() {
    server.dumpStdErr();
  }
}
