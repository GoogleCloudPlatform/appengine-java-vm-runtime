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

import static com.google.apphosting.vmruntime.jetty9.VmRuntimeTestBase.JETTY_HOME_PATTERN;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.servlet.jsp.JspFactory;
import org.apache.jasper.runtime.JspFactoryImpl;
import org.apache.tomcat.InstanceManager;
import org.apache.tomcat.SimpleInstanceManager;
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

class JettyRunner implements Runnable {

  static final String LOG_FILE_PATTERN = "target/log.%g";
  
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
    started.await(timeout, units);
    if (!server.isStarted())
      throw new IllegalStateException("server state="+server.getState());
  }
  
  @Override
  public void run() {

    try
    {
      // Set GAE SystemProperties
      setSystemProperties();
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
      File logs=File.createTempFile("logs", "logs");
      logs.delete();
      logs.mkdirs();
      NCSARequestLog requestLog=new NCSARequestLog(logs.getCanonicalPath()+"/request.yyyy_mm_dd.log");
      requestLogHandler.setRequestLog(requestLog);
      requestLog.setRetainDays(2);
      requestLog.setAppend(true);
      requestLog.setExtended(true);
      requestLog.setLogTimeZone("GMT");
      requestLog.setLogLatency(true);
      requestLog.setPreferProxiedForAddress(true);
   
      // Ugly hack to delete possible previous run lock file
      new File("target/log.0.lck").delete();
      new File("target/log.0.1.lck").delete();
    
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
		      jspInit.onStartup(Collections.EMPTY_SET, context.getServletContext());
			}
		}, true);
     
      // find the sibling testwebapp target
      File currentDir = new File("").getAbsoluteFile();
      File webAppLocation = new File(currentDir, "target/webapps/testwebapp");
      context.setResourceBase(webAppLocation.getAbsolutePath());
      context.init((appengineWebXml==null?"WEB-INF/appengine-web.xml":appengineWebXml));
      context.setParentLoaderPriority(true); // true in tests for easier mocking
      
      // Hack to find the webdefault.xml
      File webDefault = new File(currentDir, "target/jetty-base/etc/webdefault.xml");
      context.setDefaultsDescriptor(webDefault.getAbsolutePath());
     
      contexts.addHandler(context);
      // start and join
      server.start();
      
    } catch (Exception e) {
      e.printStackTrace();
    }
    finally
    {
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
  protected void setSystemProperties() throws IOException {

    System.setProperty(
            "com.google.apphosting.vmruntime.VmRuntimeFileLogHandler.pattern", LOG_FILE_PATTERN);
    System.setProperty("jetty.appengineport", me.alexpanov.net.FreePortFinder.findFreeLocalPort() + "");
    System.setProperty("jetty.appenginehost", "localhost");
    System.setProperty("jetty.appengine.forwarded", "true");
    System.setProperty("jetty.home", JETTY_HOME_PATTERN);
    System.setProperty("GAE_SERVER_PORT", ""+port);
  }
  
  public void stop() throws Exception {
    server.stop();
  }
  
  public static void main(String... args)
  {
    new JettyRunner(8080).run(); 
  }
}
