package com.google.apphosting.tests.usercode.testservlets;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

@SuppressWarnings("serial")
public class DumpServlet extends HttpServlet
{
    @Override
    protected void doGet( HttpServletRequest request,
                          HttpServletResponse response ) throws ServletException,
                                                        IOException
    {    
        Collection<String> history = (Collection<String>)request.getAttribute("history");
        if (history!=null)
          history.add(String.format("%d,%s", System.nanoTime(),"doGet"));

        response.setContentType("text/html");
        response.setStatus(HttpServletResponse.SC_OK);

        PrintWriter out = response.getWriter();

        out.println("<h1>DumpServlet</h1>");
        out.println("<h2>Context Fields:</h2>");
        out.println("<pre>");
        out.printf("serverInfo=%s%n",getServletContext().getServerInfo());
        out.printf("getServletContextName=%s%n",getServletContext().getServletContextName());
        out.printf("virtualServerName=%s%n",getServletContext().getVirtualServerName());
        out.printf("contextPath=%s%n",getServletContext().getContextPath());
        out.printf("version=%d.%d%n",getServletContext().getMajorVersion(),getServletContext().getMinorVersion());
        out.printf("effectiveVersion=%d.%d%n",getServletContext().getEffectiveMajorVersion(),getServletContext().getEffectiveMinorVersion());
        out.println("</pre>");
        out.println("<h2>Request Fields:</h2>");
        out.println("<pre>");
        out.printf("remoteHost/Addr:port=%s/%s:%d%n",request.getRemoteHost(),request.getRemoteAddr(),request.getRemotePort());
        out.printf("localName/Addr:port=%s/%s:%d%n",request.getLocalName(),request.getLocalAddr(),request.getLocalPort());
        out.printf("scheme=%s method=%s protocol=%s%n",request.getScheme(), request.getMethod(), request.getProtocol());
        out.printf("serverName:serverPort=%s:%d%n",request.getServerName(),request.getServerPort());
        out.printf("requestURI=%s%n",request.getRequestURI());
        out.printf("requestURL=%s%n",request.getRequestURL().toString());
        out.printf("contextPath|servletPath|pathInfo=%s|%s|%s%n",request.getContextPath(),request.getServletPath(),request.getPathInfo());
        out.printf("session/new=%s/%b%n",request.getSession(true).getId(),request.getSession().isNew());
        out.println("</pre>");
        out.println("<h2>Request Headers:</h2>");
        out.println("<pre>");
        for (String n : Collections.list(request.getHeaderNames()))
            for (String v : Collections.list(request.getHeaders(n)))
                out.printf("%s: %s%n",n,v);
        out.println("</pre>");

        out.println("<h2>Session:</h2>");
        out.println("<pre>");
        HttpSession session = request.getSession(false);
        if (session != null) {
          out.printf("s.id()=%s%n", session.getId());
          out.printf("s.new()=%b%n", session.isNew());
          out.printf("s.last()=%b%n", session.getLastAccessedTime());
          for (Enumeration<String> e = session.getAttributeNames(); e.hasMoreElements();) {
            String n = e.nextElement();
            out.printf("%s=%s%n", n, session.getAttribute(n));
          }

          for (Enumeration<String> e = request.getParameterNames(); e.hasMoreElements();) {
            String n = e.nextElement();
            session.setAttribute(n, request.getParameter(n));
          }
        }
        out.println("</pre>");
        
        out.println("<h2>Response Fields:</h2>");
        out.println("<pre>");
        out.printf("bufferSize=%d%n",response.getBufferSize());
        out.printf("encodedURL(\"/foo/bar\")=%s%n",response.encodeURL("/foo/bar"));
        out.printf("encodedRedirectURL(\"/foo/bar\")=%s%n",response.encodeRedirectURL("/foo/bar"));
        out.println("</pre>");
        out.println("<h2>Environment:</h2>");
        out.println("<pre>");
        for (Map.Entry<String,String> e : System.getenv().entrySet())
          out.printf("%s=%s%n",e.getKey(),e.getValue());
        out.println("</pre>");
        out.println("<h2>System Properties:</h2>");
        out.println("<pre>");
        for (Object n : System.getProperties().keySet())
          out.printf("%s=%s%n",n,System.getProperty(n.toString()));
        out.println("</pre>");

        if (history!=null)
          history.add(String.format("%d,%s", System.nanoTime(),"response written"));
        response.flushBuffer();
        if (history!=null)
          history.add(String.format("%d,%s", System.nanoTime(),"flushed"));
        out.println("<h2>History:</h2>");
        out.println("<pre>");
        if (history!=null)
        {
          long last=0;
          for (String line : history)
          {
            long time = Long.valueOf(line.split(",")[0]).longValue();
            out.printf("%9d, %s%n",(time-last),line);
            last=time;
          }}
        
        out.println("</pre>");
    }
}
